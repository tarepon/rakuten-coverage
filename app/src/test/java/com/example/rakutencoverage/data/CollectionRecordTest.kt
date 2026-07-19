package com.example.rakutencoverage.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionRecordTest {

    @Test
    fun platinum5gIsRarest() {
        assertEquals(0, SignalLevel.PLATINUM_5G.rarityRank)
    }

    @Test
    fun noSignalIsLeastRare() {
        assertEquals(5, SignalLevel.NO_SIGNAL.rarityRank)
    }

    @Test
    fun rarityRankIsStrictlyOrdered() {
        val ordered = listOf(
            SignalLevel.PLATINUM_5G,
            SignalLevel.FIVE_G,
            SignalLevel.PLATINUM,
            SignalLevel.LTE,
            SignalLevel.WEAK,
            SignalLevel.NO_SIGNAL
        )
        val ranks = ordered.map { it.rarityRank }
        assertEquals(ranks.sorted(), ranks)
        assertEquals(ranks.distinct(), ranks)
    }

    @Test
    fun noSignalCellGridIs555m() {
        // 圏外セルは0.005度(約555m)グリッド。グリッド内は同一セル、跨いだら別セル
        val base = latLngToCellId(35.0000, 137.0000, SignalLevel.NO_SIGNAL)
        val sameCell = latLngToCellId(35.0020, 137.0020, SignalLevel.NO_SIGNAL)   // +0.002度: 丸めて同一
        val nextCell = latLngToCellId(35.0030, 137.0000, SignalLevel.NO_SIGNAL)   // +0.003度: 隣のグリッド
        assertEquals(base, sameCell)
        assert(base != nextCell)
        assert(base.startsWith("nosig:"))
    }

    @Test
    fun airplaneModeAndNoSimAreNotCollectable() {
        assertEquals(99, SignalLevel.AIRPLANE_MODE.rarityRank)
        assertEquals(99, SignalLevel.NO_SIM.rarityRank)
        assert(!SignalLevel.AIRPLANE_MODE.isCollectable)
        assert(!SignalLevel.NO_SIM.isCollectable)
    }
}
