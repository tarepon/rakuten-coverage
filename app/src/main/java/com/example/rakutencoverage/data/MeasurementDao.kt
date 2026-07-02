package com.example.rakutencoverage.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {

    @Insert
    suspend fun insert(measurement: Measurement): Long

    @Insert
    suspend fun insertAll(measurements: List<Measurement>)

    /** インポート時の重複除外用: 既存レコードのタイムスタンプ一覧 */
    @Query("SELECT timestamp FROM measurements")
    suspend fun getAllTimestamps(): List<String>

    @Query("SELECT * FROM measurements ORDER BY id DESC")
    fun observeAll(): Flow<List<Measurement>>

    @Query("SELECT * FROM measurements ORDER BY id DESC")
    suspend fun getAll(): List<Measurement>

    @Query("SELECT COUNT(*) FROM measurements")
    suspend fun count(): Int

    @Query("DELETE FROM measurements")
    suspend fun deleteAll()

    /** 図鑑用: cellId ごとの初回計測日時と出会った回数を返す（cellId が null のレコードは除外） */
    @Query("""
        SELECT cellId,
               MIN(timestamp)   AS firstSeen,
               COUNT(*)         AS encounterCount,
               MAX(networkType) AS networkType,
               MAX(band)        AS band
        FROM measurements
        WHERE cellId IS NOT NULL
        GROUP BY cellId
        ORDER BY firstSeen DESC
    """)
    fun observeMonsterEncounters(): Flow<List<MonsterEncounter>>
}
