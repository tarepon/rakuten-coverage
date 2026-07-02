package com.example.rakutencoverage.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Measurement::class, StampRecord::class, CollectionRecord::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun measurementDao(): MeasurementDao
    abstract fun stampDao(): StampDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE measurements ADD COLUMN arenaId TEXT")
                db.execSQL("ALTER TABLE measurements ADD COLUMN arenaName TEXT")
                db.execSQL("ALTER TABLE measurements ADD COLUMN seatLabel TEXT")
                db.execSQL("ALTER TABLE measurements ADD COLUMN gamePhase TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS stamp_records (
                        spotId TEXT NOT NULL PRIMARY KEY,
                        spotType TEXT NOT NULL,
                        spotName TEXT NOT NULL,
                        achievedAt TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE measurements ADD COLUMN cellId TEXT")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS collection_records (
                        h3Index TEXT NOT NULL PRIMARY KEY,
                        signalLevel TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        capturedAt TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rakuten_coverage.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
