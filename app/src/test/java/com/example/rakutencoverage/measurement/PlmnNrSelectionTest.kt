package com.example.rakutencoverage.measurement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * selectCellIndexByPlmn の 5G NSA (NR優先) 拡張の単体テスト。
 * PLMN選別自体の基本ケースは RakutenCellFilterTest を参照。
 * 5G NSA (在圏LTEアンカー + 非在圏NRセカンダリ) で NR セルを選べることが主眼。
 */
class PlmnNrSelectionTest {

    private fun rakutenLte(registered: Boolean = false) =
        PlmnCell("440", "11", registered)

    private fun rakutenNr(registered: Boolean = false, signal: Boolean = true) =
        PlmnCell("440", "11", registered, isNr = true, hasSignal = signal)

    @Test
    fun `NSA構成 - 在圏LTEアンカーが先頭でも信号あり楽天NRセカンダリを選ぶ`() {
        // 在圏優先だけの旧実装では LTE と誤判定していたケース (ステータスバー5G+/アプリLTE)
        val cells = listOf(rakutenLte(registered = true), rakutenNr(registered = false))
        assertEquals(1, selectCellIndexByPlmn(cells))
    }

    @Test
    fun `SA構成 - 在圏楽天NRは信号あり非在圏NRより優先される`() {
        val cells = listOf(rakutenNr(registered = false), rakutenNr(registered = true))
        assertEquals(1, selectCellIndexByPlmn(cells))
    }

    @Test
    fun `信号なしの非在圏NRは無視して在圏楽天LTEを選ぶ`() {
        // 古い/測定不能な NR 隣接セルで誤って 5G 判定しない
        val cells = listOf(rakutenNr(registered = false, signal = false), rakutenLte(registered = true))
        assertEquals(1, selectCellIndexByPlmn(cells))
    }

    @Test
    fun `他社PLMNのNRセルはNR優先の対象にならない`() {
        // ドコモの5Gが見えていても楽天LTEを選ぶ (DUAL SIM機での誤混入防止)
        val cells = listOf(
            PlmnCell("440", "10", registered = true, isNr = true),  // docomo NR
            rakutenLte(registered = true)
        )
        assertEquals(1, selectCellIndexByPlmn(cells))
    }

    @Test
    fun `NRなしなら従来どおり在圏楽天LTEを選ぶ`() {
        val cells = listOf(rakutenLte(registered = false), rakutenLte(registered = true))
        assertEquals(1, selectCellIndexByPlmn(cells))
    }

    @Test
    fun `楽天NRとauローミングでは楽天NRを選ぶ`() {
        val cells = listOf(
            PlmnCell("440", "50", registered = true),               // au roaming
            rakutenNr(registered = false)
        )
        assertEquals(1, selectCellIndexByPlmn(cells))
    }

    @Test
    fun `デフォルト引数の互換 - isNrとhasSignal省略の既存呼び出しが従来動作`() {
        // RakutenCellFilterTest の既存ケースと同じ形 (3引数) が壊れないこと
        val cells = listOf(
            PlmnCell("440", "10", registered = true),
            PlmnCell("440", "11", registered = false)
        )
        assertEquals(1, selectCellIndexByPlmn(cells))
    }

    @Test
    fun `楽天もauも無ければnull`() {
        assertNull(selectCellIndexByPlmn(listOf(PlmnCell("440", "10", registered = true, isNr = true))))
    }
}
