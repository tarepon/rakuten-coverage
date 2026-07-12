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

    /** 最新1件をリアルタイム監視する Flow (バックグラウンド計測サービスの結果を前面 UI に反映するために使用) */
    @Query("SELECT * FROM measurements ORDER BY id DESC LIMIT 1")
    fun observeLatest(): Flow<Measurement?>

    @Query("SELECT * FROM measurements ORDER BY id DESC")
    suspend fun getAll(): List<Measurement>

    /**
     * 通知表示用: timestamp (ISO8601, UTC) が指定日付 (yyyy-MM-dd, UTC) で始まる件数。
     * @param utcDatePrefix 例: "2026-07-12"
     */
    @Query("SELECT COUNT(*) FROM measurements WHERE timestamp LIKE :utcDatePrefix || '%'")
    suspend fun countByUtcDatePrefix(utcDatePrefix: String): Int

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
