package com.fabiantorrestech.visualtimerplus.overlay

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.fabiantorrestech.visualtimerplus.MainActivity
import com.fabiantorrestech.visualtimerplus.notification.TimerNotificationManager
import com.fabiantorrestech.visualtimerplus.timer.AppState
import com.fabiantorrestech.visualtimerplus.timer.ONE_HOUR_MILLIS
import com.fabiantorrestech.visualtimerplus.timer.OverlaySize
import com.fabiantorrestech.visualtimerplus.timer.OverlayStyle
import com.fabiantorrestech.visualtimerplus.timer.TimerInstance
import com.fabiantorrestech.visualtimerplus.timer.TimerRepository
import com.fabiantorrestech.visualtimerplus.timer.TimerService
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import com.fabiantorrestech.visualtimerplus.util.formatClockTime
import com.fabiantorrestech.visualtimerplus.util.formatEndTimeFromNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object TimerOverlayManager {
    private lateinit var appContext: Context
    private lateinit var windowManager: WindowManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var initialized = false
    private var isAppForeground = false
    private var latestState: AppState = AppState()

    private var accessibilityWindowManager: WindowManager? = null
    private var activeWindowManager: WindowManager? = null
    private var activeWindowType: Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    private var overlayContainer: LinearLayout? = null
    private var overlayView: OverlayTimerView? = null
    private var overlayPanel: OverlayPanelView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var touchListener: OverlayTouchListener? = null
    private var touchListenerTimerIndex: Int = -1
    private var imeBottomInset = 0
    private var imeRestorePosition: OverlayPosition? = null
    private var overlayAutoMovedForIme = false
    private var overlayMovedByUserWhileImeVisible = false

    private var isPanelOpen: Boolean = false
    private var isSnappedLeft: Boolean = false
    private var panelFocusedTimerIndex: Int = 0
    private val longPressHandler = Handler(Looper.getMainLooper())

    private const val PREFS_NAME = "visual_timer_prefs"
    private const val KEY_OVERLAY_SNAPPED_LEFT = "overlay_pos_left_edge"
    private const val KEY_OVERLAY_POS_Y = "overlay_pos_y"
    private const val OVERLAY_POS_UNSET = -1
    private const val PANEL_WIDTH_DP = 176
    private const val LONG_PRESS_DURATION_MS = 500L
    private const val IME_CLEARANCE_DP = 16

    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        windowManager = appContext.getSystemService(WindowManager::class.java)
        isAppForeground = TimerRepository.isAppForeground
        latestState = TimerRepository.getState()
        scope.launch {
            TimerRepository.state.collectLatest { state ->
                latestState = state
                refreshOverlay()
            }
        }
        initialized = true
        refreshOverlay()
    }

    fun setAppForeground(foreground: Boolean) {
        isAppForeground = foreground
        if (foreground && isPanelOpen) closePanel()
        refreshOverlay()
    }

    fun canDrawOverlays(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun permissionSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun onAccessibilityServiceConnected(service: AccessibilityService) {
        accessibilityWindowManager = service.getSystemService(WindowManager::class.java)
        if (latestState.overlayShowOnLockscreen && overlayContainer != null) removeOverlay()
        refreshOverlay()
    }

    fun onAccessibilityServiceDisconnected() {
        if (overlayContainer != null && activeWindowManager === accessibilityWindowManager) removeOverlay()
        accessibilityWindowManager = null
        refreshOverlay()
    }

    fun isAccessibilityServiceConnected(): Boolean = accessibilityWindowManager != null

    private fun refreshOverlay() {
        if (!initialized) return
        val timerIndex = latestState.overlayTimerIndex
        val timer = timerIndex?.let { latestState.timers[it] }
        val shouldShow = !isAppForeground &&
            latestState.overlayEnabled &&
            canDrawOverlays(appContext) &&
            timer != null &&
            timer.status in setOf(TimerStatus.Running, TimerStatus.Overtime, TimerStatus.Paused)

        if (!shouldShow) {
            removeOverlay()
            return
        }

        ensureOverlayView()
        updateOverlay(timerIndex!!, timer!!)
    }

    private fun ensureOverlayView() {
        val useAccessibility = accessibilityWindowManager != null && latestState.overlayShowOnLockscreen
        val desiredType = if (useAccessibility)
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        if (overlayContainer != null && layoutParams != null) {
            if (activeWindowType == desiredType) return
            removeOverlay()
        }

        val effectiveWm = if (useAccessibility) accessibilityWindowManager!! else windowManager
        activeWindowManager = effectiveWm
        activeWindowType = desiredType

        val sizePx = sizePx(latestState.overlaySize)
        val overlay = OverlayTimerView(appContext)
        val container = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        container.addView(overlay, LinearLayout.LayoutParams(sizePx, sizePx))

        ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
            onWindowInsetsChanged(insets)
            insets
        }

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        val lockscreenFlag = if (latestState.overlayShowOnLockscreen)
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED else 0
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedY = prefs.getInt(KEY_OVERLAY_POS_Y, OVERLAY_POS_UNSET)
        val snappedLeft = prefs.getBoolean(KEY_OVERLAY_SNAPPED_LEFT, false)
        isSnappedLeft = snappedLeft

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            desiredType,
            baseFlags or lockscreenFlag,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val metrics = appContext.resources.displayMetrics
            val maxX = max(0, metrics.widthPixels - sizePx)
            if (savedY != OVERLAY_POS_UNSET) {
                x = if (snappedLeft) 0 else maxX
                y = savedY.coerceIn(0, max(0, metrics.heightPixels - sizePx))
            } else {
                x = maxX - dp(24)
                y = max(dp(32), metrics.heightPixels / 3)
            }
        }

        effectiveWm.addView(container, params)
        overlayContainer = container
        overlayView = overlay
        layoutParams = params
        ViewCompat.requestApplyInsets(container)
    }

    private fun updateOverlay(timerIndex: Int, timer: TimerInstance) {
        val params = layoutParams ?: return
        val view = overlayView ?: return

        if (touchListener == null || touchListenerTimerIndex != timerIndex) {
            touchListener = OverlayTouchListener(timerIndex)
            touchListenerTimerIndex = timerIndex
            view.setOnTouchListener(touchListener)
        }

        val sizePx = sizePx(latestState.overlaySize)
        val panelPx = dp(PANEL_WIDTH_DP)
        val targetWidth = if (isPanelOpen) sizePx + panelPx else sizePx
        if (params.width != targetWidth || params.height != sizePx) {
            params.width = targetWidth
            params.height = sizePx
            val timerLp = view.layoutParams as? LinearLayout.LayoutParams
            if (timerLp == null || timerLp.width != sizePx) {
                view.layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            }
        }

        val lockscreenEnabled = latestState.overlayShowOnLockscreen
        val hasLockscreenFlag = (params.flags and WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED) != 0
        if (hasLockscreenFlag != lockscreenEnabled) {
            params.flags = if (lockscreenEnabled) {
                params.flags or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            } else {
                params.flags and WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED.inv()
            }
        }

        val displayTimer = if (isPanelOpen) {
            latestState.timers.getOrNull(panelFocusedTimerIndex) ?: timer
        } else {
            timer
        }

        view.render(
            timer = displayTimer,
            style = latestState.overlayStyle,
            overlaySize = latestState.overlaySize,
        )

        if (isPanelOpen) updatePanelContent()

        clampPosition(params)
        applyImeAvoidanceIfNeeded(params)
        updateOverlayLayout(params)
    }

    private fun removeOverlay() {
        val container = overlayContainer ?: return
        val wm = activeWindowManager ?: windowManager
        try {
            wm.removeView(container)
        } catch (_: IllegalArgumentException) {
        }
        overlayContainer = null
        overlayView = null
        overlayPanel = null
        isPanelOpen = false
        layoutParams = null
        activeWindowManager = null
        touchListener = null
        touchListenerTimerIndex = -1
        longPressHandler.removeCallbacksAndMessages(null)
        clearImeTracking()
    }

    private fun clampPosition(params: WindowManager.LayoutParams) {
        val metrics = appContext.resources.displayMetrics
        val maxX = max(0, metrics.widthPixels - params.width)
        val maxY = max(0, metrics.heightPixels - params.height)
        params.x = min(max(params.x, 0), maxX)
        params.y = min(max(params.y, 0), maxY)
    }

    private fun snapToEdge() {
        val params = layoutParams ?: return
        val metrics = appContext.resources.displayMetrics
        val maxX = max(0, metrics.widthPixels - params.width)
        val snapLeft = params.x + (params.width / 2) < metrics.widthPixels / 2
        params.x = if (snapLeft) 0 else maxX
        clampPosition(params)
        applyImeAvoidanceIfNeeded(params)

        if (isPanelOpen && snapLeft != isSnappedLeft) {
            updatePanelSideInContainer(snapLeft)
        }
        isSnappedLeft = snapLeft

        updateOverlayLayout(params)
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_OVERLAY_SNAPPED_LEFT, snapLeft)
            .putInt(KEY_OVERLAY_POS_Y, params.y)
            .apply()
    }

    private fun togglePanel(timerIndex: Int) {
        if (isPanelOpen) closePanel() else openPanel(timerIndex)
    }

    private fun openPanel(timerIndex: Int) {
        if (isPanelOpen) return
        val container = overlayContainer ?: return
        val params = layoutParams ?: return

        panelFocusedTimerIndex = timerIndex

        val sizePx = sizePx(latestState.overlaySize)
        val panelPx = dp(PANEL_WIDTH_DP)

        val panel = OverlayPanelView(appContext, isSnappedLeft)
        overlayPanel = panel
        isPanelOpen = true

        val panelLp = LinearLayout.LayoutParams(panelPx, sizePx)
        if (isSnappedLeft) {
            container.addView(panel, panelLp)
        } else {
            container.addView(panel, 0, panelLp)
            params.x = (params.x - panelPx).coerceAtLeast(0)
        }

        params.width = sizePx + panelPx

        updatePanelContent()
        clampPosition(params)
        updateOverlayLayout(params)
    }

    private fun closePanel() {
        if (!isPanelOpen) return
        val container = overlayContainer ?: return
        val panel = overlayPanel ?: return
        val params = layoutParams ?: return

        val sizePx = sizePx(latestState.overlaySize)

        container.removeView(panel)
        overlayPanel = null
        isPanelOpen = false

        params.width = sizePx
        if (!isSnappedLeft) {
            val metrics = appContext.resources.displayMetrics
            params.x = max(0, metrics.widthPixels - sizePx)
        }

        clampPosition(params)
        updateOverlayLayout(params)
    }

    private fun updatePanelSideInContainer(newSnappedLeft: Boolean) {
        val container = overlayContainer ?: return
        val view = overlayView ?: return
        val panel = overlayPanel ?: return

        val sizePx = sizePx(latestState.overlaySize)
        val panelPx = dp(PANEL_WIDTH_DP)

        container.removeAllViews()
        if (newSnappedLeft) {
            container.addView(view, LinearLayout.LayoutParams(sizePx, sizePx))
            container.addView(panel, LinearLayout.LayoutParams(panelPx, sizePx))
        } else {
            container.addView(panel, LinearLayout.LayoutParams(panelPx, sizePx))
            container.addView(view, LinearLayout.LayoutParams(sizePx, sizePx))
        }
        panel.isPanelOnRight = newSnappedLeft
        panel.invalidate()
    }

    private fun updatePanelContent() {
        val panel = overlayPanel ?: return
        val state = latestState
        val activeTimers = state.timers.withIndex()
            .filter { it.value.status in setOf(TimerStatus.Running, TimerStatus.Paused, TimerStatus.Overtime) }
            .toList()

        if (activeTimers.none { it.index == panelFocusedTimerIndex } && activeTimers.isNotEmpty()) {
            panelFocusedTimerIndex = activeTimers.first().index
        }

        val timer = state.timers.getOrNull(panelFocusedTimerIndex) ?: return
        panel.updateContent(timer, panelFocusedTimerIndex, activeTimers)
    }

    private fun onLongPress(timerIndex: Int) {
        val timer = latestState.timers.getOrNull(timerIndex) ?: return
        val view = overlayView ?: return
        when (timer.status) {
            TimerStatus.Running -> {
                sendServiceAction(TimerService.ACTION_PAUSE, timerIndex, foreground = false)
                view.showStatusIndicator(isPaused = true)
            }
            TimerStatus.Paused -> {
                sendServiceAction(TimerService.ACTION_RESUME, timerIndex, foreground = true)
                view.showStatusIndicator(isPaused = false)
            }
            else -> {}
        }
    }

    private fun onPanelAction(action: PanelAction) {
        val state = latestState
        val activeTimers = state.timers.withIndex()
            .filter { it.value.status in setOf(TimerStatus.Running, TimerStatus.Paused, TimerStatus.Overtime) }
            .toList()

        when (action) {
            PanelAction.PrevTimer -> {
                if (activeTimers.size <= 1) return
                val pos = activeTimers.indexOfFirst { it.index == panelFocusedTimerIndex }
                panelFocusedTimerIndex = if (pos > 0) activeTimers[pos - 1].index else activeTimers.last().index
                val t = state.timers.getOrNull(panelFocusedTimerIndex)
                if (t != null) overlayView?.render(t, state.overlayStyle, state.overlaySize)
                updatePanelContent()
            }
            PanelAction.NextTimer -> {
                if (activeTimers.size <= 1) return
                val pos = activeTimers.indexOfFirst { it.index == panelFocusedTimerIndex }
                panelFocusedTimerIndex = if (pos >= 0 && pos < activeTimers.size - 1) {
                    activeTimers[pos + 1].index
                } else {
                    activeTimers.first().index
                }
                val t = state.timers.getOrNull(panelFocusedTimerIndex)
                if (t != null) overlayView?.render(t, state.overlayStyle, state.overlaySize)
                updatePanelContent()
            }
            PanelAction.TogglePauseResume -> {
                val timer = state.timers.getOrNull(panelFocusedTimerIndex) ?: return
                when (timer.status) {
                    TimerStatus.Running -> sendServiceAction(TimerService.ACTION_PAUSE, panelFocusedTimerIndex, false)
                    TimerStatus.Paused -> sendServiceAction(TimerService.ACTION_RESUME, panelFocusedTimerIndex, true)
                    else -> {}
                }
            }
            PanelAction.ConfirmReset -> {
                sendServiceAction(TimerService.ACTION_RESET, panelFocusedTimerIndex, false)
            }
            PanelAction.OpenApp -> {
                openAppToTimer(panelFocusedTimerIndex)
            }
        }
    }

    private fun sendServiceAction(action: String, timerIndex: Int, foreground: Boolean) {
        val intent = Intent(appContext, TimerService::class.java)
            .setAction(action)
            .putExtra(TimerService.EXTRA_TIMER_INDEX, timerIndex)
        if (foreground) appContext.startForegroundService(intent) else appContext.startService(intent)
    }

    private fun onWindowInsetsChanged(insets: WindowInsetsCompat) {
        val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
        val nextImeBottomInset = if (imeVisible) {
            insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        } else {
            0
        }
        if (!imeVisible) {
            restoreAfterImeDismissIfNeeded()
            imeBottomInset = 0
            clearImeTracking()
            return
        }

        imeBottomInset = nextImeBottomInset
        val params = layoutParams ?: return
        applyImeAvoidanceIfNeeded(params)
        updateOverlayLayout(params)
    }

    private fun applyImeAvoidanceIfNeeded(params: WindowManager.LayoutParams) {
        if (imeBottomInset <= 0) return
        val keyboardSafeMaxY = keyboardSafeMaxY(params)
        if (params.y <= keyboardSafeMaxY) return
        if (!overlayAutoMovedForIme && !overlayMovedByUserWhileImeVisible) {
            imeRestorePosition = OverlayPosition(params.x, params.y)
            overlayAutoMovedForIme = true
        }
        params.y = keyboardSafeMaxY
    }

    private fun keyboardSafeMaxY(params: WindowManager.LayoutParams): Int {
        val metrics = appContext.resources.displayMetrics
        val keyboardTop = metrics.heightPixels - imeBottomInset
        val gap = dp(IME_CLEARANCE_DP)
        return max(0, keyboardTop - params.height - gap)
    }

    private fun restoreAfterImeDismissIfNeeded() {
        if (!overlayAutoMovedForIme || overlayMovedByUserWhileImeVisible) return
        val params = layoutParams ?: return
        val savedPosition = imeRestorePosition ?: return
        params.x = savedPosition.x
        params.y = savedPosition.y
        clampPosition(params)
        updateOverlayLayout(params)
    }

    private fun clearImeTracking() {
        imeRestorePosition = null
        overlayAutoMovedForIme = false
        overlayMovedByUserWhileImeVisible = false
    }

    private fun updateOverlayLayout(params: WindowManager.LayoutParams) {
        val container = overlayContainer ?: return
        val wm = activeWindowManager ?: windowManager
        try {
            wm.updateViewLayout(container, params)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun openAppToTimer(timerIndex: Int) {
        val intent = Intent(appContext, MainActivity::class.java)
            .putExtra(TimerNotificationManager.EXTRA_TARGET_TIMER_INDEX, timerIndex)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        appContext.startActivity(intent)
    }

    private fun sizePx(size: OverlaySize): Int = when (size) {
        OverlaySize.Small -> dp(72)
        OverlaySize.Medium -> dp(96)
        OverlaySize.Large -> dp(120)
    }

    private fun dp(value: Int): Int =
        (value * appContext.resources.displayMetrics.density).toInt()

    private fun markOverlayMovedByUserDuringIme() {
        if (imeBottomInset > 0) overlayMovedByUserWhileImeVisible = true
    }

    private enum class PanelAction { PrevTimer, NextTimer, TogglePauseResume, ConfirmReset, OpenApp }

    private data class OverlayPosition(val x: Int, val y: Int)

    // ── Touch Listener ───────────────────────────────────────────────────────────

    private class OverlayTouchListener(private val timerIndex: Int) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var lastTouchX = 0f
        private var lastTouchY = 0f
        private var dragStarted = false
        private var longPressTriggered = false

        private val longPressRunnable = Runnable {
            longPressTriggered = true
            TimerOverlayManager.onLongPress(timerIndex)
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val params = TimerOverlayManager.layoutParams ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    dragStarted = false
                    longPressTriggered = false
                    TimerOverlayManager.longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_DURATION_MS)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastTouchX
                    val dy = event.rawY - lastTouchY
                    params.x += dx.toInt()
                    params.y += dy.toInt()
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    TimerOverlayManager.clampPosition(params)
                    val totalDeltaX = params.x - initialX
                    val totalDeltaY = params.y - initialY
                    if (!dragStarted && hasExceededTouchSlop(totalDeltaX.toFloat(), totalDeltaY.toFloat())) {
                        dragStarted = true
                        TimerOverlayManager.longPressHandler.removeCallbacks(longPressRunnable)
                        TimerOverlayManager.markOverlayMovedByUserDuringIme()
                    }
                    TimerOverlayManager.applyImeAvoidanceIfNeeded(params)
                    TimerOverlayManager.updateOverlayLayout(params)
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    TimerOverlayManager.longPressHandler.removeCallbacks(longPressRunnable)
                    if (!dragStarted && !longPressTriggered) {
                        restorePosition(params)
                        TimerOverlayManager.togglePanel(timerIndex)
                    } else if (dragStarted) {
                        TimerOverlayManager.snapToEdge()
                    }
                    dragStarted = false
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    TimerOverlayManager.longPressHandler.removeCallbacks(longPressRunnable)
                    restorePosition(params)
                    dragStarted = false
                    return true
                }
            }
            return false
        }

        private fun restorePosition(params: WindowManager.LayoutParams) {
            params.x = initialX
            params.y = initialY
            TimerOverlayManager.clampPosition(params)
            TimerOverlayManager.applyImeAvoidanceIfNeeded(params)
            TimerOverlayManager.updateOverlayLayout(params)
        }

        private fun hasExceededTouchSlop(deltaX: Float, deltaY: Float): Boolean =
            abs(deltaX) > TimerOverlayManager.dp(DRAG_TOUCH_SLOP_DP) ||
                abs(deltaY) > TimerOverlayManager.dp(DRAG_TOUCH_SLOP_DP)

        private companion object {
            const val DRAG_TOUCH_SLOP_DP = 12
        }
    }

    // ── Overlay Timer View ───────────────────────────────────────────────────────

    private class OverlayTimerView(context: Context) : View(context) {
        private val ringTrackColor = Color.parseColor("#33111111")
        private val timerTrackColor = Color.parseColor("#CC000000")
        private val ringRed = Color.parseColor("#EF4444")
        private val timerRedDark = Color.parseColor("#7F1D1D")
        private val pausedColor = Color.parseColor("#D4D4D8")
        private val overtimeColor = Color.parseColor("#DC2626")
        private val outlineColor = Color.parseColor("#33FFFFFF")

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        private val arcBounds = RectF()
        private val indicatorPath = Path()

        private var timer: TimerInstance = TimerInstance(id = 0)
        private var style: OverlayStyle = OverlayStyle.Ring
        private var overlaySize: OverlaySize = OverlaySize.Medium

        private var indicatorAlpha: Float = 0f
        private var indicatorIsPause: Boolean = true
        private var indicatorAnimator: ValueAnimator? = null

        fun render(timer: TimerInstance, style: OverlayStyle, overlaySize: OverlaySize) {
            if (indicatorIsPause && indicatorAlpha > 0f && timer.status == TimerStatus.Running) {
                clearStatusIndicator()
            }
            this.timer = timer
            this.style = style
            this.overlaySize = overlaySize
            invalidate()
        }

        fun showStatusIndicator(isPaused: Boolean) {
            indicatorAnimator?.cancel()
            indicatorIsPause = isPaused
            indicatorAlpha = 1f
            if (!isPaused) {
                indicatorAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                    startDelay = 1000L
                    duration = 800L
                    addUpdateListener {
                        indicatorAlpha = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            }
            invalidate()
        }

        private fun clearStatusIndicator() {
            indicatorAnimator?.cancel()
            indicatorAlpha = 0f
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val diameter = min(width, height).toFloat()
            if (diameter <= 0f) return

            val centerX = width / 2f
            val centerY = height / 2f
            val radius = diameter / 2f
            val strokeWidth = diameter * 0.115f
            val inset = strokeWidth / 2f + diameter * 0.02f
            arcBounds.set(inset, inset, width - inset, height - inset)

            val alphaFactor = overtimeAlpha(timer.status)
            val progress = progressSpec(timer)

            when (style) {
                OverlayStyle.Ring -> drawRingStyle(canvas, centerX, centerY, radius, strokeWidth, alphaFactor, progress)
                OverlayStyle.TimerFace -> drawTimerFaceStyle(canvas, centerX, centerY, radius, alphaFactor, progress)
            }

            drawCenteredText(canvas, centerX, centerY, diameter)

            if (indicatorAlpha > 0f) drawStatusIndicator(canvas, centerX, centerY, diameter)

            if (timer.status == TimerStatus.Overtime) postInvalidateOnAnimation()
        }

        private fun drawRingStyle(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            radius: Float,
            strokeWidth: Float,
            alphaFactor: Float,
            progress: OverlayProgressSpec,
        ) {
            fillPaint.color = timerTrackColor
            canvas.drawCircle(centerX, centerY, radius - strokeWidth * 0.35f, fillPaint)

            ringPaint.strokeWidth = strokeWidth
            ringPaint.color = ringTrackColor
            canvas.drawArc(arcBounds, 0f, 360f, false, ringPaint)

            ringPaint.color = accentColor().withAlpha(alphaFactor)
            if (timer.status == TimerStatus.Overtime) {
                canvas.drawArc(arcBounds, -90f, 359.9f, false, ringPaint)
            } else if (progress.primaryFraction > 0f) {
                canvas.drawArc(arcBounds, -90f, progress.sweepSign * 360f * progress.primaryFraction, false, ringPaint)
            }

            outlinePaint.strokeWidth = max(1f, diameterPx() * 0.012f)
            outlinePaint.color = outlineColor
            canvas.drawCircle(centerX, centerY, radius - outlinePaint.strokeWidth / 2f, outlinePaint)
        }

        private fun drawTimerFaceStyle(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            radius: Float,
            alphaFactor: Float,
            progress: OverlayProgressSpec,
        ) {
            fillPaint.color = timerTrackColor
            canvas.drawCircle(centerX, centerY, radius, fillPaint)

            if (timer.status == TimerStatus.Overtime) {
                fillPaint.color = overtimeColor.withAlpha(alphaFactor)
                canvas.drawCircle(centerX, centerY, radius, fillPaint)
            } else if (progress.showTwoLayer) {
                fillPaint.color = timerRedDark
                canvas.drawCircle(centerX, centerY, radius, fillPaint)
                if (progress.overlayFraction > 0f) {
                    fillPaint.color = accentColor()
                    canvas.drawArc(arcBounds, -90f, progress.sweepSign * 360f * progress.overlayFraction, true, fillPaint)
                }
            } else if (progress.primaryFraction > 0f) {
                fillPaint.color = accentColor()
                canvas.drawArc(arcBounds, -90f, progress.sweepSign * 360f * progress.primaryFraction, true, fillPaint)
            }

            outlinePaint.strokeWidth = max(1f, diameterPx() * 0.012f)
            outlinePaint.color = outlineColor
            canvas.drawCircle(centerX, centerY, radius - outlinePaint.strokeWidth / 2f, outlinePaint)
        }

        private fun drawCenteredText(canvas: Canvas, centerX: Float, centerY: Float, diameter: Float) {
            val sizeFactor = when (overlaySize) {
                OverlaySize.Small -> 0.21f
                OverlaySize.Medium -> 0.22f
                OverlaySize.Large -> 0.23f
            }
            textPaint.textSize = diameter * sizeFactor
            textPaint.color = Color.WHITE
            val text = if (timer.status == TimerStatus.Overtime) {
                "+${timer.displayMillis.formatClockTime()}"
            } else {
                timer.displayMillis.formatClockTime()
            }
            val baseline = centerY - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(text, centerX, baseline, textPaint)
        }

        private fun drawStatusIndicator(canvas: Canvas, cx: Float, cy: Float, diameter: Float) {
            val alpha = (indicatorAlpha * 255).toInt().coerceIn(0, 255)
            val size = diameter * 0.4f
            fillPaint.color = Color.argb(alpha, 255, 255, 255)
            if (indicatorIsPause) {
                val bw = size * 0.22f
                val bh = size * 0.65f
                val gap = size * 0.20f
                canvas.drawRoundRect(cx - gap / 2f - bw, cy - bh / 2f, cx - gap / 2f, cy + bh / 2f, bw * 0.2f, bw * 0.2f, fillPaint)
                canvas.drawRoundRect(cx + gap / 2f, cy - bh / 2f, cx + gap / 2f + bw, cy + bh / 2f, bw * 0.2f, bw * 0.2f, fillPaint)
            } else {
                val h = size * 0.65f
                val w = h * 0.9f
                indicatorPath.rewind()
                indicatorPath.moveTo(cx - w * 0.3f, cy - h / 2f)
                indicatorPath.lineTo(cx + w * 0.7f, cy)
                indicatorPath.lineTo(cx - w * 0.3f, cy + h / 2f)
                indicatorPath.close()
                canvas.drawPath(indicatorPath, fillPaint)
            }
        }

        private fun progressSpec(timer: TimerInstance): OverlayProgressSpec {
            if (timer.status == TimerStatus.Overtime) {
                return OverlayProgressSpec(1f, 0f, false, if (timer.settings.clockwiseModeEnabled) 1f else -1f)
            }
            val displayMs = timer.displayMillis
            val useFullClock = timer.settings.fullClockMode && timer.status != TimerStatus.Idle
            val sweepSign = if (timer.settings.clockwiseModeEnabled) 1f else -1f
            if (useFullClock) {
                val total = timer.selectedDurationMillis.coerceAtLeast(1L)
                return OverlayProgressSpec((displayMs.toFloat() / total.toFloat()).coerceIn(0f, 1f), 0f, false, sweepSign)
            }
            return when {
                displayMs >= ONE_HOUR_MILLIS * 2L -> OverlayProgressSpec(1f, 1f, true, sweepSign)
                displayMs > ONE_HOUR_MILLIS -> OverlayProgressSpec(
                    1f,
                    ((displayMs - ONE_HOUR_MILLIS).toFloat() / ONE_HOUR_MILLIS).coerceIn(0f, 1f),
                    true,
                    sweepSign,
                )
                else -> OverlayProgressSpec((displayMs.toFloat() / ONE_HOUR_MILLIS).coerceIn(0f, 1f), 0f, false, sweepSign)
            }
        }

        private fun overtimeAlpha(status: TimerStatus): Float {
            if (status != TimerStatus.Overtime) return 1f
            val phase = (SystemClock.uptimeMillis() % 1200L) / 1200f
            return 0.35f + (0.65f * (1f - abs((phase * 2f) - 1f)))
        }

        private fun accentColor(): Int = when (timer.status) {
            TimerStatus.Paused -> pausedColor
            TimerStatus.Overtime -> overtimeColor
            else -> ringRed
        }

        private fun Int.withAlpha(alphaFactor: Float): Int {
            val clamped = alphaFactor.coerceIn(0f, 1f)
            return Color.argb(
                (Color.alpha(this) * clamped).toInt(),
                Color.red(this),
                Color.green(this),
                Color.blue(this),
            )
        }

        private fun diameterPx(): Float = min(width, height).toFloat()
    }

    // ── Overlay Panel View ───────────────────────────────────────────────────────

    private class OverlayPanelView(context: Context, var isPanelOnRight: Boolean) : View(context) {
        private val density = context.resources.displayMetrics.density

        private val bgColor = Color.parseColor("#CC000000")
        private val orangeColor = Color.parseColor("#F97316")
        private val dimColor = Color.parseColor("#55FFFFFF")
        private val confirmColor = Color.parseColor("#22C55E")
        private val destructColor = Color.parseColor("#EF4444")

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        private val bgPath = Path()

        private val leftArrowRect = RectF()
        private val rightArrowRect = RectF()
        private val pauseResumeRect = RectF()
        private val resetRect = RectF()
        private val openAppRect = RectF()
        private val confirmRect = RectF()
        private val cancelRect = RectF()

        var timerName: String = ""
        var endTimeText: String = ""
        var isPaused: Boolean = false
        var isOvertime: Boolean = false
        var pageText: String = "1 / 1"
        var hasMultipleTimers: Boolean = false
        var isConfirmingReset: Boolean = false

        private val resetConfirmHandler = Handler(Looper.getMainLooper())
        private val resetTimeoutRunnable = Runnable {
            isConfirmingReset = false
            invalidate()
        }

        fun updateContent(
            timer: TimerInstance,
            timerIndex: Int,
            activeTimers: List<IndexedValue<TimerInstance>>,
        ) {
            timerName = timer.activeTimerName.ifBlank { "Timer ${timerIndex + 1}" }
            endTimeText = if (timer.status == TimerStatus.Overtime) {
                "+${timer.displayMillis.formatClockTime()}"
            } else {
                formatEndTimeFromNow(timer.displayMillis, timer.settings.showEndTimeSecondsEnabled)
            }
            isPaused = timer.status == TimerStatus.Paused
            isOvertime = timer.status == TimerStatus.Overtime
            val pos = (activeTimers.indexOfFirst { it.index == timerIndex } + 1).coerceAtLeast(1)
            pageText = "$pos / ${activeTimers.size}"
            hasMultipleTimers = activeTimers.size > 1
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            drawBackground(canvas, w, h)

            val pad = w * 0.08f
            val sec1H = h * 0.36f
            val sec2H = h * 0.32f

            // Timer name
            val nameSize = h * 0.125f
            textPaint.textSize = nameSize
            textPaint.color = Color.WHITE
            val name = truncate(timerName, textPaint, w - pad * 2f)
            canvas.drawText(name, w / 2f, sec1H * 0.38f + nameSize * 0.35f, textPaint)

            // End time
            val endSize = h * 0.105f
            textPaint.textSize = endSize
            textPaint.color = orangeColor
            canvas.drawText(endTimeText, w / 2f, sec1H * 0.72f + endSize * 0.35f, textPaint)

            // Nav arrows + page count
            val navMidY = sec1H + sec2H / 2f
            val arrowSize = sec2H * 0.50f
            val arrowInset = w * 0.10f
            textPaint.textSize = arrowSize
            textPaint.color = if (hasMultipleTimers) Color.WHITE else dimColor
            canvas.drawText("◀", arrowInset + arrowSize / 2f, navMidY + arrowSize * 0.35f, textPaint)
            canvas.drawText("▶", w - arrowInset - arrowSize / 2f, navMidY + arrowSize * 0.35f, textPaint)

            textPaint.textSize = h * 0.095f
            textPaint.color = Color.WHITE
            canvas.drawText(pageText, w / 2f, navMidY + h * 0.045f, textPaint)

            leftArrowRect.set(0f, sec1H, w * 0.33f, sec1H + sec2H)
            rightArrowRect.set(w * 0.67f, sec1H, w, sec1H + sec2H)

            // Controls
            val ctrlTop = sec1H + sec2H
            val ctrlH = h - ctrlTop
            val iconSize = ctrlH * 0.52f
            val iconY = ctrlTop + ctrlH * 0.5f + iconSize * 0.35f
            val thirdW = w / 3f

            if (isConfirmingReset) {
                textPaint.textSize = iconSize
                textPaint.color = confirmColor
                canvas.drawText("✓", thirdW * 0.5f, iconY, textPaint)
                textPaint.color = destructColor
                canvas.drawText("✕", thirdW * 2.5f, iconY, textPaint)
                confirmRect.set(0f, ctrlTop, w * 0.5f, h)
                cancelRect.set(w * 0.5f, ctrlTop, w, h)
                pauseResumeRect.setEmpty()
                resetRect.setEmpty()
                openAppRect.setEmpty()
            } else {
                textPaint.textSize = iconSize
                textPaint.color = Color.WHITE
                canvas.drawText(if (isPaused || isOvertime) "▶" else "⏸", thirdW * 0.5f, iconY, textPaint)
                canvas.drawText("↺", thirdW * 1.5f, iconY, textPaint)
                canvas.drawText("↗", thirdW * 2.5f, iconY, textPaint)
                pauseResumeRect.set(0f, ctrlTop, thirdW, h)
                resetRect.set(thirdW, ctrlTop, thirdW * 2f, h)
                openAppRect.set(thirdW * 2f, ctrlTop, w, h)
                confirmRect.setEmpty()
                cancelRect.setEmpty()
            }
        }

        private fun drawBackground(canvas: Canvas, w: Float, h: Float) {
            val r = 10f * density
            bgPath.rewind()
            if (isPanelOnRight) {
                bgPath.moveTo(0f, 0f)
                bgPath.lineTo(w - r, 0f)
                bgPath.arcTo(RectF(w - 2f * r, 0f, w, 2f * r), -90f, 90f, false)
                bgPath.lineTo(w, h - r)
                bgPath.arcTo(RectF(w - 2f * r, h - 2f * r, w, h), 0f, 90f, false)
                bgPath.lineTo(0f, h)
            } else {
                bgPath.moveTo(r, 0f)
                bgPath.lineTo(w, 0f)
                bgPath.lineTo(w, h)
                bgPath.lineTo(r, h)
                bgPath.arcTo(RectF(0f, h - 2f * r, 2f * r, h), 90f, 90f, false)
                bgPath.lineTo(0f, r)
                bgPath.arcTo(RectF(0f, 0f, 2f * r, 2f * r), 180f, 90f, false)
            }
            bgPath.close()
            fillPaint.color = bgColor
            canvas.drawPath(bgPath, fillPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> return true
                MotionEvent.ACTION_UP -> {
                    val x = event.x
                    val y = event.y
                    when {
                        !confirmRect.isEmpty && confirmRect.contains(x, y) -> {
                            resetConfirmHandler.removeCallbacks(resetTimeoutRunnable)
                            isConfirmingReset = false
                            invalidate()
                            TimerOverlayManager.onPanelAction(PanelAction.ConfirmReset)
                        }
                        !cancelRect.isEmpty && cancelRect.contains(x, y) -> {
                            resetConfirmHandler.removeCallbacks(resetTimeoutRunnable)
                            isConfirmingReset = false
                            invalidate()
                        }
                        !leftArrowRect.isEmpty && leftArrowRect.contains(x, y) ->
                            TimerOverlayManager.onPanelAction(PanelAction.PrevTimer)
                        !rightArrowRect.isEmpty && rightArrowRect.contains(x, y) ->
                            TimerOverlayManager.onPanelAction(PanelAction.NextTimer)
                        !pauseResumeRect.isEmpty && pauseResumeRect.contains(x, y) ->
                            TimerOverlayManager.onPanelAction(PanelAction.TogglePauseResume)
                        !resetRect.isEmpty && resetRect.contains(x, y) -> {
                            isConfirmingReset = true
                            resetConfirmHandler.removeCallbacks(resetTimeoutRunnable)
                            resetConfirmHandler.postDelayed(resetTimeoutRunnable, 3000L)
                            invalidate()
                        }
                        !openAppRect.isEmpty && openAppRect.contains(x, y) ->
                            TimerOverlayManager.onPanelAction(PanelAction.OpenApp)
                    }
                    return true
                }
            }
            return false
        }

        private fun truncate(text: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(text) <= maxWidth) return text
            var t = text
            while (t.isNotEmpty() && paint.measureText("$t…") > maxWidth) t = t.dropLast(1)
            return if (t.isEmpty()) "…" else "$t…"
        }
    }

    private data class OverlayProgressSpec(
        val primaryFraction: Float,
        val overlayFraction: Float,
        val showTwoLayer: Boolean,
        val sweepSign: Float,
    )
}
