package com.ramka.app.speedtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedTestAnalysisTest {

    private fun test(dl: Double? = null, ul: Double? = null, ping: Int? = null, jitter: Int? = null) =
        NetworkSpeedTest(dl, ul, ping, jitter, timestamp = 0L)

    @Test
    fun pingThresholdsMatchSpec() {
        assertEquals(SpeedQuality.GOOD, SpeedTestAnalysis.pingQuality(0))
        assertEquals(SpeedQuality.GOOD, SpeedTestAnalysis.pingQuality(49))
        assertEquals(SpeedQuality.FAIR, SpeedTestAnalysis.pingQuality(50))
        assertEquals(SpeedQuality.FAIR, SpeedTestAnalysis.pingQuality(99))
        assertEquals(SpeedQuality.POOR, SpeedTestAnalysis.pingQuality(100))
    }

    @Test
    fun jitterAndSpeedThresholds() {
        assertEquals(SpeedQuality.GOOD, SpeedTestAnalysis.jitterQuality(19))
        assertEquals(SpeedQuality.FAIR, SpeedTestAnalysis.jitterQuality(20))
        assertEquals(SpeedQuality.POOR, SpeedTestAnalysis.jitterQuality(50))
        assertEquals(SpeedQuality.GOOD, SpeedTestAnalysis.speedQuality(5.0))
        assertEquals(SpeedQuality.FAIR, SpeedTestAnalysis.speedQuality(1.0))
        assertEquals(SpeedQuality.POOR, SpeedTestAnalysis.speedQuality(0.99))
    }

    @Test
    fun mbpsComputation() {
        // 1 000 000 байт за 1 с = 8 Мбит/с
        assertEquals(8.0, SpeedTestAnalysis.mbps(1_000_000, 1_000_000_000), 1e-9)
        assertEquals(0.0, SpeedTestAnalysis.mbps(0, 1_000_000_000), 0.0)
        assertEquals(0.0, SpeedTestAnalysis.mbps(1_000, 0), 0.0)
    }

    @Test
    fun pingAndJitterStatistics() {
        assertNull(SpeedTestAnalysis.pingAndJitter(emptyList()))
        assertEquals(40 to 0, SpeedTestAnalysis.pingAndJitter(listOf(40L)))
        // замеры 30, 40, 60, 40, 30: среднее 40; разности 10, 20, 20, 10 → jitter 15
        assertEquals(40 to 15, SpeedTestAnalysis.pingAndJitter(listOf(30L, 40L, 60L, 40L, 30L)))
    }

    @Test
    fun lowSpeedRecommendsWifiOrTurn() {
        assertTrue(SpeedRecommendation.LOW_SPEED in SpeedTestAnalysis.recommendations(test(dl = 0.5, ul = 8.0, ping = 20, jitter = 5)))
    }

    @Test
    fun highJitterAndHighPing() {
        val r = SpeedTestAnalysis.recommendations(test(ping = 150, jitter = 80))
        assertTrue(SpeedRecommendation.HIGH_JITTER in r)
        assertTrue(SpeedRecommendation.HIGH_PING in r)
    }

    @Test
    fun allGoodOnlyWhenSpeedWasMeasured() {
        assertEquals(
            listOf(SpeedRecommendation.ALL_GOOD),
            SpeedTestAnalysis.recommendations(test(dl = 30.0, ul = 10.0, ping = 20, jitter = 5))
        )
        assertEquals(
            listOf(SpeedRecommendation.LATENCY_GOOD_NO_SPEED),
            SpeedTestAnalysis.recommendations(test(ping = 20, jitter = 5))
        )
        assertTrue(SpeedTestAnalysis.recommendations(test()).isEmpty())
    }

    @Test
    fun urlMustBeHttpsWithHostAndNoCredentials() {
        assertEquals("https://example.com/f.bin", SpeedTestUrl.parse("  https://example.com/f.bin "))
        assertEquals("HTTPS://example.com/x", SpeedTestUrl.parse("HTTPS://example.com/x"))
        listOf(
            "", "   ", "http://example.com/f", "example.com/f", "ftp://example.com/f", "https://",
            "https://user:pass@example.com/f", "https://exa mple.com/f", "https:///path"
        ).forEach { assertNull("должен быть отклонён: '$it'", SpeedTestUrl.parse(it)) }
    }
}
