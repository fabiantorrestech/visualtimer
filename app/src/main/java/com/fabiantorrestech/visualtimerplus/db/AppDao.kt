package com.fabiantorrestech.visualtimerplus.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // ── Folders ──────────────────────────────────────────────────────────────

    @Query("SELECT * FROM preset_folders ORDER BY sortOrder ASC, name ASC")
    fun observeFolders(): Flow<List<PresetFolderEntity>>

    @Insert
    suspend fun insertFolder(folder: PresetFolderEntity): Long

    @Update
    suspend fun updateFolder(folder: PresetFolderEntity)

    @Delete
    suspend fun deleteFolder(folder: PresetFolderEntity)

    // Move orphaned presets to Uncategorized when a folder is deleted
    @Query("UPDATE presets SET folderId = NULL WHERE folderId = :folderId")
    suspend fun orphanPresetsInFolder(folderId: Long)

    // ── Presets ───────────────────────────────────────────────────────────────

    @Query("SELECT * FROM presets ORDER BY folderId ASC NULLS LAST, sortOrder ASC, name ASC")
    fun observePresets(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchPresets(query: String): Flow<List<PresetEntity>>

    @Insert
    suspend fun insertPreset(preset: PresetEntity): Long

    @Update
    suspend fun updatePreset(preset: PresetEntity)

    @Delete
    suspend fun deletePreset(preset: PresetEntity)

    @Query("SELECT COUNT(*) FROM presets")
    suspend fun getPresetCount(): Int

    // ── Timer log ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM timer_log ORDER BY startedAt DESC LIMIT :limit")
    fun observeLog(limit: Int): Flow<List<TimerLogEntity>>

    @Query("SELECT * FROM timer_log ORDER BY startedAt DESC")
    fun observeAllLog(): Flow<List<TimerLogEntity>>

    @Insert
    suspend fun insertLogEntry(entry: TimerLogEntity): Long

    @Query(
        "UPDATE timer_log " +
            "SET endedAt = :endedAt, adjustedDurationMillis = :adjustedDuration, " +
            "timeToDismissMillis = :timeToDismissMillis, cumulativeDurationMillis = :cumulativeDurationMillis " +
            "WHERE id = :id",
    )
    suspend fun completeLogEntry(
        id: Long,
        endedAt: Long,
        adjustedDuration: Long?,
        timeToDismissMillis: Long,
        cumulativeDurationMillis: Long?,
    )

    @Query("SELECT COUNT(*) FROM timer_log")
    suspend fun getLogCount(): Int

    @Query("DELETE FROM timer_log WHERE id = (SELECT id FROM timer_log ORDER BY startedAt ASC LIMIT 1)")
    suspend fun deleteOldestLogEntry()
}
