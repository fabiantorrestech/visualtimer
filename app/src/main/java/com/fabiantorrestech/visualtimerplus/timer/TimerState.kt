package com.fabiantorrestech.visualtimerplus.timer

const val MAX_DURATION_MILLIS = 99L * 3600L * 1000L
const val MAX_TIMERS = 20
const val DRAG_MAX_MILLIS = 2L * 3600L * 1000L
const val ONE_HOUR_MILLIS = 3600_000L
const val DURATION_STEP_MILLIS = 60_000L
const val CLEAN_MODE_AUTO_DISMISS_MIN_SECONDS = 1
const val CLEAN_MODE_AUTO_DISMISS_MAX_SECONDS = 20
const val CLEAN_MODE_AUTO_DISMISS_DEFAULT_SECONDS = 3

enum class ThemeMode { Light, Dark, System }

enum class OverlaySize { Small, Medium, Large }

enum class OverlayStyle { Ring, TimerFace }

enum class OverlayLabelPosition { Top, Bottom }

enum class FinishedVibrationMode(val durationMillis: Long?) {
    Off(0L),
    OneMinute(60_000L),
    TwoMinutes(120_000L),
    FiveMinutes(300_000L),
    TenMinutes(600_000L),
    Forever(null),
}

enum class FinishedSoundRoute { Default, Alarm, Notification, Media }

enum class TimerStatus {
    Idle,
    Running,
    Paused,
    Overtime,
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

fun clampCleanModeAutoDismissSeconds(seconds: Int): Int =
    seconds.coerceIn(CLEAN_MODE_AUTO_DISMISS_MIN_SECONDS, CLEAN_MODE_AUTO_DISMISS_MAX_SECONDS)

fun AppState.createBlankTimer(id: Int): TimerInstance {
    val resetDuration = defaultDurationMillis.takeIf { it > 0L } ?: 0L
    return TimerInstance(
        id = id,
        status = TimerStatus.Idle,
        selectedDurationMillis = resetDuration,
        remainingMillis = resetDuration,
        defaultDurationMillis = defaultDurationMillis,
        settings = defaultTimerSettings,
    )
}

fun TimerInstance.isDefaultManaged(): Boolean =
    activeTimerName.isBlank() &&
        activePresetId == null &&
        activeLogEntryId == -1L &&
        scheduleId == null

fun AppState.resetToLatestDefaults(timer: TimerInstance): TimerInstance =
    createBlankTimer(id = timer.id)

fun AppState.isReusableEmptyTimer(timer: TimerInstance): Boolean {
    return timer.status == TimerStatus.Idle &&
        timer.isDefaultManaged() &&
        timer.targetEndTimeMillis == null &&
        timer.pausedRemainingMillis == null &&
        timer.originalDurationMillis == 0L &&
        timer.totalAdjustmentMillis == 0L &&
        timer.timeToDismissAccumulatedMillis == 0L &&
        timer.overtimeStartedAtMillis == null
}

fun AppState.findReusableEmptyTimerIndex(): Int? =
    timers.indexOfFirst(::isReusableEmptyTimer).takeIf { it >= 0 }

fun AppState.findNextAvailableTimerSlot(): Int? =
    findReusableEmptyTimerIndex() ?: timers.size.takeIf { it < MAX_TIMERS }
