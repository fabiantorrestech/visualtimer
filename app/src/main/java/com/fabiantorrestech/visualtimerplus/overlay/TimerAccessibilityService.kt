package com.fabiantorrestech.visualtimerplus.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class TimerAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        TimerOverlayManager.onAccessibilityServiceConnected(this)
    }

    override fun onUnbind(intent: Intent): Boolean {
        TimerOverlayManager.onAccessibilityServiceDisconnected()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() {}
}
