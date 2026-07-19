package com.example.rakutencoverage.measurement

import com.example.rakutencoverage.data.SignalLevel
import com.example.rakutencoverage.data.resolveSignalLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * applyNrOverride の単体テスト。
 * NR セルを報告しない NSA 端末 (ステータスバー5G+/セル走査はLTE) の補正が主眼。
 */
class NrOverrideTest {

    @Test
    fun `LTE + NR接続中 - 5Gに昇格しバンドはnullに落とす`() {
        assertEquals("5G" to null, applyNrOverride("LTE", "Band 3", nrConnected = true))
    }

    @Test
    fun `LTE Band 28 + NR接続中 - アンカーバンドを引き継いでプラチナ5G誤判定しない`() {
        val (type, band) = applyNrOverride("LTE", "Band 28", nrConnected = true)
        assertEquals("5G", type)
        assertEquals(null, band)
        // band を残すと PLATINUM_5G になってしまうケース。null 化により FIVE_G に収まる
        assertEquals(SignalLevel.FIVE_G, resolveSignalLevel(type, band))
    }

    @Test
    fun `LTE + NR未接続 - そのまま`() {
        assertEquals("LTE" to "Band 3", applyNrOverride("LTE", "Band 3", nrConnected = false))
    }

    @Test
    fun `セル走査で5G判定済み - NRバンドを保持したまま変更しない`() {
        assertEquals("5G" to "n77", applyNrOverride("5G", "n77", nrConnected = true))
        assertEquals("5G" to "n257", applyNrOverride("5G", "n257", nrConnected = true))
    }

    @Test
    fun `LTE以外は昇格しない`() {
        assertEquals("3G" to null, applyNrOverride("3G", null, nrConnected = true))
        assertEquals("NO_SERVICE" to null, applyNrOverride("NO_SERVICE", null, nrConnected = true))
    }

    @Test
    fun `auローミングセル選択時は昇格しない`() {
        // DisplayInfoのNR状態が古い値のままauエリアに入った場合に、
        // 楽天5Gが無い場所を5Gとして記録しないためのPLMNゲート
        assertEquals(
            "LTE" to "Band 18",
            applyNrOverride("LTE", "Band 18", nrConnected = true, isRakutenCell = false)
        )
    }

    @Test
    fun `楽天セル選択時はゲートを通過して昇格する`() {
        assertEquals(
            "5G" to null,
            applyNrOverride("LTE", "Band 3", nrConnected = true, isRakutenCell = true)
        )
    }
}
