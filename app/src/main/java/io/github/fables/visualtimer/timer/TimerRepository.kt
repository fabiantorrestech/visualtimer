package com.fabiantorrestech.visualtimerplus.timer

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TimerRepository {
    private const val PREFS_NAME = "visual_timer_prefs"
    private const val KEY_STATUS = "status"
    private const val KEY_SELECTED_DURATION = "selected_duration"
    private const val KEY_REMAINING = "remaining"
    private const val KEY_TARGET_END = "target_end"
    private const val KEY_PAUSED_REMAINING = "paused_remaining"
    private const val KEY_OLED_MODE = "oled_mode"
    private const val KEY_SOUND_ENABLED = "sound_enabled"
    private const val KEY_FINISHED_VIBRATION_MODE = "finished_vibration_mode"
    private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
    private const val KEY_KEEP_SCREEN_AWAKE_ENABLED = "keep_screen_awake_enabled"
    private const val KEY_HIDE_STATUS_BAR_ENABLED = "hide_status_bar_enabled"
    private const val KEY_HIDE_STATUS_BAR_ONLY_WHEN_RUNNING = "hide_status_bar_only_when_running"
    private const val KEY_SHOW_CURRENT_TIME_ENABLED = "show_current_time_enabled"
    private const val KEY_SHOW_CLOCK_SECONDS_ENABLED = "show_clock_seconds_enabled"
    private const val KEY_CLOCK_POSITION = "clock_position"
    private const val KEY_CLOCK_TEXT_SIZE = "clock_text_size"
    private const val KEY_CLOCKWISE_MODE_ENABLED = "clockwise_mode_enabled"
    private const val KEY_CLEAN_MODE_ENABLED = "clean_mode_enabled"
    private const val KEY_HIDE_CLOCK_IN_CLEAN_MODE = "hide_clock_in_clean_mode"
    private const val KEY_THEME_MODE = "theme_mode"

    private val mutableState = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = mutableState.asStateFlow()

    private var initialized = false
    private lateinit var appContext: Context
    private lateinit var preferences: SharedPreferences

    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        mutableState.value = loadState()
        initialized = true
    }

    fun getState(): TimerState = mutableState.value

    fun update(transform: (TimerState) -> TimerState) {
        val newState = transform(mutableState.value)
        mutableState.value = newState
        if (initialized) {
            persistState(newState)
        }
    }

    private fun loadState(): TimerState {
        val statusName = preferences.getString(KEY_STATUS, TimerStatus.Idle.name) ?: TimerStatus.Idle.name
        val status = TimerStatus.entries.firstOrNull { it.name == statusName } ?: TimerStatus.Idle
        val persistedSelectedDuration = clampDuration(preferences.getLong(KEY_SELECTED_DURATION, 0L))
        val targetEndTime = preferences.takeIf { preferences.contains(KEY_TARGET_END) }?.getLong(KEY_TARGET_END, 0L)
        val pausedRemaining = preferences.takeIf { preferences.contains(KEY_PAUSED_REMAINING) }?.getLong(KEY_PAUSED_REMAINING, 0L)
        val persistedRemaining = clampDuration(preferences.getLong(KEY_REMAINING, persistedSelectedDuration))
        val now = System.currentTimeMillis()
        val recomputedRemaining = when {
            status == TimerStatus.Running && targetEndTime != null -> clampDuration(targetEndTime - now)
            status == TimerStatus.Paused && pausedRemaining != null -> clampDuration(pausedRemaining)
            status == TimerStatus.Finished -> 0L
            else -> persistedRemaining
        }
        val normalizedStatus = when {
            status == TimerStatus.Running && recomputedRemaining == 0L && persistedSelectedDuration > 0L -> TimerStatus.Finished
            else -> status
        }
        val selectedDuration = if (normalizedStatus == TimerStatus.Idle) 0L else persistedSelectedDuration
        val remainingMillis = if (normalizedStatus == TimerStatus.Idle) 0L else recomputedRemaining
        val clockPositionName = preferences.getString(KEY_CLOCK_POSITION, ClockPosition.Left.name)
            ?: ClockPosition.Left.name
        val clockPosition = ClockPosition.entries.firstOrNull { it.name == clockPositionName }
            ?: ClockPosition.Left
        val clockTextSizeName = preferences.getString(KEY_CLOCK_TEXT_SIZE, ClockTextSize.Medium.name)
            ?: ClockTextSize.Medium.name
        val clockTextSize = ClockTextSize.entries.firstOrNull { it.name == clockTextSizeName }
            ?: ClockTextSize.Medium
        val finishedVibrationMode = preferences.getString(KEY_FINISHED_VIBRATION_MODE, null)
            ?.let { modeName -> FinishedVibrationMode.entries.firstOrNull { it.name == modeName } }
            ?: if (preferences.getBoolean(KEY_VIBRATION_ENABLED, true)) {
                FinishedVibrationMode.OneMinute
            } else {
                FinishedVibrationMode.Off
            }

        return TimerState(
            status = normalizedStatus,
            selectedDurationMillis = selectedDuration,
            remainingMillis = remainingMillis,
            targetEndTimeMillis = if (normalizedStatus == TimerStatus.Running) targetEndTime else null,
            pausedRemainingMillis = if (normalizedStatus == TimerStatus.Paused) clampDuration(pausedRemaining ?: 0L) else null,
            isOledMode = preferences.getBoolean(KEY_OLED_MODE, false),
            soundEnabled = preferences.getBoolean(KEY_SOUND_ENABLED, true),
            finishedVibrationMode = finishedVibrationMode,
            keepScreenAwakeEnabled = preferences.getBoolean(KEY_KEEP_SCREEN_AWAKE_ENABLED, false),
            hideStatusBarEnabled = preferences.getBoolean(KEY_HIDE_STATUS_BAR_ENABLED, false),
            hideStatusBarOnlyWhenRunning = preferences.getBoolean(KEY_HIDE_STATUS_BAR_ONLY_WHEN_RUNNING, false),
            showCurrentTimeEnabled = preferences.getBoolean(KEY_SHOW_CURRENT_TIME_ENABLED, false),
            showClockSecondsEnabled = preferences.getBoolean(KEY_SHOW_CLOCK_SECONDS_ENABLED, false),
            clockPosition = clockPosition,
            clockTextSize = clockTextSize,
            clockwiseModeEnabled = preferences.getBoolean(KEY_CLOCKWISE_MODE_ENABLED, true),
            cleanModeEnabled = preferences.getBoolean(KEY_CLEAN_MODE_ENABLED, false),
            hideClockInCleanMode = preferences.getBoolean(KEY_HIDE_CLOCK_IN_CLEAN_MODE, false),
            themeMode = ThemeMode.entries.firstOrNull {
                it.name == preferences.getString(KEY_THEME_MODE, ThemeMode.System.name)
            } ?: ThemeMode.System,
        )
    }

    private fun persistState(state: TimerState) {
        preferences.edit()
            .putString(KEY_STATUS, state.status.name)
            .putLong(KEY_SELECTED_DURATION, state.selectedDurationMillis)
            .putLong(KEY_REMAINING, state.remainingMillis)
            .putBoolean(KEY_OLED_MODE, state.isOledMode)
            .putBoolean(KEY_SOUND_ENABLED, state.soundEnabled)
            .putString(KEY_FINISHED_VIBRATION_MODE, state.finishedVibrationMode.name)
            .putBoolean(KEY_KEEP_SCREEN_AWAKE_ENABLED, state.keepScreenAwakeEnabled)
            .putBoolean(KEY_HIDE_STATUS_BAR_ENABLED, state.hideStatusBarEnabled)
            .putBoolean(KEY_HIDE_STATUS_BAR_ONLY_WHEN_RUNNING, state.hideStatusBarOnlyWhenRunning)
            .putBoolean(KEY_SHOW_CURRENT_TIME_ENABLED, state.showCurrentTimeEnabled)
            .putBoolean(KEY_SHOW_CLOCK_SECONDS_ENABLED, state.showClockSecondsEnabled)
            .putString(KEY_CLOCK_POSITION, state.clockPosition.name)
            .putString(KEY_CLOCK_TEXT_SIZE, state.clockTextSize.name)
            .putBoolean(KEY_CLOCKWISE_MODE_ENABLED, state.clockwiseModeEnabled)
            .putBoolean(KEY_CLEAN_MODE_ENABLED, state.cleanModeEnabled)
            .putBoolean(KEY_HIDE_CLOCK_IN_CLEAN_MODE, state.hideClockInCleanMode)
            .putString(KEY_THEME_MODE, state.themeMode.name)
            .apply {
                if (state.targetEndTimeMillis != null) {
                    putLong(KEY_TARGET_END, state.targetEndTimeMillis)
                } else {
                    remove(KEY_TARGET_END)
                }
                if (state.pausedRemainingMillis != null) {
                    putLong(KEY_PAUSED_REMAINING, state.pausedRemainingMillis)
                } else {
                    remove(KEY_PAUSED_REMAINING)
                }
            }
            .apply()
    }
}
