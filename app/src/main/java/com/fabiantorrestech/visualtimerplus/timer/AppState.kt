package com.fabiantorrestech.visualtimerplus.timer

import androidx.compose.runtime.Immutable

enum class NotificationMode { Consolidated, Individual }

@Immutable
data class AppState(
    val timers: List<TimerInstance> = listOf(TimerInstance(id = 0)),
    val activeTimerIndex: Int = 0,
    val isOledMode: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val hideStatusBarEnabled: Boolean = false,
    val hideStatusBarOnlyWhenRunning: Boolean = false,
    val defaultTimerSettings: TimerSettings = TimerSettings(),
    val notificationMode: NotificationMode = NotificationMode.Consolidated,
    val hidePageDotsInCleanMode: Boolean = true,
    val confirmSwipeDelete: Boolean = true,
    val defaultDurationMillis: Long = 0L,
    val tapToToggleMinimalMode: Boolean = true,
    val notificationUpdateIntervalSeconds: Int = 15,
    val overlayEnabled: Boolean = true,
    val overlaySize: OverlaySize = OverlaySize.Medium,
    val overlayStyle: OverlayStyle = OverlayStyle.Ring,
    val overlayShowTimerName: Boolean = false,
    val overlayTimerNamePosition: OverlayLabelPosition = OverlayLabelPosition.Top,
    val overlayShowOnLockscreen: Boolean = false,
    val autoBackupEnabled: Boolean = false,
    val autoOpenAppAfterQuickStart: Boolean = true,
    val customFontPath: String? = null,
    val customFontDisplayName: String? = null,
) {
    val activeTimer: TimerInstance
        get() = timers.getOrElse(activeTimerIndex) { timers.first() }

    val keepScreenAwakeEnabled: Boolean
        get() = timers.any {
            it.settings.keepScreenAwake && (it.status == TimerStatus.Running || it.status == TimerStatus.Overtime)
        }

    val anyTimerRunningOrPaused: Boolean
        get() = timers.any {
            it.status == TimerStatus.Running || it.status == TimerStatus.Paused || it.status == TimerStatus.Overtime
        }

    val runningTimerCount: Int
        get() = timers.count { it.status == TimerStatus.Running }

    val overlayTimerIndex: Int?
        get() = timers.indexOfFirst { it.status == TimerStatus.Running }.takeIf { it >= 0 }
            ?: timers.indexOfFirst { it.status == TimerStatus.Overtime }.takeIf { it >= 0 }
            ?: timers.indexOfFirst { it.status == TimerStatus.Paused }.takeIf { it >= 0 }

    val overlayTimer: TimerInstance?
        get() = overlayTimerIndex?.let { timers[it] }

    fun withTimer(index: Int, transform: (TimerInstance) -> TimerInstance): AppState {
        if (index !in timers.indices) return this
        val updated = timers.toMutableList()
        updated[index] = transform(updated[index])
        return copy(timers = updated)
    }
}
