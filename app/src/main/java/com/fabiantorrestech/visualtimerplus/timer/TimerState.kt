package com.fabiantorrestech.visualtimerplus.timer

import androidx.compose.runtime.Immutable

private const val MINUTE_IN_MILLIS = 60_000L
const val MAX_DURATION_MILLIS = 99L * 3600L * 1000L
const val DRAG_MAX_MILLIS = 2L * 3600L * 1000L
const val ONE_HOUR_MILLIS = 3600_000L
const val DURATION_STEP_MILLIS = 60_000L

enum class ThemeMode { Light, Dark, System }

enum class FinishedVibrationMode(val durationMillis: Long?) {
    Off(0L),
    OneMinute(60_000L),
    TwoMinutes(120_000L),
    FiveMinutes(300_000L),
    TenMinutes(600_000L),
    Forever(null),
}

enum class FinishedSoundRoute { Default, Alarm, Notification, Media }

@Immutable
data class TimerState(
    val status: TimerStatus = TimerStatus.Idle,
    val selectedDurationMillis: Long = 0L,
    val remainingMillis: Long = 0L,
    val targetEndTimeMillis: Long? = null,
    val pausedRemainingMillis: Long? = null,
    val isOledMode: Boolean = false,
    val soundEnabled: Boolean = true,
    val finishedSoundRoute: FinishedSoundRoute = FinishedSoundRoute.Default,
    val finishedSoundVolumePercent: Int = 100,
    val overrideMutedSystemVolume: Boolean = false,
    val ignoreSilentMode: Boolean = false,
    val fullClockMode: Boolean = false,
    val finishedVibrationMode: FinishedVibrationMode = FinishedVibrationMode.OneMinute,
    val keepScreenAwakeEnabled: Boolean = false,
    val hideStatusBarEnabled: Boolean = false,
    val hideStatusBarOnlyWhenRunning: Boolean = false,
    val showCurrentTimeEnabled: Boolean = false,
    val showClockSecondsEnabled: Boolean = false,
    val clockPosition: ClockPosition = ClockPosition.Left,
    val clockTextSizeSp: Float = 32f,
    val clockwiseModeEnabled: Boolean = true,
    val cleanModeEnabled: Boolean = false,
    val hideClockInCleanMode: Boolean = false,
    val centerTimeSizeSp: Float = 36f,
    val timerTitleEnabled: Boolean = false,
    val timerTitleHideInCleanMode: Boolean = false,
    val timerTitlePosition: ClockPosition = ClockPosition.Center,
    val timerTitleTextSizeSp: Float = 16f,
    val themeMode: ThemeMode = ThemeMode.System,
    val originalDurationMillis: Long = 0L,
    val activeTimerName: String = "",
    val activePresetId: Long? = null,
    val defaultDurationMillis: Long = 0L,
    val promptBeforeStart: Boolean = false,
) {
    val displayMillis: Long
        get() = when (status) {
            TimerStatus.Running -> remainingMillis
            TimerStatus.Paused -> pausedRemainingMillis ?: remainingMillis
            TimerStatus.Finished -> 0L
            TimerStatus.Idle -> selectedDurationMillis
        }

    val adjustedTotalMillis: Long?
        get() = if (originalDurationMillis > 0L && selectedDurationMillis != originalDurationMillis)
            selectedDurationMillis else null

    val isTimerNameAdjusted: Boolean
        get() = activePresetId != null && adjustedTotalMillis != null
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


fun clampDuration(durationMillis: Long): Long = durationMillis.coerceIn(0L, MAX_DURATION_MILLIS)

fun snapDuration(durationMillis: Long): Long {
    val normalized = clampDuration(durationMillis)
    val snapped = ((normalized + (DURATION_STEP_MILLIS / 2)) / DURATION_STEP_MILLIS) * DURATION_STEP_MILLIS
    return clampDuration(snapped)
}
