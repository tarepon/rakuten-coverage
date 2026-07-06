package com.example.rakutencoverage.data.monster

import com.example.rakutencoverage.data.SignalLevel

object MonsterGenerator {

    fun generate(cellId: String, encounterCount: Int, signalLevel: SignalLevel): Monster {
        val seed = cellId.hashCode().toLong() and 0xFFFFFFFFL
        val rng = SeededRandom(seed)

        val name = MonsterAssets.names[rng.nextInt(MonsterAssets.names.size)]
        // 乱数消費順は変えず(既存個体のステータス保護)、2ロールの組合せで denpamon 絵文字を決める
        val bodyIdx = rng.nextInt(MonsterAssets.bodyEmojis.size)
        val colorIdx = rng.nextInt(MonsterAssets.colorEmojis.size)
        val emoji = MonsterAssets.monsterEmojis[
            (bodyIdx * MonsterAssets.colorEmojis.size + colorIdx) % MonsterAssets.monsterEmojis.size
        ]

        val hp      = rng.nextInt(100) + 1
        val attack  = rng.nextInt(100) + 1
        val defense = rng.nextInt(100) + 1

        val moveCount = rng.nextInt(3) // 0, 1, 2
        val moves = if (moveCount == 0) {
            emptyList()
        } else {
            val shuffled = MonsterAssets.moves.toMutableList()
            // Fisher-Yates で先頭 moveCount 個を選ぶ
            for (i in 0 until moveCount) {
                val j = i + rng.nextInt(shuffled.size - i)
                val tmp = shuffled[i]; shuffled[i] = shuffled[j]; shuffled[j] = tmp
            }
            shuffled.take(moveCount)
        }

        // 野生の初期レベル 3〜10(既存ロールの後に追加 — 手前のステータスに影響しない)
        val level = 3 + rng.nextInt(8)

        return Monster(
            cellId = cellId,
            name = name,
            emoji = emoji,
            hp = hp,
            attack = attack,
            defense = defense,
            moves = moves,
            encounterCount = encounterCount,
            signalLevel = signalLevel,
            level = level
        )
    }

    // 線形合同法による決定論的な擬似乱数（同じseed→同じ結果）
    private class SeededRandom(seed: Long) {
        private var state = seed

        fun nextInt(bound: Int): Int {
            state = (state * 1664525L + 1013904223L) and 0xFFFFFFFFL
            return ((state shr 16) % bound).toInt()
        }
    }
}
