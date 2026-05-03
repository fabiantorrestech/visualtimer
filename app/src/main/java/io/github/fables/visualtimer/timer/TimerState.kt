package com.fabiantorrestech.visualtimerplus.timer

import androidx.compose.runtime.Immutable

private const val MINUTE_IN_MILLIS = 60_000L
const val MAX_DURATION_MILLIS = 60L * MINUTE_IN_MILLIS

enum class ThemeMode { Light, Dark, System }
const val DURATION_STEP_MILLIS = 60_000L

enum class FinishedVibrationMode(val durationMillis: Long?) {
    Off(0L),
    OneMinute(60_000L),
    TwoMinutes(120_000L),
    FiveMinutes(300_000L),
    TenMinutes(600_000L),
    Forever(null),
}

@Immutable
data class TimerState(
    val status: TimerStatus = TimerStatus.Idle,
    val selectedDurationMillis: Long = 0L,
    val remainingMillis: Long = 0L,
    val targetEndTimeMillis: Long? = null,
    val pausedRemainingMillis: Long? = null,
    val isOledMode: Boolean = false,
    val soundEnabled: Boolean = true,
    val finishedVibrationMode: FinishedVibrationMode = FinishedVibrationMode.OneMinute,
    val keepScreenAwakeEnabled: Boolean = false,
    val hideStatusBarEnabled: Boolean = false,
    val hideStatusBarOnlyWhenRunning: Boolean = false,
    val showCurrentTimeEnabled: Boolean = false,
    val showClockSecondsEnabled: Boolean = false,
    val clockPosition: ClockPosition = ClockPosition.Left,
    val clockTextSize: ClockTextSize = ClockTextSize.Medium,
    val clockwiseModeEnabled: Boolean = true,
    val cleanModeEnabled: Boolean = false,
    val hideClockInCleanMode: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
) {
    val displayMillis: Long
        get() = when (status) {
            TimerStatus.Running -> remainingMillis
            TimerStatus.Paused -> pausedRemainingMillis ?: remainingMillis
            TimerStatus.Finished -> 0L
            TimerStatus.Idle -> selectedDurationMillis
        }
}

enum class TimerStatus {
    Idle,
    Running,
    Paused,
    Finished,
}

enum class ClockPosition {
    Left,
    Center,
    Right,
}

enum class ClockTextSize {
    Small,
    Medium,
    Large,
}

fun clampDuration(durationMillis: Long): Long = durationMillis.coerceIn(0L, MAX_DURATION_MILLIS)

fun snapDuration(durationMillis: Long): Long {
    val normalized = clampDuration(durationMillis)
    val snapped = ((normalized + (DURATION_STEP_MILLIS / 2)) / DURATION_STEP_MILLIS) * DURATION_STEP_MILLIS
    return clampDuration(snapped)
}
