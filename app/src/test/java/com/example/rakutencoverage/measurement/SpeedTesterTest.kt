package com.example.rakutencoverage.measurement

import org.junit.Assert.assertEquals
import org.junit.Test

/** SpeedTester.calcMbps の単体テスト(純粋関数、ネットワークI/Oなし)。 */
class SpeedTesterTest {

    @Test
    fun calcMbpsConvertsBytesAndElapsedTimeToMegabitsPerSecond() {
        // 10,000,000 bytes (80,000,000 bits) / 1秒 = 80 Mbps
        assertEquals(80.0, SpeedTester.calcMbps(10_000_000L, 1000L), 0.0001)
    }

    @Test
    fun calcMbpsHandlesSubSecondElapsedTime() {
        // 1,000,000 bytes (8,000,000 bits) / 0.5秒 = 16 Mbps
        assertEquals(16.0, SpeedTester.calcMbps(1_000_000L, 500L), 0.0001)
    }

    @Test
    fun calcMbpsReturnsZeroForNonPositiveElapsedTime() {
        assertEquals(0.0, SpeedTester.calcMbps(1_000_000L, 0L), 0.0001)
        assertEquals(0.0, SpeedTester.calcMbps(1_000_000L, -100L), 0.0001)
    }

    @Test
    fun calcMbpsReturnsZeroForZeroBytes() {
        assertEquals(0.0, SpeedTester.calcMbps(0L, 1000L), 0.0001)
    }
}
