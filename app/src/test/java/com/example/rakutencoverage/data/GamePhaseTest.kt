package com.example.rakutencoverage.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** nextPhase(現在フェーズ) → 次フェーズ の純粋関数テスト。チェックイン入力画面のフェーズ自動送りで使用。 */
class GamePhaseTest {

    @Test
    fun preGameAdvancesToQ1() {
        assertEquals(GamePhase.Q1, nextPhase(GamePhase.PRE_GAME))
    }

    @Test
    fun q1AdvancesToBreakQ1Q2() {
        assertEquals(GamePhase.BREAK_Q1_Q2, nextPhase(GamePhase.Q1))
    }

    @Test
    fun breakQ1Q2AdvancesToQ2() {
        assertEquals(GamePhase.Q2, nextPhase(GamePhase.BREAK_Q1_Q2))
    }

    @Test
    fun q2AdvancesToHalftime() {
        assertEquals(GamePhase.HALFTIME, nextPhase(GamePhase.Q2))
    }

    @Test
    fun halftimeAdvancesToQ3() {
        assertEquals(GamePhase.Q3, nextPhase(GamePhase.HALFTIME))
    }

    @Test
    fun q3AdvancesToBreakQ3Q4() {
        assertEquals(GamePhase.BREAK_Q3_Q4, nextPhase(GamePhase.Q3))
    }

    @Test
    fun breakQ3Q4AdvancesToQ4() {
        assertEquals(GamePhase.Q4, nextPhase(GamePhase.BREAK_Q3_Q4))
    }

    @Test
    fun q4AdvancesToOvertime() {
        assertEquals(GamePhase.OVERTIME, nextPhase(GamePhase.Q4))
    }

    @Test
    fun overtimeAdvancesToPostGame() {
        assertEquals(GamePhase.POST_GAME, nextPhase(GamePhase.OVERTIME))
    }

    @Test
    fun postGameStaysAtPostGame() {
        assertEquals(GamePhase.POST_GAME, nextPhase(GamePhase.POST_GAME))
    }
}
