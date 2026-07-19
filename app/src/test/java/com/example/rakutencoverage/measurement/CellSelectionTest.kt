package com.example.rakutencoverage.measurement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * selectMeasurementCellIndex の単体テスト。
 * 5G NSA (LTE アンカー + 非在圏 NR セカンダリ) で NR セルを選べることが主眼。
 */
class CellSelectionTest {

    private fun nr(registered: Boolean = false, signal: Boolean = true) =
        CellCandidate(CellType.NR, registered, signal)

    private fun lte(registered: Boolean = false, signal: Boolean = true) =
        CellCandidate(CellType.LTE, registered, signal)

    @Test
    fun `空リストなら null`() {
        assertNull(selectMeasurementCellIndex(emptyList()))
    }

    @Test
    fun `在圏NRのみ - SA 5G はそのセルを選ぶ`() {
        assertEquals(0, selectMeasurementCellIndex(listOf(nr(registered = true))))
    }

    @Test
    fun `NSA構成 - 在圏LTEアンカーが先頭でも信号ありNRセカンダリを選ぶ`() {
        // 従来実装 (先頭セル判定) では LTE と誤判定していたケース
        val cells = listOf(lte(registered = true), nr(registered = false, signal = true))
        assertEquals(1, selectMeasurementCellIndex(cells))
    }

    @Test
    fun `在圏NRは信号ありの非在圏NRより優先される`() {
        val cells = listOf(nr(registered = false, signal = true), nr(registered = true))
        assertEquals(1, selectMeasurementCellIndex(cells))
    }

    @Test
    fun `信号なしの非在圏NRは無視して在圏LTEを選ぶ`() {
        // 古い/測定不能な NR 隣接セルで誤って 5G 判定しない
        val cells = listOf(nr(registered = false, signal = false), lte(registered = true))
        assertEquals(1, selectMeasurementCellIndex(cells))
    }

    @Test
    fun `NRなし - 非在圏の隣接セルが先頭でも在圏LTEを選ぶ`() {
        // 従来実装では隣接セルのバンド・RSSIを拾う可能性があったケース
        val cells = listOf(lte(registered = false), lte(registered = true))
        assertEquals(1, selectMeasurementCellIndex(cells))
    }

    @Test
    fun `在圏セルが1つもなければ先頭にフォールバック`() {
        val cells = listOf(lte(registered = false, signal = false), lte(registered = false))
        assertEquals(0, selectMeasurementCellIndex(cells))
    }

    @Test
    fun `WCDMAやGSMのみでも在圏セルを選ぶ`() {
        val cells = listOf(
            CellCandidate(CellType.GSM, isRegistered = false, hasSignal = false),
            CellCandidate(CellType.WCDMA, isRegistered = true, hasSignal = false)
        )
        assertEquals(1, selectMeasurementCellIndex(cells))
    }
}
