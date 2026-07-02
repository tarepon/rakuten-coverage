package com.example.rakutencoverage.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StampDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun achieve(record: StampRecord)

    @Query("SELECT * FROM stamp_records ORDER BY achievedAt DESC")
    fun observeAll(): Flow<List<StampRecord>>

    @Query("SELECT spotId FROM stamp_records")
    suspend fun getAchievedIds(): List<String>

    @Query("SELECT * FROM stamp_records")
    suspend fun getAll(): List<StampRecord>

    /** インポート用: 既存レコードを優先し、無いスポットだけ追加する */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(records: List<StampRecord>)

    @Query("DELETE FROM stamp_records")
    suspend fun deleteAll()
}
