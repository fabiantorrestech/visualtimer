package com.fabiantorrestech.visualtimerplus.timer

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.fabiantorrestech.visualtimerplus.overlay.TimerOverlayManager

enum class FinishedAlertPermission {
    Notifications,
    Overlay,
    FullScreenIntent,
    Accessibility,
}

data class FinishedAlertRequirements(
    val mode: FinishedAlertMode,
    val missingPermissions: List<FinishedAlertPermission>,
) {
    val isSatisfied: Boolean
        get() = missingPermissions.isEmpty()

    val shouldUseFinishedPage: Boolean
        get() = mode == FinishedAlertMode.TimerIsUpPage || !isSatisfied
}

object FinishedAlertRequirementResolver {

    fun resolve(
        context: Context,
        appState: AppState,
        accessibilityServiceConnected: Boolean = TimerOverlayManager.isAccessibilityServiceConnected(),
        overlayPermissionGranted: Boolean = TimerOverlayManager.canDrawOverlays(context),
    ): FinishedAlertRequirements {
        val missing = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(FinishedAlertPermission.Notifications)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val notificationManager = context.getSystemService(NotificationManager::class.java)
                if (!notificationManager.canUseFullScreenIntent()) {
                    add(FinishedAlertPermission.FullScreenIntent)
                }
            }
            if (appState.finishedAlertMode == FinishedAlertMode.NotificationAndOverlay) {
                if (!overlayPermissionGranted) {
                    add(FinishedAlertPermission.Overlay)
                }
                if (appState.overlayShowOnLockscreen && !accessibilityServiceConnected) {
                    add(FinishedAlertPermission.Accessibility)
                }
            }
        }
        return FinishedAlertRequirements(
            mode = appState.finishedAlertMode,
            missingPermissions = missing,
        )
    }

    fun settingsIntent(context: Context, permission: FinishedAlertPermission): Intent = when (permission) {
        FinishedAlertPermission.Notifications -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                }
            }
        }
        FinishedAlertPermission.Overlay -> TimerOverlayManager.permissionSettingsIntent(context)
        FinishedAlertPermission.FullScreenIntent -> Intent(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            android.net.Uri.parse("package:${context.packageName}"),
        )
        FinishedAlertPermission.Accessibility -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
