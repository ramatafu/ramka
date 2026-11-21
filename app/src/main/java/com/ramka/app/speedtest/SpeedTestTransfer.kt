package com.ramka.app.speedtest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import com.ramka.network.proxy.ProxyAuth
import com.ramka.network.proxy.ProxyManager
import com.ramka.network.proxy.ProxyMisconfiguredException
import com.ramka.network.proxy.ProxyNotReadyException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/** Сколько байт и за сколько наносекунд передано. */
data class Transfer(val bytes: Long, val nanos: Long)

/**
 * HTTP-часть теста; интерфейс нужен, чтобы тестировать [NetworkSpeedTester] без сети.
 * Если включён прокси, запрос идёт через него; при неверных настройках прокси или их незагруженности запрос НЕ
 * выполняется ([ProxyMisconfiguredException], [ProxyNotReadyException]), напрямую он не уходит.
 */
interface SpeedTestTransfer {
    /**
     * @throws ProxyMisconfiguredException прокси включён, но настроен неверно
     * @throws ProxyNotReadyException настройки прокси ещё не загружены
     * @throws IOException любая сетевая ошибка, ответ не 200 или пустое тело
     */
    suspend fun download(url: String, maxBytes: Long, maxMillis: Long): Transfer

    /**
     * POST [size] нулевых байт. null — сервер не принял POST (не 2xx) или запрос не удался.
     * @throws ProxyMisconfiguredException см. [download]
     * @throws ProxyNotReadyException см. [download]
     */
    suspend fun upload(url: String, size: Int): Transfer?
}

class HttpUrlSpeedTestTransfer @Inject constructor(
    private val proxyManager: ProxyManager
) : SpeedTestTransfer {

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
                // Ошибки прокси не «сервер не принял POST»: пусть дойдут до пользователя как есть.
                if (e is ProxyMisconfiguredException || e is ProxyNotReadyException) throw e
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

    /**
     * Соединение с учётом прокси. Решение принимается по одному снимку настроек: адрес и логин берутся из одного и
     * того же состояния. Глобальный `Authenticator` не используется: для HTTP-прокси заголовок
     * `Proxy-Authorization` ставится вручную.
     *
     * Ограничение платформы: `HttpURLConnection` не умеет SOCKS5 с логином без `Authenticator`. Такой запрос
     * завершится ошибкой (напрямую он не уйдёт), для релея SOCKS5 с логином поддерживается ручным рукопожатием.
     */
    private suspend fun open(url: String): HttpURLConnection {
        val settings = proxyManager.awaitUsableSettings() // null: прокси выключен; иначе настройки гарантированно верны
        val target = URL(url)
        val raw = if (settings == null) target.openConnection() else target.openConnection(ProxyManager.proxyFor(settings))
        val conn = raw as? HttpURLConnection ?: throw IOException("Не HTTP(S)-адрес")
        if (settings != null) {
            ProxyAuth.headerFor(settings)?.let { conn.setRequestProperty("Proxy-Authorization", it) }
        }
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
