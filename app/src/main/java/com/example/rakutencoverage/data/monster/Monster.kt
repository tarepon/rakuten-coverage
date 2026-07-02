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
    val signalLevel: SignalLevel
)
