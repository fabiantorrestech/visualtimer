package com.fabiantorrestech.visualtimerplus.timer

sealed interface TimerAction {
    data class SetDuration(val durationMillis: Long) : TimerAction
    data class AdjustDuration(val deltaMillis: Long) : TimerAction
    data class SetSoundEnabled(val enabled: Boolean) : TimerAction
    data class SetFinishedVibrationMode(val mode: FinishedVibrationMode) : TimerAction
    data class SetKeepScreenAwakeEnabled(val enabled: Boolean) : TimerAction
    data class SetHideStatusBarEnabled(val enabled: Boolean) : TimerAction
    data class SetHideStatusBarOnlyWhenRunning(val enabled: Boolean) : TimerAction
    data class SetShowCurrentTimeEnabled(val enabled: Boolean) : TimerAction
    data class SetShowClockSecondsEnabled(val enabled: Boolean) : TimerAction
    data class SetClockPosition(val position: ClockPosition) : TimerAction
    data class SetClockTextSize(val size: ClockTextSize) : TimerAction
    data class SetClockwiseModeEnabled(val enabled: Boolean) : TimerAction
    data class SetCleanModeEnabled(val enabled: Boolean) : TimerAction
    data class SetHideClockInCleanMode(val enabled: Boolean) : TimerAction
    data class SetThemeMode(val mode: ThemeMode) : TimerAction
    data object Start : TimerAction
    data object Pause : TimerAction
    data object Resume : TimerAction
    data object Reset : TimerAction
    data object DismissFinished : TimerAction
}
