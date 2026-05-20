package com.fabiantorrestech.visualtimerplus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabiantorrestech.visualtimerplus.db.AppDatabase
import com.fabiantorrestech.visualtimerplus.notification.TimerNotificationManager
import com.fabiantorrestech.visualtimerplus.overlay.TimerOverlayManager
import com.fabiantorrestech.visualtimerplus.timer.ThemeMode
import com.fabiantorrestech.visualtimerplus.timer.TimerAction
import com.fabiantorrestech.visualtimerplus.timer.TimerController
import com.fabiantorrestech.visualtimerplus.timer.TimerRepository
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import com.fabiantorrestech.visualtimerplus.ui.screen.TimerLogScreen
import com.fabiantorrestech.visualtimerplus.ui.screen.TimerScreen
import com.fabiantorrestech.visualtimerplus.ui.theme.VisualTimerPlusTheme

sealed class AppScreen {
    object Timer : AppScreen()
    object TimerLog : AppScreen()
}

class MainActivity : ComponentActivity() {
    private data class TimerLaunchTarget(val index: Int, val token: Long = System.nanoTime())

    private val pendingTimerLaunchTarget = mutableStateOf<TimerLaunchTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TimerRepository.initialize(applicationContext)
        TimerRepository.setAppForeground(true)
        TimerOverlayManager.initialize(applicationContext)
        TimerOverlayManager.setAppForeground(true)
        pendingTimerLaunchTarget.value = extractTimerLaunchTarget(intent)

        setContent {
            val controller = remember { TimerController(applicationContext) }
            val uiState by controller.uiState.collectAsStateWithLifecycle()
            val db = remember { AppDatabase.getInstance(applicationContext) }
            val lifecycleOwner = LocalLifecycleOwner.current
            var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Timer) }
            val launchTarget = pendingTimerLaunchTarget.value
            val openPresetsOnLaunch = remember {
                intent?.action == "com.fabiantorrestech.visualtimerplus.OPEN_PRESETS"
            }
            var overlayPermissionGranted by remember {
                mutableStateOf(TimerOverlayManager.canDrawOverlays(applicationContext))
            }
            var accessibilityServiceConnected by remember {
                mutableStateOf(TimerOverlayManager.isAccessibilityServiceConnected())
            }
            var shouldRequestNotifications by remember { mutableStateOf(false) }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted) controller.syncNotification()
            }

            LaunchedEffect(shouldRequestNotifications) {
                if (shouldRequestNotifications) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    shouldRequestNotifications = false
                }
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> {
                            TimerRepository.setAppForeground(true)
                            TimerOverlayManager.setAppForeground(true)
                            overlayPermissionGranted = TimerOverlayManager.canDrawOverlays(applicationContext)
                            accessibilityServiceConnected = TimerOverlayManager.isAccessibilityServiceConnected()
                        }
                        Lifecycle.Event.ON_RESUME -> {
                            overlayPermissionGranted = TimerOverlayManager.canDrawOverlays(applicationContext)
                            accessibilityServiceConnected = TimerOverlayManager.isAccessibilityServiceConnected()
                        }
                        Lifecycle.Event.ON_STOP -> {
                            TimerRepository.setAppForeground(false)
                            TimerOverlayManager.setAppForeground(false)
                            if (TimerRepository.getState().timers.any { it.status == TimerStatus.Finished || it.status == TimerStatus.Overtime }) {
                                controller.syncNotification()
                            }
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            LaunchedEffect(launchTarget?.token) {
                val target = launchTarget ?: return@LaunchedEffect
                currentScreen = AppScreen.Timer
                controller.dispatch(TimerAction.SetActiveTimer(target.index))
                pendingTimerLaunchTarget.value = null
            }

            DisposableEffect(uiState.keepScreenAwakeEnabled) {
                if (uiState.keepScreenAwakeEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }

            val activeTimerStatus = uiState.activeTimer.status
            DisposableEffect(
                uiState.hideStatusBarEnabled,
                uiState.hideStatusBarOnlyWhenRunning,
                activeTimerStatus,
            ) {
                val shouldHideStatusBar = uiState.hideStatusBarEnabled &&
                    (
                        !uiState.hideStatusBarOnlyWhenRunning ||
                            activeTimerStatus == TimerStatus.Running ||
                            activeTimerStatus == TimerStatus.Overtime
                        )
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                if (shouldHideStatusBar) {
                    insetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    insetsController.hide(WindowInsetsCompat.Type.statusBars())
                } else {
                    insetsController.show(WindowInsetsCompat.Type.statusBars())
                }
                onDispose { insetsController.show(WindowInsetsCompat.Type.statusBars()) }
            }

            val systemDark = isSystemInDarkTheme()
            val isDark = when (uiState.themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.System -> systemDark
            }
            VisualTimerPlusTheme(isDark = isDark, oledBlackEnabled = uiState.isOledMode, customFontPath = uiState.customFontPath) {
                AnimatedContent(targetState = currentScreen, label = "screen") { screen ->
                    when (screen) {
                        AppScreen.Timer -> TimerScreen(
                            stateFlow = controller.uiState,
                            onAction = controller::dispatch,
                            onToggleOledMode = { enabled ->
                                controller.dispatch(com.fabiantorrestech.visualtimerplus.timer.TimerAction.SetOledMode(enabled))
                            },
                            onNotificationPermissionNeeded = {
                                if (
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        applicationContext,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    shouldRequestNotifications = true
                                }
                            },
                            onOpenLog = { currentScreen = AppScreen.TimerLog },
                            overlayPermissionGranted = overlayPermissionGranted,
                            onOpenOverlayPermissionSettings = {
                                startActivity(TimerOverlayManager.permissionSettingsIntent(applicationContext))
                            },
                            accessibilityServiceConnected = accessibilityServiceConnected,
                            onOpenAccessibilitySettings = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            },
                            db = db,
                            openPresetsOnLaunch = openPresetsOnLaunch,
                        )
                        AppScreen.TimerLog -> TimerLogScreen(
                            db = db,
                            onBack = { currentScreen = AppScreen.Timer },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingTimerLaunchTarget.value = extractTimerLaunchTarget(intent)
    }

    private fun extractTimerLaunchTarget(intent: android.content.Intent?): TimerLaunchTarget? {
        val index = intent?.getIntExtra(TimerNotificationManager.EXTRA_TARGET_TIMER_INDEX, -1) ?: -1
        return if (index >= 0) TimerLaunchTarget(index) else null
    }
}
