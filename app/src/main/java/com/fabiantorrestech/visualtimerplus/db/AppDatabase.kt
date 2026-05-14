package com.fabiantorrestech.visualtimerplus.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PresetFolderEntity::class, PresetEntity::class, TimerLogEntity::class, ScheduledTimerEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE presets ADD COLUMN ignoreSilentMode INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE timer_log ADD COLUMN timeToDismissMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE timer_log ADD COLUMN cumulativeDurationMillis INTEGER")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS scheduled_timers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        presetId INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        oneTimeDateEpochDay INTEGER,
                        weekdayMask INTEGER NOT NULL,
                        startTimeMinutes INTEGER NOT NULL,
                        timingMode TEXT NOT NULL,
                        durationMillis INTEGER,
                        endTimeMinutes INTEGER,
                        lastOutcome TEXT NOT NULL,
                        lastOutcomeAtMillis INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE timer_log ADD COLUMN scheduleId INTEGER")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS scheduled_timers_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        presetId INTEGER,
                        type TEXT NOT NULL,
                        oneTimeDateEpochDay INTEGER,
                        weekdayMask INTEGER NOT NULL,
                        startTimeMinutes INTEGER NOT NULL,
                        timingMode TEXT NOT NULL,
                        durationMillis INTEGER,
                        endTimeMinutes INTEGER,
                        lastOutcome TEXT NOT NULL,
                        lastOutcomeAtMillis INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO scheduled_timers_new (
                        id, name, presetId, type, oneTimeDateEpochDay, weekdayMask,
                        startTimeMinutes, timingMode, durationMillis, endTimeMinutes,
                        lastOutcome, lastOutcomeAtMillis
                    )
                    SELECT
                        id, name, presetId, type, oneTimeDateEpochDay, weekdayMask,
                        startTimeMinutes, timingMode, durationMillis, endTimeMinutes,
                        lastOutcome, lastOutcomeAtMillis
                    FROM scheduled_timers
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE scheduled_timers")
                db.execSQL("ALTER TABLE scheduled_timers_new RENAME TO scheduled_timers")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "visual_timer_db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
    }
}
