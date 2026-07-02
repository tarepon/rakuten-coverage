package com.example.rakutencoverage.data

import androidx.room.ColumnInfo

/** Room の部分クエリ結果用データクラス */
data class MonsterEncounter(
    val cellId: String,
    @ColumnInfo(name = "firstSeen") val firstSeen: String,
    @ColumnInfo(name = "encounterCount") val encounterCount: Int,
    @ColumnInfo(name = "networkType") val networkType: String,
    @ColumnInfo(name = "band") val band: String?
)
