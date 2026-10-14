package com.ramka.app.speedtest

import com.ramka.app.relay.RelayPoolStore
import com.ramka.domain.relay.RelayCheckResult
import com.ramka.domain.relay.RelayChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Проверка сети.
 *  - Ping и Jitter: несколько вызовов `RelayClient.check()` к первому рабочему релею пула (только если пул
 *    включён: выключенный пул означает «к релеям не обращаться»).
 *  - Download: HTTP GET на пользовательский https-URL. Upload: POST 1 МБ на тот же URL, если сервер принимает.
 *  - Если URL пуст, измеряются только Ping и Jitter.
 * Результат кэшируется на час (ключ — URL и набор релеев): повторный запуск в этот срок сети не использует.
 */
@Singleton
class NetworkSpeedTester @Inject constructor(
    private val relayChecker: RelayChecker,
    private val relayPool: RelayPoolStore,
    private val transfer: SpeedTestTransfer
) {
    /** Подменяется в тестах. */
    internal var nowMillis: () -> Long = { System.currentTimeMillis() }

    private val mutex = Mutex()
    private var cache: Cached? = null

    private class Cached(val key: String, val outcome: SpeedTestOutcome, val atMillis: Long)

    /**
     * @param testUrl нормализованный https-URL или null/пусто — только Ping и Jitter
     * @param force true — игнорировать кэш
     */
    suspend fun runSpeedTest(
        testUrl: String?,
        force: Boolean = false,
        onStage: (SpeedTestStage) -> Unit = {}
    ): SpeedTestOutcome = mutex.withLock {
        val url = testUrl?.takeIf { it.isNotBlank() }
        val entries = relayPool.pool.value.activeEntries
        val key = url.orEmpty() + "|" + entries.joinToString(",") { it.address.format() }

        val cached = cache
        if (!force && cached != null && cached.key == key && nowMillis() - cached.atMillis < CACHE_MILLIS) {
            return@withLock cached.outcome.copy(fromCache = true)
        }

        val skipped = mutableSetOf<SpeedTestSkip>()
        var ping: Int? = null
        var jitter: Int? = null
        var download: Double? = null
        var upload: Double? = null

        if (entries.isEmpty()) {
            skipped += SpeedTestSkip.NO_RELAY
        } else {
            onStage(SpeedTestStage.PING)
            val stats = measureLatency(entries.mapNotNull { it.endpointOrNull })
            if (stats == null) skipped += SpeedTestSkip.RELAY_FAILED else { ping = stats.first; jitter = stats.second }
        }

        if (url == null) {
            skipped += SpeedTestSkip.NO_URL
        } else {
            onStage(SpeedTestStage.DOWNLOAD)
            try {
                val t = transfer.download(url, MAX_DOWNLOAD_BYTES, MAX_DOWNLOAD_MILLIS)
                download = SpeedTestAnalysis.mbps(t.bytes, t.nanos)
            } catch (e: IOException) {
                skipped += SpeedTestSkip.DOWNLOAD_FAILED
            }
            if (download != null) {
                onStage(SpeedTestStage.UPLOAD)
                val t = transfer.upload(url, UPLOAD_BYTES)
                if (t == null) skipped += SpeedTestSkip.UPLOAD_UNSUPPORTED
                else upload = SpeedTestAnalysis.mbps(t.bytes, t.nanos)
            }
        }

        val now = nowMillis()
        val outcome = SpeedTestOutcome(NetworkSpeedTest(download, upload, ping, jitter, now), skipped)
        if (outcome.test.hasAnyMeasurement) cache = Cached(key, outcome, now)
        outcome
    }

    /** Первый релей, давший не меньше [MIN_OK_SAMPLES] успешных замеров из [PING_SAMPLES]. */
    private suspend fun measureLatency(
        endpoints: List<com.ramka.domain.relay.RelayEndpoint>
    ): Pair<Int, Int>? {
        for (endpoint in endpoints) {
            val samples = ArrayList<Long>()
            repeat(PING_SAMPLES) { index ->
                val start = System.nanoTime()
                val result = relayChecker.check(endpoint)
                val millis = (System.nanoTime() - start) / 1_000_000L
                if (result is RelayCheckResult.Ok) samples += millis
                if (index < PING_SAMPLES - 1) delay(PING_GAP_MILLIS)
            }
            if (samples.size >= MIN_OK_SAMPLES) return SpeedTestAnalysis.pingAndJitter(samples)
        }
        return null
    }

    companion object {
        const val CACHE_MILLIS = 60L * 60 * 1000
        const val PING_SAMPLES = 5
        const val MIN_OK_SAMPLES = 3
        const val PING_GAP_MILLIS = 200L
        const val MAX_DOWNLOAD_BYTES = 50L * 1024 * 1024
        const val MAX_DOWNLOAD_MILLIS = 10_000L
        const val UPLOAD_BYTES = 1024 * 1024
    }
}
