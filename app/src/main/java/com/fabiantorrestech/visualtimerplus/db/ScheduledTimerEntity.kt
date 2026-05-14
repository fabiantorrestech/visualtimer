package com.fabiantorrestech.visualtimerplus.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek

@Entity(tableName = "scheduled_timers")
data class ScheduledTimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val presetId: Long? = null,
    val type: String,
    val oneTimeDateEpochDay: Long? = null,
    val weekdayMask: Int = 0,
    val startTimeMinutes: Int,
    val timingMode: String,
    val durationMillis: Long? = null,
    val endTimeMinutes: Int? = null,
    val lastOutcome: String = ScheduledTimerOutcome.None.name,
    val lastOutcomeAtMillis: Long? = null,
)

enum class ScheduledTimerType {
    OneTime,
    Repeating,
}

enum class ScheduledTimerTimingMode {
    Duration,
    EndTime,
}

enum class ScheduledTimerOutcome {
    None,
    Started,
    MissedCapacity,
    MissingPreset,
}

fun ScheduledTimerEntity.scheduleType(): ScheduledTimerType =
    ScheduledTimerType.entries.firstOrNull { it.name == type } ?: ScheduledTimerType.OneTime

fun ScheduledTimerEntity.scheduleTimingMode(): ScheduledTimerTimingMode =
    ScheduledTimerTimingMode.entries.firstOrNull { it.name == timingMode } ?: ScheduledTimerTimingMode.Duration

fun ScheduledTimerEntity.scheduleOutcome(): ScheduledTimerOutcome =
    ScheduledTimerOutcome.entries.firstOrNull { it.name == lastOutcome } ?: ScheduledTimerOutcome.None

fun weekdayMaskFor(days: Set<DayOfWeek>): Int =
    days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

fun Int.containsWeekday(day: DayOfWeek): Boolean = (this and (1 shl (day.value - 1))) != 0

fun Int.toWeekdaySet(): Set<DayOfWeek> =
    DayOfWeek.entries.filterTo(linkedSetOf()) { containsWeekday(it) }
