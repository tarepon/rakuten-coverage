package com.example.rakutencoverage.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {

    @Query("SELECT * FROM collection_records ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<CollectionRecord>>

    @Query("SELECT * FROM collection_records WHERE h3Index = :h3Index LIMIT 1")
    suspend fun findByH3Index(h3Index: String): CollectionRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: CollectionRecord)

    @Query("SELECT * FROM collection_records")
    suspend fun getAll(): List<CollectionRecord>

    @Query("DELETE FROM collection_records")
    suspend fun deleteAll()

    /** インポート用: 既存レコードを優先し、無いセルだけ追加する */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(records: List<CollectionRecord>)

    @Query("SELECT COUNT(*) FROM collection_records WHERE signalLevel = :level")
    suspend fun countByLevel(level: String): Int

    /** 圏外（NO_SIGNAL）を記録した都道府県数の代替として、一意のh3IndexをNO_SIGNALで数える */
    @Query("SELECT COUNT(*) FROM collection_records WHERE signalLevel = 'NO_SIGNAL'")
    fun observeNoSignalCount(): Flow<Int>

    /** 捕獲済みセルIDのリストをリアルタイム監視（HashSet化してO(1)検索に使う） */
    @Query("SELECT h3Index FROM collection_records")
    fun observeAllH3Indexes(): Flow<List<String>>
}
