package com.fabiantorrestech.visualtimerplus.timer

import androidx.compose.runtime.Immutable

@Immutable
data class TimerInstance(
    val id: Int,
    val status: TimerStatus = TimerStatus.Idle,
    val selectedDurationMillis: Long = 0L,
    val remainingMillis: Long = 0L,
    val targetEndTimeMillis: Long? = null,
    val pausedRemainingMillis: Long? = null,
    val originalDurationMillis: Long = 0L,
    val activeTimerName: String = "",
    val activePresetId: Long? = null,
    val defaultDurationMillis: Long = 0L,
    val settings: TimerSettings = TimerSettings(),
    val activeLogEntryId: Long = -1L,
    val scheduleId: Long? = null,
    val totalAdjustmentMillis: Long = 0L,
    val timeToDismissAccumulatedMillis: Long = 0L,
    val overtimeStartedAtMillis: Long? = null,
) {
    val displayMillis: Long
        get() = when (status) {
            TimerStatus.Running -> remainingMillis
            TimerStatus.Paused -> pausedRemainingMillis ?: remainingMillis
            TimerStatus.Overtime -> currentOvertimeSegmentMillis
            TimerStatus.Finished -> 0L
            TimerStatus.Idle -> selectedDurationMillis
        }

    val adjustedDurationMillis: Long?
        get() = if (originalDurationMillis > 0L && totalAdjustmentMillis != 0L) {
            (originalDurationMillis + totalAdjustmentMillis).coerceAtLeast(0L)
        } else {
            null
        }

    val adjustedTotalMillis: Long?
        get() = adjustedDurationMillis

    val allottedDurationMillis: Long
        get() = adjustedDurationMillis ?: originalDurationMillis

    val currentOvertimeSegmentMillis: Long
        get() = if (status == TimerStatus.Overtime) {
            remainingMillis.coerceAtLeast(0L)
        } else {
            0L
        }

    val timeToDismissMillis: Long
        get() = timeToDismissAccumulatedMillis + currentOvertimeSegmentMillis

    val cumulativeDurationMillis: Long
        get() = allottedDurationMillis + timeToDismissMillis

    val isTimerNameAdjusted: Boolean
        get() = activePresetId != null && adjustedDurationMillis != null

    val isScheduledLaunch: Boolean
        get() = scheduleId != null
}
