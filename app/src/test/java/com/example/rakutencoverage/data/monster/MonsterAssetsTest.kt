package com.example.rakutencoverage.data.monster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonsterAssetsTest {

    @Test
    fun everyCategoryHasTenMoves() {
        MonsterCategory.entries.forEach { category ->
            assertEquals(
                "category=$category",
                10,
                MonsterAssets.movesByCategory[category]?.size
            )
        }
    }

    @Test
    fun noDuplicateMovesAcrossCategories() {
        val allMoves = MonsterAssets.movesByCategory.values.flatten()
        assertEquals(allMoves.size, allMoves.toSet().size)
    }

    @Test
    fun millimeterWaveBeamBelongsToPhantom() {
        assertTrue("ミリ波ビーム" in MonsterAssets.movesByCategory[MonsterCategory.PHANTOM]!!)
    }

    @Test
    fun roamingKickBelongsToRocket() {
        assertTrue("ローミングキック" in MonsterAssets.movesByCategory[MonsterCategory.ROCKET]!!)
    }
}
