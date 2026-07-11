package com.example.rakutencoverage.ui.checkin

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SpeedColors の境界値テスト。
 * androidx.compose.ui.graphics.Color(Long)/(Int) はビット演算のみの純粋なJVMコードで
 * android.graphics.Color 等のAndroidフレームワーク呼び出しを含まないため、
 * Robolectric無しのローカルJVM単体テストでも安全に評価できる。
 */
class SpeedColorsTest {

    private val green = Color(0xFF43A047)
    private val orange = Color(0xFFFB8C00)
    private val brown = Color(0xFF5D4037)

    // ---- downloadColor: ≥30→緑 / 5〜30→オレンジ / <5→茶 ----

    @Test
    fun downloadColorAtThirtyIsGreen() {
        assertEquals(green, downloadColor(30.0))
    }

    @Test
    fun downloadColorJustBelowThirtyIsOrange() {
        assertEquals(orange, downloadColor(29.9))
    }

    @Test
    fun downloadColorAtFiveIsOrange() {
        assertEquals(orange, downloadColor(5.0))
    }

    @Test
    fun downloadColorJustBelowFiveIsBrown() {
        assertEquals(brown, downloadColor(4.9))
    }

    @Test
    fun downloadColorAtZeroIsBrown() {
        assertEquals(brown, downloadColor(0.0))
    }

    // ---- uploadColor: ≥10→緑 / 2〜10→オレンジ / <2→茶 ----

    @Test
    fun uploadColorAtTenIsGreen() {
        assertEquals(green, uploadColor(10.0))
    }

    @Test
    fun uploadColorJustBelowTenIsOrange() {
        assertEquals(orange, uploadColor(9.9))
    }

    @Test
    fun uploadColorAtTwoIsOrange() {
        assertEquals(orange, uploadColor(2.0))
    }

    @Test
    fun uploadColorJustBelowTwoIsBrown() {
        assertEquals(brown, uploadColor(1.9))
    }

    // ---- latencyColor: ≤60→緑 / 61〜150→オレンジ / >150→茶 ----

    @Test
    fun latencyColorAtSixtyIsGreen() {
        assertEquals(green, latencyColor(60))
    }

    @Test
    fun latencyColorAtSixtyOneIsOrange() {
        assertEquals(orange, latencyColor(61))
    }

    @Test
    fun latencyColorAtOneFiftyIsOrange() {
        assertEquals(orange, latencyColor(150))
    }

    @Test
    fun latencyColorAtOneFiftyOneIsBrown() {
        assertEquals(brown, latencyColor(151))
    }
}
