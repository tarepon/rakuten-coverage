package com.example.rakutencoverage.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stamp_records")
data class StampRecord(
    @PrimaryKey val spotId: String,
    val spotType: String,   // SpotType.name
    val spotName: String,
    val achievedAt: String  // ISO8601
)
