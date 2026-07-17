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
    fun airplaneModeAndNoSimAreNotCollectable() {
        assertEquals(99, SignalLevel.AIRPLANE_MODE.rarityRank)
        assertEquals(99, SignalLevel.NO_SIM.rarityRank)
        assert(!SignalLevel.AIRPLANE_MODE.isCollectable)
        assert(!SignalLevel.NO_SIM.isCollectable)
    }
}
