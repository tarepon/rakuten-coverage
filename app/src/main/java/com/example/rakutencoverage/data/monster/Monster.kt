package com.example.rakutencoverage.data.monster

import com.example.rakutencoverage.data.SignalLevel

data class Monster(
    val cellId: String,
    val name: String,
    val emoji: String,
    val hp: Int,
    val attack: Int,
    val defense: Int,
    val moves: List<String>,
    val encounterCount: Int,
    val signalLevel: SignalLevel,
    val level: Int = 1,    // 野生の初期値は生成時に決定。捕獲後は collection_records で成長
    val xp: Int = 0
)
