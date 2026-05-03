package com.fabiantorrestech.visualtimerplus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fabiantorrestech.visualtimerplus.timer.ThemeMode
import com.fabiantorrestech.visualtimerplus.timer.TimerController
import com.fabiantorrestech.visualtimerplus.timer.TimerRepository
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import com.fabiantorrestech.visualtimerplus.ui.screen.TimerScreen
import com.fabiantorrestech.visualtimerplus.ui.theme.VisualTimerPlusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TimerRepository.initialize(applicationContext)

        setContent {
            val controller = remember { TimerController(applicationContext) }
            val uiState by controller.uiState.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current
            var shouldRequestNotifications by remember { mutableStateOf(false) }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted) {
                    controller.syncNotification()
                }
            }

            LaunchedEffect(shouldRequestNotifications) {
                if (shouldRequestNotifications) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    shouldRequestNotifications = false
                }
            }

            DisposableEffect(lifecycleOwner, uiState.status) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME && uiState.status == TimerStatus.Finished) {
                        controller.dismissFinishedIfActive()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)

                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            DisposableEffect(uiState.keepScreenAwakeEnabled) {
                if (uiState.keepScreenAwakeEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }

                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            DisposableEffect(
                uiState.hideStatusBarEnabled,
                uiState.hideStatusBarOnlyWhenRunning,
                uiState.status,
            ) {
                val shouldHideStatusBar = uiState.hideStatusBarEnabled &&
                    (!uiState.hideStatusBarOnlyWhenRunning || uiState.status == TimerStatus.Running)
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)

                if (shouldHideStatusBar) {
                    insetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    insetsController.hide(WindowInsetsCompat.Type.statusBars())
                } else {
                    insetsController.show(WindowInsetsCompat.Type.statusBars())
                }

                onDispose {
                    insetsController.show(WindowInsetsCompat.Type.statusBars())
                }
            }

            val systemDark = isSystemInDarkTheme()
            val isDark = when (uiState.themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.System -> systemDark
            }
            VisualTimerPlusTheme(isDark = isDark, oledBlackEnabled = uiState.isOledMode) {
                TimerScreen(
                    stateFlow = controller.uiState,
                    onAction = controller::dispatch,
                    onToggleOledMode = controller::setOledMode,
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
                )
            }
        }
    }
}
