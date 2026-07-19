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

    /**
     * 楽天回線以外の計測(DUAL SIM運用で誤記録された他社SIMのレコード等)の件数。
     * 対象: carrierがRakuten以外の実測 / carrier不明なのにバンドがある実測 /
     *       機内モード・SIMなしのレコード。
     * 圏外(NO_SERVICE系: carrier・bandともnull)と正常な楽天レコードは対象外。
     */
    @Query("""
        SELECT COUNT(*) FROM measurements
        WHERE (carrier IS NOT NULL AND carrier NOT LIKE '%Rakuten%')
           OR (carrier IS NULL AND band IS NOT NULL)
           OR networkType IN ('AIRPLANE_MODE', 'NO_SIM')
    """)
    suspend fun countNonRakuten(): Int

    /** countNonRakuten と同一条件で削除し、削除件数を返す */
    @Query("""
        DELETE FROM measurements
        WHERE (carrier IS NOT NULL AND carrier NOT LIKE '%Rakuten%')
           OR (carrier IS NULL AND band IS NOT NULL)
           OR networkType IN ('AIRPLANE_MODE', 'NO_SIM')
    """)
    suspend fun deleteNonRakuten(): Int

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
