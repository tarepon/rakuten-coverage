package com.example.rakutencoverage.data.monster

import com.example.rakutencoverage.data.SignalLevel
import org.junit.Assert.assertTrue
import org.junit.Test

class MonsterGeneratorTest {

    private val testCellIds = (1..30).map { "test-cell-$it" }

    @Test
    fun legendStatsStayWithinRange() {
        testCellIds.forEach { cellId ->
            val monster = MonsterGenerator.generate(cellId, 1, SignalLevel.PLATINUM)
            assertTrue("hp=${monster.hp}", monster.hp in MonsterCategory.LEGEND.hpRange)
            assertTrue("attack=${monster.attack}", monster.attack in MonsterCategory.LEGEND.attackRange)
            assertTrue("defense=${monster.defense}", monster.defense in MonsterCategory.LEGEND.defenseRange)
        }
    }

    @Test
    fun phantomStatsStayWithinRange() {
        testCellIds.forEach { cellId ->
            val monster = MonsterGenerator.generate(cellId, 1, SignalLevel.PLATINUM_5G)
            assertTrue(monster.hp in MonsterCategory.PHANTOM.hpRange)
            assertTrue(monster.attack in MonsterCategory.PHANTOM.attackRange)
            assertTrue(monster.defense in MonsterCategory.PHANTOM.defenseRange)
        }
    }

    @Test
    fun rocketStatsStayWithinRange() {
        testCellIds.forEach { cellId ->
            val monster = MonsterGenerator.generate(cellId, 1, SignalLevel.WEAK)
            assertTrue(monster.hp in MonsterCategory.ROCKET.hpRange)
            assertTrue(monster.attack in MonsterCategory.ROCKET.attackRange)
            assertTrue(monster.defense in MonsterCategory.ROCKET.defenseRange)
        }
    }

    @Test
    fun normalStatsStayWithinFullRange() {
        testCellIds.forEach { cellId ->
            val monster = MonsterGenerator.generate(cellId, 1, SignalLevel.LTE)
            assertTrue(monster.hp in 1..100)
            assertTrue(monster.attack in 1..100)
            assertTrue(monster.defense in 1..100)
        }
    }

    @Test
    fun movesComeFromMatchingCategoryPool() {
        testCellIds.forEach { cellId ->
            val monster = MonsterGenerator.generate(cellId, 1, SignalLevel.PLATINUM)
            monster.moves.forEach { move ->
                assertTrue(
                    "move=$move not in LEGEND pool",
                    move in MonsterAssets.movesByCategory[MonsterCategory.LEGEND]!!
                )
            }
        }
    }

    @Test
    fun rocketMovesComeFromRocketPool() {
        testCellIds.forEach { cellId ->
            val monster = MonsterGenerator.generate(cellId, 1, SignalLevel.WEAK)
            monster.moves.forEach { move ->
                assertTrue(
                    "move=$move not in ROCKET pool",
                    move in MonsterAssets.movesByCategory[MonsterCategory.ROCKET]!!
                )
            }
        }
    }

    @Test
    fun sameCellIdIsDeterministic() {
        val a = MonsterGenerator.generate("fixed-cell", 1, SignalLevel.PLATINUM)
        val b = MonsterGenerator.generate("fixed-cell", 1, SignalLevel.PLATINUM)
        assertTrue(a.hp == b.hp && a.attack == b.attack && a.defense == b.defense && a.moves == b.moves)
    }
}
