package com.fabiantorrestech.visualtimerplus.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "presets",
    foreignKeys = [
        ForeignKey(
            entity = PresetFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val durationMillis: Long,
    @ColumnInfo(index = true) val folderId: Long? = null,
    val sortOrder: Int = 0,
    // Per-preset settings — data model only; UI deferred to a later phase
    val soundEnabled: Boolean? = null,
    val finishedSoundVolumePercent: Int? = null,
    val overrideSystemVolume: Boolean? = null,
    val finishedVibrationModeName: String? = null,
    val keepScreenAwake: Boolean? = null,
    val showCurrentTime: Boolean? = null,
    val showSeconds: Boolean? = null,
    val clockPositionName: String? = null,
    val clockSizeName: String? = null,
)
