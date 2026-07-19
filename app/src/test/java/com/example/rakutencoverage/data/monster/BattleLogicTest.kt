package com.example.rakutencoverage.data.monster

import com.example.rakutencoverage.data.SignalLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class BattleLogicTest {

    @Test
    fun lteBeatsPlatinum() {
        assertEquals(1.5, typeMultiplier(SignalLevel.LTE, SignalLevel.PLATINUM), 0.001)
    }

    @Test
    fun platinumBeatsFiveG() {
        assertEquals(1.5, typeMultiplier(SignalLevel.PLATINUM, SignalLevel.FIVE_G), 0.001)
    }

    @Test
    fun weakIsGlassCannonAgainstAnything() {
        assertEquals(1.25, typeMultiplier(SignalLevel.WEAK, SignalLevel.LTE), 0.001)
        assertEquals(1.25, typeMultiplier(SignalLevel.LTE, SignalLevel.WEAK), 0.001)
    }

    @Test
    fun baseCatchRateHasNoMillimeterWaveBranch() {
        assertEquals(0.28, baseCatchRate(SignalLevel.PLATINUM_5G), 0.001)
        assertEquals(0.40, baseCatchRate(SignalLevel.FIVE_G), 0.001)
        assertEquals(0.40, baseCatchRate(SignalLevel.PLATINUM), 0.001)
        assertEquals(0.40, baseCatchRate(SignalLevel.NO_SIGNAL), 0.001)
        assertEquals(0.55, baseCatchRate(SignalLevel.WEAK), 0.001)
        assertEquals(0.75, baseCatchRate(SignalLevel.LTE), 0.001)
    }
}
