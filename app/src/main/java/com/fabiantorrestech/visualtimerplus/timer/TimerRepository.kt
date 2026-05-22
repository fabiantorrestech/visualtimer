package com.fabiantorrestech.visualtimerplus.timer

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TimerRepository {
    private const val PREFS_NAME = "visual_timer_prefs"

    // ── App-global keys ────────────────────────────────────────────────────────
    private const val KEY_OLED_MODE = "oled_mode"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_HIDE_STATUS_BAR_ENABLED = "hide_status_bar_enabled"
    private const val KEY_HIDE_STATUS_BAR_ONLY_WHEN_RUNNING = "hide_status_bar_only_when_running"
    private const val KEY_NOTIFICATION_MODE = "notification_mode"
    private const val KEY_HIDE_PAGE_DOTS_IN_CLEAN_MODE = "hide_page_dots_in_clean_mode"
    private const val KEY_NOTIFICATION_UPDATE_INTERVAL = "notification_update_interval"
    private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
    private const val KEY_OVERLAY_SIZE = "overlay_size"
    private const val KEY_OVERLAY_STYLE = "overlay_style"
    private const val KEY_OVERLAY_SHOW_TIMER_NAME = "overlay_show_timer_name"
    private const val KEY_OVERLAY_TIMER_NAME_POSITION = "overlay_timer_name_position"
    private const val KEY_OVERLAY_SHOW_ON_LOCKSCREEN = "overlay_show_on_lockscreen"
    private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
    private const val KEY_AUTO_OPEN_APP_AFTER_QUICK_START = "auto_open_app_after_quick_start"
    private const val KEY_CUSTOM_FONT_PATH = "custom_font_path"
    private const val KEY_CUSTOM_FONT_DISPLAY_NAME = "custom_font_display_name"

    // ── Timer count ────────────────────────────────────────────────────────────
    private const val KEY_TIMER_COUNT = "timer_count"
    private const val KEY_ACTIVE_TIMER_INDEX = "active_timer_index"

    // ── Default timer settings keys (prefix) ──────────────────────────────────
    private const val DEFAULT_PREFIX = "default_"

    // ── Per-timer key prefixes ─────────────────────────────────────────────────
    // Keys are prefixed with "t{n}_" for timer index n
    private const val K_STATUS = "status"
    private const val K_SELECTED_DURATION = "selected_duration"
    private const val K_REMAINING = "remaining"
    private const val K_TARGET_END = "target_end"
    private const val K_PAUSED_REMAINING = "paused_remaining"
    private const val K_ORIGINAL_DURATION = "original_duration"
    private const val K_ACTIVE_TIMER_NAME = "active_timer_name"
    private const val K_ACTIVE_PRESET_ID = "active_preset_id"
    private const val K_DEFAULT_DURATION = "default_duration"
    private const val K_ACTIVE_LOG_ENTRY_ID = "active_log_entry_id"
    private const val K_SCHEDULE_ID = "schedule_id"
    private const val K_TOTAL_ADJUSTMENT = "total_adjustment"
    private const val K_TIME_TO_DISMISS_ACCUMULATED = "time_to_dismiss_accumulated"
    private const val K_OVERTIME_STARTED_AT = "overtime_started_at"
    private const val K_SOUND_ENABLED = "sound_enabled"
    private const val K_FINISHED_SOUND_ROUTE = "finished_sound_route"
    private const val K_FINISHED_SOUND_VOLUME = "finished_sound_volume"
    private const val K_OVERRIDE_MUTED_SYSTEM_VOLUME = "override_muted_system_volume"
    private const val K_IGNORE_SILENT_MODE = "ignore_silent_mode"
    private const val K_FULL_CLOCK_MODE = "full_clock_mode"
    private const val K_FINISHED_VIBRATION_MODE = "finished_vibration_mode"
    private const val K_KEEP_SCREEN_AWAKE = "keep_screen_awake"
    private const val K_SHOW_CURRENT_TIME = "show_current_time_enabled"
    private const val K_SHOW_CLOCK_SECONDS = "show_clock_seconds_enabled"
    private const val K_CLOCK_POSITION = "clock_position"
    private const val K_CLOCK_TEXT_SIZE_SP = "clock_text_size_sp"
    private const val K_CLOCKWISE_MODE = "clockwise_mode_enabled"
    private const val K_SHOW_DIRECTION_INDICATOR = "show_direction_indicator"
    private const val K_CLEAN_MODE = "clean_mode_enabled"
    private const val K_CLEAN_MODE_AUTO_DISMISS = "clean_mode_auto_dismiss_seconds"
    private const val K_CLEAN_MODE_AUTO_DISMISS_ENABLED = "clean_mode_auto_dismiss_enabled"
    private const val K_HIDE_CLOCK_IN_CLEAN_MODE = "hide_clock_in_clean_mode"
    private const val K_TIMER_TITLE_ENABLED = "timer_title_enabled"
    private const val K_TIMER_TITLE_HIDE_IN_CLEAN_MODE = "timer_title_hide_in_clean_mode"
    private const val K_TIMER_TITLE_POSITION = "timer_title_position"
    private const val K_TIMER_TITLE_TEXT_SIZE_SP = "timer_title_text_size_sp"
    private const val K_CENTER_TIME_SIZE_SP = "center_time_size_sp"
    private const val K_PROMPT_BEFORE_START = "prompt_before_start"
    private const val K_SHOW_END_TIME = "show_end_time_enabled"
    private const val K_SHOW_END_TIME_SECONDS = "show_end_time_seconds_enabled"
    private const val K_END_TIME_SIZE_SP = "end_time_size_sp"

    // ── Legacy single-timer keys (for migration) ───────────────────────────────
    private const val LEGACY_KEY_STATUS = "status"
    private const val LEGACY_KEY_SELECTED_DURATION = "selected_duration"
    private const val LEGACY_KEY_REMAINING = "remaining"
    private const val LEGACY_KEY_TARGET_END = "target_end"
    private const val LEGACY_KEY_PAUSED_REMAINING = "paused_remaining"
    private const val LEGACY_KEY_ORIGINAL_DURATION = "original_duration"
    private const val LEGACY_KEY_ACTIVE_TIMER_NAME = "active_timer_name"
    private const val LEGACY_KEY_ACTIVE_PRESET_ID = "active_preset_id"
    private const val LEGACY_KEY_DEFAULT_DURATION = "default_duration"
    private const val LEGACY_KEY_SOUND_ENABLED = "sound_enabled"
    private const val LEGACY_KEY_FINISHED_SOUND_ROUTE = "finished_sound_route"
    private const val LEGACY_KEY_FINISHED_SOUND_VOLUME = "finished_sound_volume"
    private const val LEGACY_KEY_OVERRIDE_MUTED_SYSTEM_VOLUME = "override_muted_system_volume"
    private const val LEGACY_KEY_IGNORE_SILENT_MODE = "ignore_silent_mode"
    private const val LEGACY_KEY_FULL_CLOCK_MODE = "full_clock_mode"
    private const val LEGACY_KEY_FINISHED_VIBRATION_MODE = "finished_vibration_mode"
    private const val LEGACY_KEY_VIBRATION_ENABLED = "vibration_enabled"
    private const val LEGACY_KEY_KEEP_SCREEN_AWAKE_ENABLED = "keep_screen_awake_enabled"
    private const val LEGACY_KEY_SHOW_CURRENT_TIME = "show_current_time_enabled"
    private const val LEGACY_KEY_SHOW_CLOCK_SECONDS = "show_clock_seconds_enabled"
    private const val LEGACY_KEY_CLOCK_POSITION = "clock_position"
    private const val LEGACY_KEY_CLOCK_TEXT_SIZE_SP = "clock_text_size_sp"
    private const val LEGACY_KEY_CLOCKWISE_MODE = "clockwise_mode_enabled"
    private const val LEGACY_KEY_CLEAN_MODE = "clean_mode_enabled"
    private const val LEGACY_KEY_CLEAN_MODE_AUTO_DISMISS = "clean_mode_auto_dismiss_seconds"
    private const val LEGACY_KEY_HIDE_CLOCK_IN_CLEAN_MODE = "hide_clock_in_clean_mode"
    private const val LEGACY_KEY_TIMER_TITLE_ENABLED = "timer_title_enabled"
    private const val LEGACY_KEY_TIMER_TITLE_HIDE_IN_CLEAN_MODE = "timer_title_hide_in_clean_mode"
    private const val LEGACY_KEY_TIMER_TITLE_POSITION = "timer_title_position"
    private const val LEGACY_KEY_TIMER_TITLE_TEXT_SIZE_SP = "timer_title_text_size_sp"
    private const val LEGACY_KEY_CENTER_TIME_SIZE_SP = "center_time_size_sp"
    private const val LEGACY_KEY_PROMPT_BEFORE_START = "prompt_before_start"

    private val mutableState = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = mutableState.asStateFlow()

    @Volatile
    var isAppForeground: Boolean = false
        private set

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

    fun getState(): AppState = mutableState.value

    fun getTimer(index: Int): TimerInstance = mutableState.value.timers.getOrElse(index) {
        mutableState.value.timers.first()
    }

    fun update(transform: (AppState) -> AppState) {
        val newState = transform(mutableState.value)
        mutableState.value = newState
        if (initialized) persistState(newState)
    }

    fun updateTimer(index: Int, transform: (TimerInstance) -> TimerInstance) {
        update { state -> state.withTimer(index, transform) }
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    private fun loadState(): AppState {
        // Migration: if timer_count doesn't exist, migrate legacy single-timer keys to t0_*
        if (!preferences.contains(KEY_TIMER_COUNT)) {
            migrateLegacyState()
        }

        val timerCount = preferences.getInt(KEY_TIMER_COUNT, 1).coerceIn(1, MAX_TIMERS)
        val activeIndex = preferences.getInt(KEY_ACTIVE_TIMER_INDEX, 0).coerceIn(0, timerCount - 1)

        val timers = (0 until timerCount).map { i -> loadTimerInstance(i) }

        val notificationMode = preferences.getString(KEY_NOTIFICATION_MODE, null)
            ?.let { name -> NotificationMode.entries.firstOrNull { it.name == name } }
            ?: NotificationMode.Consolidated

        val themeMode = preferences.getString(KEY_THEME_MODE, null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.System

        val overlaySize = preferences.getString(KEY_OVERLAY_SIZE, null)
            ?.let { name -> OverlaySize.entries.firstOrNull { it.name == name } }
            ?: OverlaySize.Medium
        val overlayStyle = preferences.getString(KEY_OVERLAY_STYLE, null)
            ?.let { name -> OverlayStyle.entries.firstOrNull { it.name == name } }
            ?: OverlayStyle.Ring
        val overlayTimerNamePosition = preferences.getString(KEY_OVERLAY_TIMER_NAME_POSITION, null)
            ?.let { name -> OverlayLabelPosition.entries.firstOrNull { it.name == name } }
            ?: OverlayLabelPosition.Top

        return AppState(
            timers = timers,
            activeTimerIndex = activeIndex,
            isOledMode = preferences.getBoolean(KEY_OLED_MODE, false),
            themeMode = themeMode,
            hideStatusBarEnabled = preferences.getBoolean(KEY_HIDE_STATUS_BAR_ENABLED, false),
            hideStatusBarOnlyWhenRunning = preferences.getBoolean(KEY_HIDE_STATUS_BAR_ONLY_WHEN_RUNNING, false),
            defaultTimerSettings = loadTimerSettings(DEFAULT_PREFIX),
            notificationMode = notificationMode,
            hidePageDotsInCleanMode = preferences.getBoolean(KEY_HIDE_PAGE_DOTS_IN_CLEAN_MODE, true),
            notificationUpdateIntervalSeconds = preferences.getInt(KEY_NOTIFICATION_UPDATE_INTERVAL, 60),
            overlayEnabled = preferences.getBoolean(KEY_OVERLAY_ENABLED, true),
            overlaySize = overlaySize,
            overlayStyle = overlayStyle,
            overlayShowTimerName = preferences.getBoolean(KEY_OVERLAY_SHOW_TIMER_NAME, false),
            overlayTimerNamePosition = overlayTimerNamePosition,
            overlayShowOnLockscreen = preferences.getBoolean(KEY_OVERLAY_SHOW_ON_LOCKSCREEN, false),
            autoBackupEnabled = preferences.getBoolean(KEY_AUTO_BACKUP_ENABLED, false),
            autoOpenAppAfterQuickStart = preferences.getBoolean(KEY_AUTO_OPEN_APP_AFTER_QUICK_START, true),
            customFontPath = preferences.getString(KEY_CUSTOM_FONT_PATH, null),
            customFontDisplayName = preferences.getString(KEY_CUSTOM_FONT_DISPLAY_NAME, null),
        )
    }

    private fun loadTimerInstance(index: Int): TimerInstance {
        val p = "t${index}_"
        val statusName = preferences.getString("$p$K_STATUS", TimerStatus.Idle.name) ?: TimerStatus.Idle.name
        val status = TimerStatus.entries.firstOrNull { it.name == statusName } ?: TimerStatus.Idle
        val persistedSelectedDuration = clampDuration(preferences.getLong("$p$K_SELECTED_DURATION", 0L))
        val targetEndTime = if (preferences.contains("$p$K_TARGET_END"))
            preferences.getLong("$p$K_TARGET_END", 0L) else null
        val pausedRemaining = if (preferences.contains("$p$K_PAUSED_REMAINING"))
            preferences.getLong("$p$K_PAUSED_REMAINING", 0L) else null
        val persistedRemaining = preferences.getLong("$p$K_REMAINING", persistedSelectedDuration)
        val now = System.currentTimeMillis()
        val overtimeStartedAt = if (preferences.contains("$p$K_OVERTIME_STARTED_AT"))
            preferences.getLong("$p$K_OVERTIME_STARTED_AT", now) else null

        val recomputedRemaining = when {
            status == TimerStatus.Running && targetEndTime != null -> (targetEndTime - now).coerceAtLeast(0L)
            status == TimerStatus.Paused && pausedRemaining != null -> pausedRemaining.coerceAtLeast(0L)
            status == TimerStatus.Overtime -> (now - (overtimeStartedAt ?: now)).coerceAtLeast(0L)
            status == TimerStatus.Finished -> 0L
            else -> persistedRemaining.coerceAtLeast(0L)
        }

        val normalizedStatus = when {
            status == TimerStatus.Running && recomputedRemaining == 0L && persistedSelectedDuration > 0L -> TimerStatus.Overtime
            else -> status
        }

        val defaultDuration = clampDuration(preferences.getLong("$p$K_DEFAULT_DURATION", 0L))
        val selectedDuration = if (normalizedStatus == TimerStatus.Idle) defaultDuration else persistedSelectedDuration
        val remainingMillis = if (normalizedStatus == TimerStatus.Idle) selectedDuration else recomputedRemaining

        val persistedPresetId = preferences.getLong("$p$K_ACTIVE_PRESET_ID", -1L)
        val persistedScheduleId = preferences.getLong("$p$K_SCHEDULE_ID", -1L)

        return TimerInstance(
            id = index,
            status = normalizedStatus,
            selectedDurationMillis = selectedDuration,
            remainingMillis = remainingMillis,
            targetEndTimeMillis = if (normalizedStatus == TimerStatus.Running) targetEndTime else null,
            pausedRemainingMillis = if (normalizedStatus == TimerStatus.Paused) clampDuration(pausedRemaining ?: 0L) else null,
            originalDurationMillis = clampDuration(preferences.getLong("$p$K_ORIGINAL_DURATION", 0L)),
            activeTimerName = preferences.getString("$p$K_ACTIVE_TIMER_NAME", "") ?: "",
            activePresetId = if (persistedPresetId >= 0L) persistedPresetId else null,
            defaultDurationMillis = defaultDuration,
            settings = loadTimerSettings(p),
            activeLogEntryId = preferences.getLong("$p$K_ACTIVE_LOG_ENTRY_ID", -1L),
            scheduleId = if (persistedScheduleId >= 0L) persistedScheduleId else null,
            totalAdjustmentMillis = preferences.getLong("$p$K_TOTAL_ADJUSTMENT", 0L),
            timeToDismissAccumulatedMillis = preferences.getLong("$p$K_TIME_TO_DISMISS_ACCUMULATED", 0L).coerceAtLeast(0L),
            overtimeStartedAtMillis = if (normalizedStatus == TimerStatus.Overtime) overtimeStartedAt else null,
        )
    }

    private fun loadTimerSettings(prefix: String): TimerSettings {
        val finishedVibrationMode = preferences.getString("$prefix$K_FINISHED_VIBRATION_MODE", null)
            ?.let { name -> FinishedVibrationMode.entries.firstOrNull { it.name == name } }
            ?: if (preferences.getBoolean("${prefix}vibration_enabled", true))
                FinishedVibrationMode.OneMinute else FinishedVibrationMode.Off

        val finishedSoundRoute = preferences.getString("$prefix$K_FINISHED_SOUND_ROUTE", null)
            ?.let { name -> FinishedSoundRoute.entries.firstOrNull { it.name == name } }
            ?: FinishedSoundRoute.Default

        val clockPosition = preferences.getString("$prefix$K_CLOCK_POSITION", null)
            ?.let { name -> ClockPosition.entries.firstOrNull { it.name == name } }
            ?: ClockPosition.Left

        val timerTitlePosition = preferences.getString("$prefix$K_TIMER_TITLE_POSITION", null)
            ?.let { name -> ClockPosition.entries.firstOrNull { it.name == name } }
            ?: ClockPosition.Center

        return TimerSettings(
            soundEnabled = preferences.getBoolean("$prefix$K_SOUND_ENABLED", true),
            finishedSoundRoute = finishedSoundRoute,
            finishedSoundVolumePercent = preferences.getInt("$prefix$K_FINISHED_SOUND_VOLUME", 100).coerceIn(0, 100),
            overrideMutedSystemVolume = preferences.getBoolean("$prefix$K_OVERRIDE_MUTED_SYSTEM_VOLUME", false),
            ignoreSilentMode = preferences.getBoolean("$prefix$K_IGNORE_SILENT_MODE", false),
            fullClockMode = preferences.getBoolean("$prefix$K_FULL_CLOCK_MODE", false),
            finishedVibrationMode = finishedVibrationMode,
            keepScreenAwake = preferences.getBoolean("$prefix$K_KEEP_SCREEN_AWAKE", true),
            showCurrentTimeEnabled = preferences.getBoolean("$prefix$K_SHOW_CURRENT_TIME", false),
            showClockSecondsEnabled = preferences.getBoolean("$prefix$K_SHOW_CLOCK_SECONDS", false),
            clockPosition = clockPosition,
            clockTextSizeSp = preferences.getFloat("$prefix$K_CLOCK_TEXT_SIZE_SP", 32f).coerceIn(14f, 60f),
            clockwiseModeEnabled = preferences.getBoolean("$prefix$K_CLOCKWISE_MODE", true),
            cleanModeEnabled = preferences.getBoolean("$prefix$K_CLEAN_MODE", false),
            cleanModeAutoDismissEnabled = preferences.getBoolean("$prefix$K_CLEAN_MODE_AUTO_DISMISS_ENABLED", true),
            cleanModeAutoDismissSeconds = clampCleanModeAutoDismissSeconds(
                preferences.getInt("$prefix$K_CLEAN_MODE_AUTO_DISMISS", CLEAN_MODE_AUTO_DISMISS_DEFAULT_SECONDS)
            ),
            hideClockInCleanMode = preferences.getBoolean("$prefix$K_HIDE_CLOCK_IN_CLEAN_MODE", false),
            timerTitleEnabled = preferences.getBoolean("$prefix$K_TIMER_TITLE_ENABLED", false),
            timerTitleHideInCleanMode = preferences.getBoolean("$prefix$K_TIMER_TITLE_HIDE_IN_CLEAN_MODE", false),
            timerTitlePosition = timerTitlePosition,
            timerTitleTextSizeSp = preferences.getFloat("$prefix$K_TIMER_TITLE_TEXT_SIZE_SP", 16f).coerceIn(10f, 48f),
            centerTimeSizeSp = preferences.getFloat("$prefix$K_CENTER_TIME_SIZE_SP", 36f).coerceIn(20f, 80f),
            promptBeforeStart = preferences.getBoolean("$prefix$K_PROMPT_BEFORE_START", false),
            showEndTimeEnabled = preferences.getBoolean("$prefix$K_SHOW_END_TIME", false),
            showEndTimeSecondsEnabled = preferences.getBoolean("$prefix$K_SHOW_END_TIME_SECONDS", false),
            endTimeSizeSp = preferences.getFloat("$prefix$K_END_TIME_SIZE_SP", 32f).coerceIn(14f, 60f),
            showDirectionIndicator = preferences.getBoolean("$prefix$K_SHOW_DIRECTION_INDICATOR", true),
        )
    }

    fun reloadFromPrefs() {
        mutableState.value = loadState()
    }

    // ── Persist ───────────────────────────────────────────────────────────────

    private fun persistState(state: AppState) {
        val editor = preferences.edit()
            .putBoolean(KEY_OLED_MODE, state.isOledMode)
            .putString(KEY_THEME_MODE, state.themeMode.name)
            .putBoolean(KEY_HIDE_STATUS_BAR_ENABLED, state.hideStatusBarEnabled)
            .putBoolean(KEY_HIDE_STATUS_BAR_ONLY_WHEN_RUNNING, state.hideStatusBarOnlyWhenRunning)
            .putString(KEY_NOTIFICATION_MODE, state.notificationMode.name)
            .putBoolean(KEY_HIDE_PAGE_DOTS_IN_CLEAN_MODE, state.hidePageDotsInCleanMode)
            .putInt(KEY_NOTIFICATION_UPDATE_INTERVAL, state.notificationUpdateIntervalSeconds)
            .putBoolean(KEY_OVERLAY_ENABLED, state.overlayEnabled)
            .putString(KEY_OVERLAY_SIZE, state.overlaySize.name)
            .putString(KEY_OVERLAY_STYLE, state.overlayStyle.name)
            .putBoolean(KEY_OVERLAY_SHOW_TIMER_NAME, state.overlayShowTimerName)
            .putString(KEY_OVERLAY_TIMER_NAME_POSITION, state.overlayTimerNamePosition.name)
            .putBoolean(KEY_OVERLAY_SHOW_ON_LOCKSCREEN, state.overlayShowOnLockscreen)
            .putBoolean(KEY_AUTO_BACKUP_ENABLED, state.autoBackupEnabled)
            .putBoolean(KEY_AUTO_OPEN_APP_AFTER_QUICK_START, state.autoOpenAppAfterQuickStart)
            .putString(KEY_CUSTOM_FONT_PATH, state.customFontPath)
            .putString(KEY_CUSTOM_FONT_DISPLAY_NAME, state.customFontDisplayName)
            .putInt(KEY_TIMER_COUNT, state.timers.size)
            .putInt(KEY_ACTIVE_TIMER_INDEX, state.activeTimerIndex)

        persistTimerSettings(editor, DEFAULT_PREFIX, state.defaultTimerSettings)

        state.timers.forEach { timer ->
            persistTimerInstance(editor, timer)
        }

        editor.apply()
    }

    private fun persistTimerInstance(editor: SharedPreferences.Editor, timer: TimerInstance) {
        val p = "t${timer.id}_"
        editor
            .putString("$p$K_STATUS", timer.status.name)
            .putLong("$p$K_SELECTED_DURATION", timer.selectedDurationMillis)
            .putLong("$p$K_REMAINING", timer.remainingMillis)
            .putLong("$p$K_ORIGINAL_DURATION", timer.originalDurationMillis)
            .putString("$p$K_ACTIVE_TIMER_NAME", timer.activeTimerName)
            .putLong("$p$K_ACTIVE_PRESET_ID", timer.activePresetId ?: -1L)
            .putLong("$p$K_DEFAULT_DURATION", timer.defaultDurationMillis)
            .putLong("$p$K_ACTIVE_LOG_ENTRY_ID", timer.activeLogEntryId)
            .putLong("$p$K_SCHEDULE_ID", timer.scheduleId ?: -1L)
            .putLong("$p$K_TOTAL_ADJUSTMENT", timer.totalAdjustmentMillis)
            .putLong("$p$K_TIME_TO_DISMISS_ACCUMULATED", timer.timeToDismissAccumulatedMillis)

        if (timer.targetEndTimeMillis != null) {
            editor.putLong("$p$K_TARGET_END", timer.targetEndTimeMillis)
        } else {
            editor.remove("$p$K_TARGET_END")
        }
        if (timer.pausedRemainingMillis != null) {
            editor.putLong("$p$K_PAUSED_REMAINING", timer.pausedRemainingMillis)
        } else {
            editor.remove("$p$K_PAUSED_REMAINING")
        }
        if (timer.overtimeStartedAtMillis != null) {
            editor.putLong("$p$K_OVERTIME_STARTED_AT", timer.overtimeStartedAtMillis)
        } else {
            editor.remove("$p$K_OVERTIME_STARTED_AT")
        }

        persistTimerSettings(editor, p, timer.settings)
    }

    private fun persistTimerSettings(editor: SharedPreferences.Editor, prefix: String, settings: TimerSettings) {
        editor
            .putBoolean("$prefix$K_SOUND_ENABLED", settings.soundEnabled)
            .putString("$prefix$K_FINISHED_SOUND_ROUTE", settings.finishedSoundRoute.name)
            .putInt("$prefix$K_FINISHED_SOUND_VOLUME", settings.finishedSoundVolumePercent)
            .putBoolean("$prefix$K_OVERRIDE_MUTED_SYSTEM_VOLUME", settings.overrideMutedSystemVolume)
            .putBoolean("$prefix$K_IGNORE_SILENT_MODE", settings.ignoreSilentMode)
            .putBoolean("$prefix$K_FULL_CLOCK_MODE", settings.fullClockMode)
            .putString("$prefix$K_FINISHED_VIBRATION_MODE", settings.finishedVibrationMode.name)
            .putBoolean("$prefix$K_KEEP_SCREEN_AWAKE", settings.keepScreenAwake)
            .putBoolean("$prefix$K_SHOW_CURRENT_TIME", settings.showCurrentTimeEnabled)
            .putBoolean("$prefix$K_SHOW_CLOCK_SECONDS", settings.showClockSecondsEnabled)
            .putString("$prefix$K_CLOCK_POSITION", settings.clockPosition.name)
            .putFloat("$prefix$K_CLOCK_TEXT_SIZE_SP", settings.clockTextSizeSp)
            .putBoolean("$prefix$K_CLOCKWISE_MODE", settings.clockwiseModeEnabled)
            .putBoolean("$prefix$K_CLEAN_MODE", settings.cleanModeEnabled)
            .putBoolean("$prefix$K_CLEAN_MODE_AUTO_DISMISS_ENABLED", settings.cleanModeAutoDismissEnabled)
            .putInt("$prefix$K_CLEAN_MODE_AUTO_DISMISS", clampCleanModeAutoDismissSeconds(settings.cleanModeAutoDismissSeconds))
            .putBoolean("$prefix$K_HIDE_CLOCK_IN_CLEAN_MODE", settings.hideClockInCleanMode)
            .putBoolean("$prefix$K_TIMER_TITLE_ENABLED", settings.timerTitleEnabled)
            .putBoolean("$prefix$K_TIMER_TITLE_HIDE_IN_CLEAN_MODE", settings.timerTitleHideInCleanMode)
            .putString("$prefix$K_TIMER_TITLE_POSITION", settings.timerTitlePosition.name)
            .putFloat("$prefix$K_TIMER_TITLE_TEXT_SIZE_SP", settings.timerTitleTextSizeSp)
            .putFloat("$prefix$K_CENTER_TIME_SIZE_SP", settings.centerTimeSizeSp)
            .putBoolean("$prefix$K_PROMPT_BEFORE_START", settings.promptBeforeStart)
            .putBoolean("$prefix$K_SHOW_END_TIME", settings.showEndTimeEnabled)
            .putBoolean("$prefix$K_SHOW_END_TIME_SECONDS", settings.showEndTimeSecondsEnabled)
            .putFloat("$prefix$K_END_TIME_SIZE_SP", settings.endTimeSizeSp)
            .putBoolean("$prefix$K_SHOW_DIRECTION_INDICATOR", settings.showDirectionIndicator)
    }

    // ── Migration from legacy single-timer flat keys ───────────────────────────

    private fun migrateLegacyState() {
        val editor = preferences.edit()

        // Migrate per-timer state to t0_* keys
        val p = "t0_"

        fun copyStr(legacy: String, new: String) {
            if (preferences.contains(legacy)) {
                editor.putString("$p$new", preferences.getString(legacy, null))
            }
        }
        fun copyLong(legacy: String, new: String) {
            if (preferences.contains(legacy)) {
                editor.putLong("$p$new", preferences.getLong(legacy, 0L))
            }
        }
        fun copyBool(legacy: String, new: String) {
            if (preferences.contains(legacy)) {
                editor.putBoolean("$p$new", preferences.getBoolean(legacy, false))
            }
        }
        fun copyInt(legacy: String, new: String) {
            if (preferences.contains(legacy)) {
                editor.putInt("$p$new", preferences.getInt(legacy, 0))
            }
        }
        fun copyFloat(legacy: String, new: String) {
            if (preferences.contains(legacy)) {
                editor.putFloat("$p$new", preferences.getFloat(legacy, 0f))
            }
        }

        copyStr(LEGACY_KEY_STATUS, K_STATUS)
        copyLong(LEGACY_KEY_SELECTED_DURATION, K_SELECTED_DURATION)
        copyLong(LEGACY_KEY_REMAINING, K_REMAINING)
        copyLong(LEGACY_KEY_ORIGINAL_DURATION, K_ORIGINAL_DURATION)
        copyStr(LEGACY_KEY_ACTIVE_TIMER_NAME, K_ACTIVE_TIMER_NAME)
        copyLong(LEGACY_KEY_ACTIVE_PRESET_ID, K_ACTIVE_PRESET_ID)
        copyLong(LEGACY_KEY_DEFAULT_DURATION, K_DEFAULT_DURATION)
        copyStr(LEGACY_KEY_FINISHED_SOUND_ROUTE, K_FINISHED_SOUND_ROUTE)
        copyInt(LEGACY_KEY_FINISHED_SOUND_VOLUME, K_FINISHED_SOUND_VOLUME)
        copyBool(LEGACY_KEY_SOUND_ENABLED, K_SOUND_ENABLED)
        copyBool(LEGACY_KEY_OVERRIDE_MUTED_SYSTEM_VOLUME, K_OVERRIDE_MUTED_SYSTEM_VOLUME)
        copyBool(LEGACY_KEY_IGNORE_SILENT_MODE, K_IGNORE_SILENT_MODE)
        copyBool(LEGACY_KEY_FULL_CLOCK_MODE, K_FULL_CLOCK_MODE)
        copyStr(LEGACY_KEY_FINISHED_VIBRATION_MODE, K_FINISHED_VIBRATION_MODE)
        copyBool(LEGACY_KEY_KEEP_SCREEN_AWAKE_ENABLED, K_KEEP_SCREEN_AWAKE)
        copyBool(LEGACY_KEY_SHOW_CURRENT_TIME, K_SHOW_CURRENT_TIME)
        copyBool(LEGACY_KEY_SHOW_CLOCK_SECONDS, K_SHOW_CLOCK_SECONDS)
        copyStr(LEGACY_KEY_CLOCK_POSITION, K_CLOCK_POSITION)
        copyFloat(LEGACY_KEY_CLOCK_TEXT_SIZE_SP, K_CLOCK_TEXT_SIZE_SP)
        copyBool(LEGACY_KEY_CLOCKWISE_MODE, K_CLOCKWISE_MODE)
        copyBool(LEGACY_KEY_CLEAN_MODE, K_CLEAN_MODE)
        copyInt(LEGACY_KEY_CLEAN_MODE_AUTO_DISMISS, K_CLEAN_MODE_AUTO_DISMISS)
        copyBool(LEGACY_KEY_HIDE_CLOCK_IN_CLEAN_MODE, K_HIDE_CLOCK_IN_CLEAN_MODE)
        copyBool(LEGACY_KEY_TIMER_TITLE_ENABLED, K_TIMER_TITLE_ENABLED)
        copyBool(LEGACY_KEY_TIMER_TITLE_HIDE_IN_CLEAN_MODE, K_TIMER_TITLE_HIDE_IN_CLEAN_MODE)
        copyStr(LEGACY_KEY_TIMER_TITLE_POSITION, K_TIMER_TITLE_POSITION)
        copyFloat(LEGACY_KEY_TIMER_TITLE_TEXT_SIZE_SP, K_TIMER_TITLE_TEXT_SIZE_SP)
        copyFloat(LEGACY_KEY_CENTER_TIME_SIZE_SP, K_CENTER_TIME_SIZE_SP)
        copyBool(LEGACY_KEY_PROMPT_BEFORE_START, K_PROMPT_BEFORE_START)

        if (preferences.contains(LEGACY_KEY_TARGET_END)) {
            editor.putLong("$p$K_TARGET_END", preferences.getLong(LEGACY_KEY_TARGET_END, 0L))
        }
        if (preferences.contains(LEGACY_KEY_PAUSED_REMAINING)) {
            editor.putLong("$p$K_PAUSED_REMAINING", preferences.getLong(LEGACY_KEY_PAUSED_REMAINING, 0L))
        }

        // Global keys that were already global stay as-is (oled_mode, theme_mode, etc.)
        // But hide_status_bar_enabled and hide_status_bar_only_when_running need no migration — same key names

        editor.putInt(KEY_TIMER_COUNT, 1)
        editor.putInt(KEY_ACTIVE_TIMER_INDEX, 0)
        editor.apply()
    }
}
