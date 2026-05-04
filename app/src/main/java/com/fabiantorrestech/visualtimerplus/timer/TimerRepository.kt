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
    private const val KEY_FINISHED_SOUND_ROUTE = "finished_sound_route"
    private const val KEY_FINISHED_SOUND_VOLUME = "finished_sound_volume"
    private const val KEY_OVERRIDE_MUTED_SYSTEM_VOLUME = "override_muted_system_volume"
    private const val KEY_IGNORE_SILENT_MODE = "ignore_silent_mode"
    private const val KEY_FULL_CLOCK_MODE = "full_clock_mode"
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
    private const val KEY_TIMER_TITLE_ENABLED = "timer_title_enabled"
    private const val KEY_TIMER_TITLE_HIDE_IN_CLEAN_MODE = "timer_title_hide_in_clean_mode"
    private const val KEY_TIMER_TITLE_POSITION = "timer_title_position"
    private const val KEY_TIMER_TITLE_SIZE = "timer_title_size"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ORIGINAL_DURATION = "original_duration"
    private const val KEY_ACTIVE_TIMER_NAME = "active_timer_name"
    private const val KEY_ACTIVE_PRESET_ID = "active_preset_id"
    private const val KEY_DEFAULT_DURATION = "default_duration"
    private const val KEY_PROMPT_BEFORE_START = "prompt_before_start"

    private val mutableState = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = mutableState.asStateFlow()

    @Volatile
    var isAppForeground: Boolean = false
        private set

    var activeLogEntryId: Long = -1L

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

    fun setAppForeground(foreground: Boolean) {
        isAppForeground = foreground
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
        val defaultDuration = clampDuration(preferences.getLong(KEY_DEFAULT_DURATION, 0L))
        val selectedDuration = when {
            normalizedStatus == TimerStatus.Idle -> defaultDuration
            else -> persistedSelectedDuration
        }
        val remainingMillis = if (normalizedStatus == TimerStatus.Idle) selectedDuration else recomputedRemaining

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
        val finishedSoundRoute = preferences.getString(KEY_FINISHED_SOUND_ROUTE, null)
            ?.let { routeName -> FinishedSoundRoute.entries.firstOrNull { it.name == routeName } }
            ?: FinishedSoundRoute.Default

        val persistedPresetId = preferences.getLong(KEY_ACTIVE_PRESET_ID, -1L)
        val activePresetId = if (persistedPresetId >= 0L) persistedPresetId else null

        return TimerState(
            status = normalizedStatus,
            selectedDurationMillis = selectedDuration,
            remainingMillis = remainingMillis,
            targetEndTimeMillis = if (normalizedStatus == TimerStatus.Running) targetEndTime else null,
            pausedRemainingMillis = if (normalizedStatus == TimerStatus.Paused) clampDuration(pausedRemaining ?: 0L) else null,
            isOledMode = preferences.getBoolean(KEY_OLED_MODE, false),
            soundEnabled = preferences.getBoolean(KEY_SOUND_ENABLED, true),
            finishedSoundRoute = finishedSoundRoute,
            finishedSoundVolumePercent = preferences.getInt(KEY_FINISHED_SOUND_VOLUME, 100).coerceIn(0, 100),
            overrideMutedSystemVolume = preferences.getBoolean(KEY_OVERRIDE_MUTED_SYSTEM_VOLUME, false),
            ignoreSilentMode = preferences.getBoolean(KEY_IGNORE_SILENT_MODE, false),
            fullClockMode = preferences.getBoolean(KEY_FULL_CLOCK_MODE, false),
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
            timerTitleEnabled = preferences.getBoolean(KEY_TIMER_TITLE_ENABLED, false),
            timerTitleHideInCleanMode = preferences.getBoolean(KEY_TIMER_TITLE_HIDE_IN_CLEAN_MODE, false),
            timerTitlePosition = ClockPosition.entries.firstOrNull {
                it.name == preferences.getString(KEY_TIMER_TITLE_POSITION, ClockPosition.Center.name)
            } ?: ClockPosition.Center,
            timerTitleSize = ClockTextSize.entries.firstOrNull {
                it.name == preferences.getString(KEY_TIMER_TITLE_SIZE, ClockTextSize.Medium.name)
            } ?: ClockTextSize.Medium,
            themeMode = ThemeMode.entries.firstOrNull {
                it.name == preferences.getString(KEY_THEME_MODE, ThemeMode.System.name)
            } ?: ThemeMode.System,
            originalDurationMillis = clampDuration(preferences.getLong(KEY_ORIGINAL_DURATION, 0L)),
            activeTimerName = preferences.getString(KEY_ACTIVE_TIMER_NAME, "") ?: "",
            activePresetId = activePresetId,
            defaultDurationMillis = defaultDuration,
            promptBeforeStart = preferences.getBoolean(KEY_PROMPT_BEFORE_START, false),
        )
    }

    private fun persistState(state: TimerState) {
        preferences.edit()
            .putString(KEY_STATUS, state.status.name)
            .putLong(KEY_SELECTED_DURATION, state.selectedDurationMillis)
            .putLong(KEY_REMAINING, state.remainingMillis)
            .putBoolean(KEY_OLED_MODE, state.isOledMode)
            .putBoolean(KEY_SOUND_ENABLED, state.soundEnabled)
            .putString(KEY_FINISHED_SOUND_ROUTE, state.finishedSoundRoute.name)
            .putInt(KEY_FINISHED_SOUND_VOLUME, state.finishedSoundVolumePercent)
            .putBoolean(KEY_OVERRIDE_MUTED_SYSTEM_VOLUME, state.overrideMutedSystemVolume)
            .putBoolean(KEY_IGNORE_SILENT_MODE, state.ignoreSilentMode)
            .putBoolean(KEY_FULL_CLOCK_MODE, state.fullClockMode)
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
            .putBoolean(KEY_TIMER_TITLE_ENABLED, state.timerTitleEnabled)
            .putBoolean(KEY_TIMER_TITLE_HIDE_IN_CLEAN_MODE, state.timerTitleHideInCleanMode)
            .putString(KEY_TIMER_TITLE_POSITION, state.timerTitlePosition.name)
            .putString(KEY_TIMER_TITLE_SIZE, state.timerTitleSize.name)
            .putString(KEY_THEME_MODE, state.themeMode.name)
            .putLong(KEY_ORIGINAL_DURATION, state.originalDurationMillis)
            .putString(KEY_ACTIVE_TIMER_NAME, state.activeTimerName)
            .putLong(KEY_ACTIVE_PRESET_ID, state.activePresetId ?: -1L)
            .putLong(KEY_DEFAULT_DURATION, state.defaultDurationMillis)
            .putBoolean(KEY_PROMPT_BEFORE_START, state.promptBeforeStart)
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
