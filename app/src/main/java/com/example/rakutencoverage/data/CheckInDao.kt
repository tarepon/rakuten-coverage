package com.example.rakutencoverage.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {

    @Insert
    suspend fun insert(record: CheckInRecord): Long

    /** バックアップ復元用: 重複除外済みの一覧をまとめて挿入する */
    @Insert
    suspend fun insertAll(records: List<CheckInRecord>)

    @Query("SELECT * FROM checkin_records ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<CheckInRecord>>

    /** 指定スポットの全記録 (📤 共有エクスポート用) */
    @Query("SELECT * FROM checkin_records WHERE spotId = :spotId ORDER BY timestamp DESC")
    suspend fun getBySpot(spotId: String): List<CheckInRecord>

    /** バックアップ保存用: 全件 */
    @Query("SELECT * FROM checkin_records ORDER BY timestamp DESC")
    suspend fun getAll(): List<CheckInRecord>

    /** インポート時の重複除外用: 既存レコードのタイムスタンプ一覧 */
    @Query("SELECT timestamp FROM checkin_records")
    suspend fun getAllTimestamps(): List<String>

    @Query("DELETE FROM checkin_records")
    suspend fun deleteAll()
}
