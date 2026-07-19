package com.example.rakutencoverage.data.monster

import com.example.rakutencoverage.data.SignalLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonsterCategoryTest {

    @Test
    fun platinumMapsToLegend() {
        assertEquals(MonsterCategory.LEGEND, SignalLevel.PLATINUM.category)
    }

    @Test
    fun platinum5gMapsToPhantom() {
        assertEquals(MonsterCategory.PHANTOM, SignalLevel.PLATINUM_5G.category)
    }

    @Test
    fun fiveGMapsToElite() {
        assertEquals(MonsterCategory.ELITE, SignalLevel.FIVE_G.category)
    }

    @Test
    fun lteMapsToNormal() {
        assertEquals(MonsterCategory.NORMAL, SignalLevel.LTE.category)
    }

    @Test
    fun weakMapsToRocket() {
        assertEquals(MonsterCategory.ROCKET, SignalLevel.WEAK.category)
    }

    @Test
    fun noSignalMapsToNormalDark() {
        assertEquals(MonsterCategory.NORMAL_DARK, SignalLevel.NO_SIGNAL.category)
    }

    @Test
    fun airplaneModeHasNoCategory() {
        assertNull(SignalLevel.AIRPLANE_MODE.category)
    }

    @Test
    fun noSimHasNoCategory() {
        assertNull(SignalLevel.NO_SIM.category)
    }

    @Test
    fun legendHasFullRanges() {
        assertEquals(70..100, MonsterCategory.LEGEND.hpRange)
        assertEquals(70..100, MonsterCategory.LEGEND.attackRange)
        assertEquals(70..100, MonsterCategory.LEGEND.defenseRange)
    }

    @Test
    fun phantomHasLowHpDefenseHighAttack() {
        assertEquals(30..60, MonsterCategory.PHANTOM.hpRange)
        assertEquals(70..100, MonsterCategory.PHANTOM.attackRange)
        assertEquals(30..60, MonsterCategory.PHANTOM.defenseRange)
    }

    @Test
    fun rocketHasLowDefense() {
        assertEquals(40..70, MonsterCategory.ROCKET.hpRange)
        assertEquals(70..100, MonsterCategory.ROCKET.attackRange)
        assertEquals(1..40, MonsterCategory.ROCKET.defenseRange)
    }
}
