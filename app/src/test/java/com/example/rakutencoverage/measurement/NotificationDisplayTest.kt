package com.example.rakutencoverage.measurement

import com.example.rakutencoverage.data.SignalLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationDisplayTest {

    @Test
    fun `platinum-class signal levels map to PLATINUM icon`() {
        assertEquals(StatusIconCategory.PLATINUM, statusIconCategory(SignalLevel.MILLIMETER_WAVE))
        assertEquals(StatusIconCategory.PLATINUM, statusIconCategory(SignalLevel.PLATINUM_5G))
        assertEquals(StatusIconCategory.PLATINUM, statusIconCategory(SignalLevel.PLATINUM))
    }

    @Test
    fun `ordinary signal levels map to NORMAL icon`() {
        assertEquals(StatusIconCategory.NORMAL, statusIconCategory(SignalLevel.FIVE_G))
        assertEquals(StatusIconCategory.NORMAL, statusIconCategory(SignalLevel.LTE))
    }

    @Test
    fun `weak or unusable signal levels map to BAD icon`() {
        assertEquals(StatusIconCategory.BAD, statusIconCategory(SignalLevel.WEAK))
        assertEquals(StatusIconCategory.BAD, statusIconCategory(SignalLevel.NO_SIGNAL))
        assertEquals(StatusIconCategory.BAD, statusIconCategory(SignalLevel.AIRPLANE_MODE))
        assertEquals(StatusIconCategory.BAD, statusIconCategory(SignalLevel.NO_SIM))
    }

    @Test
    fun `every SignalLevel is classified into exactly one category`() {
        // 分類漏れがあると when 式が網羅性チェックでコンパイルエラーになるため、
        // ここでは全 SignalLevel を通しても例外にならないことだけを確認する
        SignalLevel.entries.forEach { level ->
            val category = statusIconCategory(level)
            assert(category in StatusIconCategory.entries)
        }
    }
}
