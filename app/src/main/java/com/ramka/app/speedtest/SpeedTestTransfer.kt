package com.ramka.app.speedtest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/** Сколько байт и за сколько наносекунд передано. */
data class Transfer(val bytes: Long, val nanos: Long)

/** HTTP-часть теста; интерфейс нужен, чтобы тестировать [NetworkSpeedTester] без сети. */
interface SpeedTestTransfer {
    /** @throws IOException любая сетевая ошибка, ответ не 200 или пустое тело */
    suspend fun download(url: String, maxBytes: Long, maxMillis: Long): Transfer

    /** POST [size] нулевых байт. null — сервер не принял POST (не 2xx) или запрос не удался. */
    suspend fun upload(url: String, size: Int): Transfer?
}

class HttpUrlSpeedTestTransfer @Inject constructor() : SpeedTestTransfer {

    override suspend fun download(url: String, maxBytes: Long, maxMillis: Long): Transfer =
        withContext(Dispatchers.IO) {
            val conn = open(url)
            try {
                conn.requestMethod = "GET"
                // Без gzip: иначе подсчитанные байты не совпадут с переданными по сети.
                conn.setRequestProperty("Accept-Encoding", "identity")
                conn.setRequestProperty("Cache-Control", "no-cache")
                if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0L
                // Время считаем от получения заголовков: задержка соединения в скорость не входит.
                val start = System.nanoTime()
                val deadline = start + maxMillis * 1_000_000L
                conn.inputStream.use { input ->
                    while (total < maxBytes && System.nanoTime() < deadline) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                    }
                }
                if (total <= 0) throw IOException("Пустой ответ")
                Transfer(total, System.nanoTime() - start)
            } finally {
                conn.disconnect()
            }
        }

    override suspend fun upload(url: String, size: Int): Transfer? =
        withContext(Dispatchers.IO) {
            val conn = try {
                open(url)
            } catch (e: IOException) {
                return@withContext null
            }
            try {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(size)
                conn.setRequestProperty("Content-Type", "application/octet-stream")
                val chunk = ByteArray(BUFFER_SIZE)
                val start = System.nanoTime()
                conn.outputStream.use { out ->
                    var sent = 0
                    while (sent < size) {
                        currentCoroutineContext().ensureActive()
                        val n = minOf(chunk.size, size - sent)
                        out.write(chunk, 0, n)
                        sent += n
                    }
                }
                val code = conn.responseCode
                val nanos = System.nanoTime() - start
                if (code in 200..299) Transfer(size.toLong(), nanos) else null
            } catch (e: IOException) {
                null
            } finally {
                conn.disconnect()
            }
        }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as? HttpURLConnection ?: throw IOException("Не HTTP(S)-адрес")
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.useCaches = false
        return conn
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val TIMEOUT_MS = 10_000
    }
}
