package com.example.rakutencoverage.data.monster

import com.example.rakutencoverage.data.SignalLevel
import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

/**
 * denpamon-go から移植したバトル・捕獲の共通ロジック。
 * サイプラの Monster モデル(hp/attack/defense は 1..100、moves は文字列)に適合させている。
 */

/** バトルで使う技。威力は技名から決定論生成する(全端末で同じ) */
data class BattleMove(val name: String, val power: Int)

/** 全モンスター共通のノーマル技(技を持たない個体の救済) */
val BASIC_MOVE = BattleMove("でんぱショット", 35)

private fun fnvHash(str: String): Long {
    var h = 0x811c9dc5L
    for (c in str) {
        h = h xor c.code.toLong()
        h = (h * 0x01000193L) and 0xFFFFFFFFL
    }
    return h
}

/** 技名 → 威力 40..80(決定論) */
fun movePower(name: String): Int = 40 + (abs(fnvHash(name)) % 41L).toInt()

/** バトルで使える技一覧: ノーマル技 + 個体の技(0〜2個) */
fun Monster.battleMoves(): List<BattleMove> =
    listOf(BASIC_MOVE) + moves.map { BattleMove(it, movePower(it)) }

/**
 * 属性相性。LTE → プラチナ → 5G → LTE の循環(左が右に強い)。
 * 闇(WEAK/NO_SIGNAL)は与ダメ・被ダメとも1.25倍のガラスキャノン。
 */
private fun SignalLevel.battleType(): Int = when (this) {
    SignalLevel.LTE                                   -> 0
    SignalLevel.PLATINUM, SignalLevel.PLATINUM_5G     -> 1
    SignalLevel.FIVE_G                                -> 2
    else                                              -> -1  // 闇
}

fun typeMultiplier(attacker: SignalLevel, defender: SignalLevel): Double {
    val a = attacker.battleType()
    val d = defender.battleType()
    if (a < 0 || d < 0) return 1.25
    return when {
        (a + 1) % 4 == d -> 1.5   // 効果バツグン
        (d + 1) % 4 == a -> 0.67  // いまひとつ
        else             -> 1.0
    }
}

/**
 * バトル中の1体。イミュータブル(Compose の再描画のため、ダメージは copy で新インスタンス)。
 * ステータスは Monster の生値(1..100)に下駄を履かせてバランスを取る。
 */
data class Combatant(
    val monster: Monster,
    val curHp: Int = monster.battleMaxHp
) {
    val isFainted: Boolean get() = curHp <= 0
    fun hit(damage: Int): Combatant = copy(curHp = (curHp - damage).coerceAtLeast(0))
}

// レベルでステータスが伸びる(denpamon-go: 1レベルあたり+6%)
val Monster.battleMaxHp: Int get() = ((hp + 60) * (1 + level * 0.06)).toInt()
val Monster.battleAtk: Int   get() = ((attack + 30) * (1 + level * 0.06)).toInt()
val Monster.battleDef: Int   get() = ((defense + 30) * (1 + level * 0.06)).toInt()

/** 次のレベルに必要なXP(denpamon-go 移植) */
fun xpNeeded(level: Int): Int = level * 25

/** 勝利時の獲得XP(denpamon-go 移植) */
fun winXp(enemyLevel: Int): Int = enemyLevel * 15

data class AttackResult(val damage: Int, val multiplier: Double)

object BattleEngine {

    fun calcDamage(
        attacker: Monster,
        defender: Monster,
        move: BattleMove,
        random: Random = Random.Default
    ): AttackResult {
        val mult = typeMultiplier(attacker.signalLevel, defender.signalLevel)
        // denpamon-go 移植: レベル係数 × 技威力 × 攻/防
        val raw = (attacker.level * 0.4 + 2) * move.power * attacker.battleAtk / (defender.battleDef * 12.0)
        val dmg = floor(raw * mult * (0.85 + random.nextDouble() * 0.3)).toInt() + 2
        return AttackResult(dmg.coerceAtLeast(1), mult)
    }

    /** 敵AI: ランダムに技を選ぶ */
    fun pickEnemyMove(enemy: Monster, random: Random = Random.Default): BattleMove {
        val moves = enemy.battleMoves()
        return moves[random.nextInt(moves.size)]
    }
}

/** 捕獲基本確率(レア度が高いほど捕まえにくい)。denpamon-go 移植 */
fun baseCatchRate(level: SignalLevel): Double = when (level) {
    SignalLevel.PLATINUM_5G     -> 0.28
    SignalLevel.FIVE_G,
    SignalLevel.PLATINUM,
    SignalLevel.NO_SIGNAL       -> 0.40
    SignalLevel.WEAK            -> 0.55
    else                        -> 0.75  // LTE ほか
}
