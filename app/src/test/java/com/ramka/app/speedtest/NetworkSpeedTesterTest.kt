package com.ramka.app.speedtest

import com.ramka.app.relay.RelayPoolStore
import com.ramka.domain.relay.RelayAddress
import com.ramka.domain.relay.RelayAddressParseResult
import com.ramka.domain.relay.RelayCheckResult
import com.ramka.domain.relay.RelayChecker
import com.ramka.domain.relay.RelayEndpoint
import com.ramka.domain.relay.RelayEntry
import com.ramka.domain.relay.RelayFailure
import com.ramka.domain.relay.RelayPool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class NetworkSpeedTesterTest {

    private class FakeStore(initial: RelayPool) : RelayPoolStore {
        private val _pool = MutableStateFlow(initial)
        override val pool: StateFlow<RelayPool> = _pool
        override fun save(pool: RelayPool) { _pool.value = pool }
    }

    private class FakeChecker(private val results: (Int) -> RelayCheckResult) : RelayChecker {
        var calls = 0
        override suspend fun check(endpoint: RelayEndpoint): RelayCheckResult = results(calls++)
    }

    private class FakeTransfer(
        private val download: Transfer? = Transfer(2_000_000, 1_000_000_000), // 16 Мбит/с
        private val upload: Transfer? = Transfer(1_000_000, 1_000_000_000)     // 8 Мбит/с
    ) : SpeedTestTransfer {
        var downloads = 0
        var uploads = 0
        override suspend fun download(url: String, maxBytes: Long, maxMillis: Long): Transfer {
            downloads++
            return download ?: throw IOException("fail")
        }
        override suspend fun upload(url: String, size: Int): Transfer? {
            uploads++
            return upload
        }
    }

    private fun entry(host: String = "relay.example.com") = RelayEntry(
        id = host,
        address = (RelayAddress.parse("$host:443") as RelayAddressParseResult.Success).address,
        token = "x".repeat(32),
        pinSha256 = "ab".repeat(32),
        label = ""
    )

    private fun pool(enabled: Boolean = true, vararg entries: RelayEntry) =
        RelayPool(enabled = enabled, entries = entries.toList())

    private fun tester(
        checker: RelayChecker = FakeChecker { RelayCheckResult.Ok },
        store: RelayPoolStore = FakeStore(pool(true, entry())),
        transfer: SpeedTestTransfer = FakeTransfer()
    ) = NetworkSpeedTester(checker, store, transfer).also { it.nowMillis = { clock } }

    private var clock = 1_000_000L

    @Test
    fun emptyUrlMeasuresOnlyPingAndJitter() = runBlocking {
        val transfer = FakeTransfer()
        val outcome = tester(transfer = transfer).runSpeedTest(testUrl = null)
        assertNotNull(outcome.test.pingMs)
        assertNotNull(outcome.test.jitterMs)
        assertNull(outcome.test.downloadSpeedMbps)
        assertNull(outcome.test.uploadSpeedMbps)
        assertTrue(SpeedTestSkip.NO_URL in outcome.skipped)
        assertEquals(0, transfer.downloads)
        assertEquals(0, transfer.uploads)
    }

    @Test
    fun filledUrlMeasuresDownloadAndUpload() = runBlocking {
        val outcome = tester().runSpeedTest("https://example.com/f.bin")
        assertEquals(16.0, outcome.test.downloadSpeedMbps!!, 1e-9)
        assertEquals(8.0, outcome.test.uploadSpeedMbps!!, 1e-9)
        assertFalse(SpeedTestSkip.NO_URL in outcome.skipped)
    }

    @Test
    fun uploadIsSkippedWhenServerRejectsPost() = runBlocking {
        val outcome = tester(transfer = FakeTransfer(upload = null)).runSpeedTest("https://example.com/f.bin")
        assertNotNull(outcome.test.downloadSpeedMbps)
        assertNull(outcome.test.uploadSpeedMbps)
        assertTrue(SpeedTestSkip.UPLOAD_UNSUPPORTED in outcome.skipped)
    }

    @Test
    fun failedDownloadIsReportedAndUploadNotAttempted() = runBlocking {
        val transfer = FakeTransfer(download = null)
        val outcome = tester(transfer = transfer).runSpeedTest("https://example.com/f.bin")
        assertNull(outcome.test.downloadSpeedMbps)
        assertTrue(SpeedTestSkip.DOWNLOAD_FAILED in outcome.skipped)
        assertEquals(0, transfer.uploads)
        assertNotNull(outcome.test.pingMs) // Ping при этом измерен
    }

    @Test
    fun disabledPoolMeansNoRelayContact() = runBlocking {
        val checker = FakeChecker { RelayCheckResult.Ok }
        val outcome = tester(checker = checker, store = FakeStore(pool(false, entry()))).runSpeedTest(null)
        assertEquals(0, checker.calls)
        assertTrue(SpeedTestSkip.NO_RELAY in outcome.skipped)
        assertFalse(outcome.test.hasAnyMeasurement)
    }

    @Test
    fun tooFewSuccessfulChecksMeansRelayFailed() = runBlocking {
        // Успешны только 2 из 5 замеров: меньше порога MIN_OK_SAMPLES
        val checker = FakeChecker { i ->
            if (i < 2) RelayCheckResult.Ok else RelayCheckResult.Failed(RelayFailure.TIMEOUT)
        }
        val outcome = tester(checker = checker).runSpeedTest(null)
        assertNull(outcome.test.pingMs)
        assertTrue(SpeedTestSkip.RELAY_FAILED in outcome.skipped)
        assertEquals(NetworkSpeedTester.PING_SAMPLES, checker.calls)
    }

    @Test
    fun fallsBackToNextRelayWhenFirstFails() = runBlocking {
        val checker = FakeChecker { i ->
            if (i < NetworkSpeedTester.PING_SAMPLES) RelayCheckResult.Failed(RelayFailure.UNREACHABLE) else RelayCheckResult.Ok
        }
        val store = FakeStore(pool(true, entry("a.example.com"), entry("b.example.com")))
        val outcome = tester(checker = checker, store = store).runSpeedTest(null)
        assertNotNull(outcome.test.pingMs)
        assertFalse(SpeedTestSkip.RELAY_FAILED in outcome.skipped)
    }

    @Test
    fun resultIsCachedForAnHourAndForceBypassesCache() = runBlocking {
        val transfer = FakeTransfer()
        val t = tester(transfer = transfer)
        val first = t.runSpeedTest("https://example.com/f.bin")
        assertFalse(first.fromCache)

        clock += 30 * 60 * 1000L
        val second = t.runSpeedTest("https://example.com/f.bin")
        assertTrue(second.fromCache)
        assertEquals(1, transfer.downloads)

        val forced = t.runSpeedTest("https://example.com/f.bin", force = true)
        assertFalse(forced.fromCache)
        assertEquals(2, transfer.downloads)

        clock += 61 * 60 * 1000L
        assertFalse(t.runSpeedTest("https://example.com/f.bin").fromCache)
    }

    @Test
    fun cacheIsNotReusedForAnotherUrl() = runBlocking {
        val transfer = FakeTransfer()
        val t = tester(transfer = transfer)
        t.runSpeedTest("https://example.com/a.bin")
        assertFalse(t.runSpeedTest("https://example.com/b.bin").fromCache)
        assertEquals(2, transfer.downloads)
    }

    @Test
    fun emptyResultsAreNotCached() = runBlocking {
        val checker = FakeChecker { RelayCheckResult.Failed(RelayFailure.UNREACHABLE) }
        val t = tester(checker = checker)
        t.runSpeedTest(null)
        val callsAfterFirst = checker.calls
        t.runSpeedTest(null)
        assertTrue(checker.calls > callsAfterFirst)
    }
}
