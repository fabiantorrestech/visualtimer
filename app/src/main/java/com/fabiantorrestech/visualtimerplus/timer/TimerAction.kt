package com.fabiantorrestech.visualtimerplus.timer

sealed interface TimerAction {
    // Per-timer actions — timerIndex = -1 means "use activeTimerIndex"
    data class SetDuration(val durationMillis: Long, val timerIndex: Int = -1) : TimerAction
    data class SetDurationExact(val durationMillis: Long, val timerIndex: Int = -1) : TimerAction
    data class AdjustDuration(val deltaMillis: Long, val timerIndex: Int = -1) : TimerAction
    data class SetDefaultDuration(val durationMillis: Long, val timerIndex: Int = -1) : TimerAction
    data class SetActiveTimerName(val name: String, val timerIndex: Int = -1) : TimerAction
    data class SetActivePresetId(val id: Long?, val timerIndex: Int = -1) : TimerAction
    data class SetPromptBeforeStart(val enabled: Boolean, val timerIndex: Int = -1) : TimerAction

    // Per-timer settings actions
    data class SetSoundEnabled(val enabled: Boolean, val timerIndex: Int = -1) : TimerAction
    data class SetFinishedSoundRoute(val route: FinishedSoundRoute, val timerIndex: Int = -1) : TimerAction
    data class SetFinishedSoundVolumePercent(val percent: Int, val timerIndex: Int = -1) : TimerAction
    data class SetOverrideMutedSystemVolume(val enabled: Boolean, val timerIndex: Int = -1) : TimerAction
    data class SetIgnoreSilentMode(val enabled: Boolean, val timerIndex: Int = -1) : TimerAction
    data class SetFullClockMode(val enabled: Boolean, val timerIndex: Int = -1) : TimerAction
    data class SetFinishedVibrationMode(val mode: FinishedVibrationMode, val timerIndex: Int = -1) : TimerAction
    data class SetKeepScreenAwakeEnabled(val enabled: Boolean, val timerIndex: Int = -1) : TimerAction
    data class SetShowCurrentTimeEnabled(val enabled: Boolean, val timerIndex: Int = -1) : TimerAction
    data class SetShowClockSecondsEnabled(val enabled: Boolean, val timerIndex: Int = -1) : TimerAction
    data class SetClockPosition(val position: ClockPosition, val timerIndex: Int = -1) : TimerAction
    data class SetClockTextSizeSp(val sp: Float, val timerIndex: Int = -1) : TimerAction
    data class SetClockwiseModeEnabled(val enabled: Boolean, val timerIndex: Int = -1) : TimerAction
    data class SetShowDirectionIndicator(val enabled: Boolean, val timerIndex: Int = -1) : TimerAction
    data class SetCleanModeEnabled(val enabled: Boolean, val timerIndex: Int = -1) : TimerAction
    data class SetCleanModeAutoDismissEnabled(val enabled: Boolean, val timerIndex: Int = -1) : TimerAction
    data class SetCleanModeAutoDismissSeconds(val seconds: Int, val timerIndex: Int = -1) : TimerAction
    data class SetHideClockInCleanMode(val enabled: Boolean, val timerIndex: Int = -1) : TimerAction
    data class SetTimerTitleEnabled(val enabled: Boolean, val timerIndex: Int = -1) : TimerAction
    data class SetTimerTitleHideInCleanMode(val enabled: Boolean, val timerIndex: Int = -1) : TimerAction
    data class SetTimerTitlePosition(val position: ClockPosition, val timerIndex: Int = -1) : TimerAction
    data class SetTimerTitleTextSizeSp(val sp: Float, val timerIndex: Int = -1) : TimerAction
    data class SetCenterTimeSizeSp(val sp: Float, val timerIndex: Int = -1) : TimerAction
    data class SetShowEndTimeEnabled(val enabled: Boolean, val timerIndex: Int = -1) : TimerAction
    data class SetShowEndTimeSecondsEnabled(val enabled: Boolean, val timerIndex: Int = -1) : TimerAction
    data class SetEndTimeSizeSp(val sp: Float, val timerIndex: Int = -1) : TimerAction

    // Per-timer lifecycle actions — timerIndex = -1 means "use activeTimerIndex"
    data class Start(val timerIndex: Int = -1) : TimerAction
    data class Pause(val timerIndex: Int = -1) : TimerAction
    data class Resume(val timerIndex: Int = -1) : TimerAction
    data class Reset(val timerIndex: Int = -1) : TimerAction
    data class DismissFinished(val timerIndex: Int = -1) : TimerAction
    data class Restart(val timerIndex: Int = -1) : TimerAction

    // App-global settings actions
    data class SetThemeMode(val mode: ThemeMode) : TimerAction
    data class SetOledMode(val enabled: Boolean) : TimerAction
    data class SetHideStatusBarEnabled(val enabled: Boolean) : TimerAction
    data class SetHideStatusBarOnlyWhenRunning(val enabled: Boolean) : TimerAction
    data class SetNotificationMode(val mode: NotificationMode) : TimerAction
    data class SetHidePageDotsInCleanMode(val enabled: Boolean) : TimerAction
    data class SetDefaultTimerSettings(val settings: TimerSettings) : TimerAction
    data class SetConfirmSwipeDelete(val enabled: Boolean) : TimerAction
    data class SetAppDefaultDuration(val durationMillis: Long) : TimerAction
    data class SetTapToToggleMinimalMode(val enabled: Boolean) : TimerAction
    data class SetNotificationUpdateInterval(val seconds: Int) : TimerAction
    data class SetOverlayEnabled(val enabled: Boolean) : TimerAction
    data class SetOverlaySize(val size: OverlaySize) : TimerAction
    data class SetOverlayStyle(val style: OverlayStyle) : TimerAction
    data class SetOverlayShowTimerName(val enabled: Boolean) : TimerAction
    data class SetOverlayTimerNamePosition(val position: OverlayLabelPosition) : TimerAction
    data class SetOverlayShowOnLockscreen(val enabled: Boolean) : TimerAction
    data class SetFinishedAlertMode(val mode: FinishedAlertMode) : TimerAction
    data class SetShowMissingFinishedAlertPermissionsBanner(val enabled: Boolean) : TimerAction
    data class SetAutoBackupEnabled(val enabled: Boolean) : TimerAction
    data class SetAutoOpenAppAfterQuickStart(val enabled: Boolean) : TimerAction
    data class SetQuickTimerLandscapePlacement(val placement: QuickTimerLandscapePlacement) : TimerAction
    data class SetCustomFont(val path: String?, val displayName: String?) : TimerAction

    // Multi-timer management
    data object AddTimer : TimerAction
    data class RemoveTimer(val timerIndex: Int) : TimerAction
    data class SetActiveTimer(val index: Int) : TimerAction
    data object RemoveAllTimers : TimerAction
    data object RemoveNonRunningTimers : TimerAction
}

fun TimerAction.withTimerIndex(index: Int): TimerAction = when (this) {
    is TimerAction.SetDuration -> copy(timerIndex = index)
    is TimerAction.SetDurationExact -> copy(timerIndex = index)
    is TimerAction.AdjustDuration -> copy(timerIndex = index)
    is TimerAction.SetDefaultDuration -> copy(timerIndex = index)
    is TimerAction.SetActiveTimerName -> copy(timerIndex = index)
    is TimerAction.SetActivePresetId -> copy(timerIndex = index)
    is TimerAction.SetPromptBeforeStart -> copy(timerIndex = index)
    is TimerAction.SetSoundEnabled -> copy(timerIndex = index)
    is TimerAction.SetFinishedSoundRoute -> copy(timerIndex = index)
    is TimerAction.SetFinishedSoundVolumePercent -> copy(timerIndex = index)
    is TimerAction.SetOverrideMutedSystemVolume -> copy(timerIndex = index)
    is TimerAction.SetIgnoreSilentMode -> copy(timerIndex = index)
    is TimerAction.SetFullClockMode -> copy(timerIndex = index)
    is TimerAction.SetFinishedVibrationMode -> copy(timerIndex = index)
    is TimerAction.SetKeepScreenAwakeEnabled -> copy(timerIndex = index)
    is TimerAction.SetShowCurrentTimeEnabled -> copy(timerIndex = index)
    is TimerAction.SetShowClockSecondsEnabled -> copy(timerIndex = index)
    is TimerAction.SetClockPosition -> copy(timerIndex = index)
    is TimerAction.SetClockTextSizeSp -> copy(timerIndex = index)
    is TimerAction.SetClockwiseModeEnabled -> copy(timerIndex = index)
    is TimerAction.SetShowDirectionIndicator -> copy(timerIndex = index)
    is TimerAction.SetCleanModeEnabled -> copy(timerIndex = index)
    is TimerAction.SetCleanModeAutoDismissEnabled -> copy(timerIndex = index)
    is TimerAction.SetCleanModeAutoDismissSeconds -> copy(timerIndex = index)
    is TimerAction.SetHideClockInCleanMode -> copy(timerIndex = index)
    is TimerAction.SetTimerTitleEnabled -> copy(timerIndex = index)
    is TimerAction.SetTimerTitleHideInCleanMode -> copy(timerIndex = index)
    is TimerAction.SetTimerTitlePosition -> copy(timerIndex = index)
    is TimerAction.SetTimerTitleTextSizeSp -> copy(timerIndex = index)
    is TimerAction.SetCenterTimeSizeSp -> copy(timerIndex = index)
    is TimerAction.SetShowEndTimeEnabled -> copy(timerIndex = index)
    is TimerAction.SetShowEndTimeSecondsEnabled -> copy(timerIndex = index)
    is TimerAction.SetEndTimeSizeSp -> copy(timerIndex = index)
    is TimerAction.Start -> copy(timerIndex = index)
    is TimerAction.Pause -> copy(timerIndex = index)
    is TimerAction.Resume -> copy(timerIndex = index)
    is TimerAction.Reset -> copy(timerIndex = index)
    is TimerAction.DismissFinished -> copy(timerIndex = index)
    is TimerAction.Restart -> copy(timerIndex = index)
    else -> this
}
