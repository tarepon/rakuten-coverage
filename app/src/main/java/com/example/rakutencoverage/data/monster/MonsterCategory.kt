package com.example.rakutencoverage.data.monster

import com.example.rakutencoverage.data.SignalLevel

/**
 * モンスターの性格分類。signalLevel(電波品質)ごとに1:1で対応する。
 * ポケモンでいう伝説・幻・ロケット団のような世界観の軸で、
 * パラメータレンジ(HP/攻撃/防御)と技プール、図鑑表示に反映する。
 */
enum class MonsterCategory(
    val displayName: String,
    val rarityLabel: String,
    val starCount: Int,
    val emoji: String,
    val argbColor: Long,
    val hpRange: IntRange,
    val attackRange: IntRange,
    val defenseRange: IntRange
) {
    LEGEND(
        displayName = "伝説のプラチナモンスター",
        rarityLabel = "★★★★★ 伝説",
        starCount = 5,
        emoji = "👑",
        argbColor = 0xFFAB47BCL,
        hpRange = 70..100,
        attackRange = 70..100,
        defenseRange = 70..100
    ),
    PHANTOM(
        displayName = "幻のプラチナ5Gモンスター",
        rarityLabel = "★★★★☆ 幻",
        starCount = 4,
        emoji = "🤩",
        argbColor = 0xFFFFD700L,
        hpRange = 30..60,
        attackRange = 70..100,
        defenseRange = 30..60
    ),
    ELITE(
        displayName = "精鋭の5G Sub6モンスター",
        rarityLabel = "★★★☆☆ 精鋭",
        starCount = 3,
        emoji = "😆",
        argbColor = 0xFF1E88E5L,
        hpRange = 50..80,
        attackRange = 70..100,
        defenseRange = 40..70
    ),
    NORMAL(
        displayName = "LTEモンスター",
        rarityLabel = "★☆☆☆☆ コモン",
        starCount = 1,
        emoji = "🙂",
        argbColor = 0xFF43A047L,
        hpRange = 1..100,
        attackRange = 1..100,
        defenseRange = 1..100
    ),
    ROCKET(
        displayName = "ロケット団の鬼",
        rarityLabel = "★★☆☆☆ ロケット団",
        starCount = 2,
        emoji = "👹",
        argbColor = 0xFF5D4037L,
        hpRange = 40..70,
        attackRange = 70..100,
        defenseRange = 1..40
    ),
    NORMAL_DARK(
        displayName = "圏外の亡霊",
        rarityLabel = "★★★☆☆ 闇レア",
        starCount = 3,
        emoji = "💀",
        argbColor = 0xFF212121L,
        hpRange = 1..100,
        attackRange = 1..100,
        defenseRange = 1..100
    )
}

/** AIRPLANE_MODE/NO_SIMはコレクション対象外のためnull */
val SignalLevel.category: MonsterCategory? get() = when (this) {
    SignalLevel.PLATINUM                            -> MonsterCategory.LEGEND
    SignalLevel.PLATINUM_5G                         -> MonsterCategory.PHANTOM
    SignalLevel.FIVE_G                              -> MonsterCategory.ELITE
    SignalLevel.LTE                                 -> MonsterCategory.NORMAL
    SignalLevel.WEAK                                -> MonsterCategory.ROCKET
    SignalLevel.NO_SIGNAL                           -> MonsterCategory.NORMAL_DARK
    SignalLevel.AIRPLANE_MODE, SignalLevel.NO_SIM   -> null
}
