package com.fabiantorrestech.visualtimerplus.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "preset_folders")
data class PresetFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
)
