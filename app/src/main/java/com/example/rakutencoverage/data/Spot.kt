package com.example.rakutencoverage.data

enum class SpotType(val label: String, val icon: String) {
    ARENA("Bリーグアリーナ", "🏟️"),
    MICHINOEKI("道の駅", "🛣️"),
    AEONMALL("イオンモール", "🛍️")
}

data class Spot(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val type: SpotType,
    val pref: String = "",
    val city: String = "",
    val club: String = "",
    val division: String = ""
)
