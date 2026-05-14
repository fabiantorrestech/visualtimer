package com.fabiantorrestech.visualtimerplus.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timer_log")
data class TimerLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val originalDurationMillis: Long,
    val adjustedDurationMillis: Long? = null,
    val timeToDismissMillis: Long = 0L,
    val cumulativeDurationMillis: Long? = null,
    val timerName: String,
    val presetId: Long? = null,
    val scheduleId: Long? = null,
)
