package com.example.rakutencoverage.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MeasurementTest {

    @Test
    fun n257FallsBackToFiveG() {
        assertEquals(SignalLevel.FIVE_G, resolveSignalLevel("5G", "n257"))
    }

    @Test
    fun n28Is5GPlatinum() {
        assertEquals(SignalLevel.PLATINUM_5G, resolveSignalLevel("5G", "n28"))
    }

    @Test
    fun band28Is5GPlatinum() {
        assertEquals(SignalLevel.PLATINUM_5G, resolveSignalLevel("5G", "Band 28"))
    }

    @Test
    fun otherFiveGBandIsFiveG() {
        assertEquals(SignalLevel.FIVE_G, resolveSignalLevel("5G", "n77"))
    }

    @Test
    fun lteBand28IsPlatinum() {
        assertEquals(SignalLevel.PLATINUM, resolveSignalLevel("LTE", "Band 28"))
    }

    @Test
    fun lteBand3IsLte() {
        assertEquals(SignalLevel.LTE, resolveSignalLevel("LTE", "Band 3"))
    }

    @Test
    fun lteBand18IsWeak() {
        assertEquals(SignalLevel.WEAK, resolveSignalLevel("LTE", "Band 18"))
    }

    @Test
    fun lteBand26IsWeak() {
        assertEquals(SignalLevel.WEAK, resolveSignalLevel("LTE", "Band 26"))
    }

    @Test
    fun noServiceIsNoSignal() {
        assertEquals(SignalLevel.NO_SIGNAL, resolveSignalLevel("NO_SERVICE", null))
    }

    @Test
    fun airplaneModeIsAirplaneMode() {
        assertEquals(SignalLevel.AIRPLANE_MODE, resolveSignalLevel("AIRPLANE_MODE", null))
    }

    @Test
    fun noSimIsNoSim() {
        assertEquals(SignalLevel.NO_SIM, resolveSignalLevel("NO_SIM", null))
    }
}
