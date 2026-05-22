package com.fabiantorrestech.visualtimerplus.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.fabiantorrestech.visualtimerplus.ui.eink.EInkMainActivity
import com.fabiantorrestech.visualtimerplus.notification.TimerNotificationManager
import com.fabiantorrestech.visualtimerplus.timer.AppState
import com.fabiantorrestech.visualtimerplus.timer.OverlayLabelPosition
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
import kotlin.math.ceil
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
    private var overlayTimerStack: LinearLayout? = null
    private var overlayView: OverlayTimerView? = null
    private var overlayLabelView: OverlayNameLabelView? = null
    private var overlayPanel: OverlayPanelView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var touchListener: OverlayTouchListener? = null
    private var touchListenerTimerIndex: Int = -1
    private var imeBottomInset = 0
    private var imeRestorePosition: OverlayPosition? = null
    private var panelCollapsedRestorePosition: OverlayPosition? = null
    private var overlayAutoMovedForIme = false
    private var overlayMovedByUserWhileImeVisible = false

    private var isPanelOpen: Boolean = false
    private var isSnappedLeft: Boolean = false
    private var renderedCollapsedLabelPosition: OverlayLabelPosition? = null
    private var panelFocusedTimerIndex: Int = 0
    private val longPressHandler = Handler(Looper.getMainLooper())

    private val overlayBlinkHandler = Handler(Looper.getMainLooper())
    private var isOverlayBlinkActive = false
    private var overlayBlinkTick = false
    private var cachedElapsedMode = "blink"
    private val overlayBlinkRunnable = object : Runnable {
        override fun run() {
            overlayBlinkTick = !overlayBlinkTick
            overlayView?.setBlinkTick(overlayBlinkTick, cachedElapsedMode)
            overlayBlinkHandler.postDelayed(this, 2500L)
        }
    }

    private fun startOverlayBlink() {
        if (isOverlayBlinkActive) return
        cachedElapsedMode = appContext.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .getString("eink_elapsed_mode", "blink") ?: "blink"
        isOverlayBlinkActive = true
        overlayBlinkTick = false
        overlayBlinkHandler.post(overlayBlinkRunnable)
    }

    private fun stopOverlayBlink() {
        if (!isOverlayBlinkActive) return
        isOverlayBlinkActive = false
        overlayBlinkHandler.removeCallbacks(overlayBlinkRunnable)
        overlayView?.setBlinkTick(false, cachedElapsedMode)
    }

    private var lastAppliedParamsX: Int = Int.MIN_VALUE
    private var lastAppliedParamsY: Int = Int.MIN_VALUE
    private var lastAppliedParamsWidth: Int = -1
    private var lastAppliedParamsHeight: Int = -1
    private var lastAppliedParamsFlags: Int = -1

    private const val PREFS_NAME = "visual_timer_prefs"
    private const val KEY_OVERLAY_SNAPPED_LEFT = "overlay_pos_left_edge"
    private const val KEY_OVERLAY_POS_Y = "overlay_pos_y"
    private const val OVERLAY_POS_UNSET = -1
    private const val PANEL_WIDTH_DP = 176
    private const val LONG_PRESS_DURATION_MS = 500L
    private const val IME_CLEARANCE_DP = 16
    private const val LABEL_EDGE_CLEARANCE_DP = 6

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
            stopOverlayBlink()
            removeOverlay()
            return
        }

        ensureOverlayView()
        updateOverlay(timerIndex!!, timer!!)
        if (timer.status == TimerStatus.Overtime) startOverlayBlink() else stopOverlayBlink()
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
        val label = OverlayNameLabelView(appContext)
        val overlay = OverlayTimerView(appContext)
        val timerStack = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
        }
        timerStack.addView(overlay, LinearLayout.LayoutParams(sizePx, sizePx))
        val container = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        container.addView(timerStack, LinearLayout.LayoutParams(sizePx, sizePx))

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
        overlayTimerStack = timerStack
        overlayView = overlay
        overlayLabelView = label
        layoutParams = params
        ViewCompat.requestApplyInsets(container)
    }

    private fun updateOverlay(timerIndex: Int, timer: TimerInstance) {
        val params = layoutParams ?: return
        val timerStack = overlayTimerStack ?: return
        val view = overlayView ?: return
        val labelView = overlayLabelView ?: return

        if (touchListener == null || touchListenerTimerIndex != timerIndex) {
            touchListener = OverlayTouchListener(timerIndex)
            touchListenerTimerIndex = timerIndex
            view.setOnTouchListener(touchListener)
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
        val layoutSpec = buildLayoutSpec(timer, params)

        applyTimerStackLayout(
            timerStack = timerStack,
            view = view,
            labelView = labelView,
            layoutSpec = layoutSpec,
            timerName = timer.activeTimerName.trim(),
        )
        applyContainerChildOrder(layoutSpec)
        applyWindowSize(params, layoutSpec)

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

    private fun buildLayoutSpec(timer: TimerInstance, params: WindowManager.LayoutParams): OverlayLayoutSpec {
        val timerSizePx = sizePx(latestState.overlaySize)
        val panelWidthPx = if (isPanelOpen) dp(PANEL_WIDTH_DP) else 0
        val labelText = timer.activeTimerName.trim()
            .takeIf { !isPanelOpen && latestState.overlayShowTimerName && it.isNotBlank() }
        val labelHeightPx = labelText?.let { collapsedLabelHeightPx(latestState.overlaySize) } ?: 0
        val renderedLabelPosition = labelText?.let {
            resolveRenderedLabelPosition(
                preferredPosition = latestState.overlayTimerNamePosition,
                params = params,
                timerSizePx = timerSizePx,
                labelHeightPx = labelHeightPx,
            )
        }
        return OverlayLayoutSpec(
            timerSizePx = timerSizePx,
            panelWidthPx = panelWidthPx,
            labelText = labelText,
            renderedLabelPosition = renderedLabelPosition,
            totalWidth = timerSizePx + panelWidthPx,
            totalHeight = timerSizePx + labelHeightPx,
        )
    }

    private fun resolveRenderedLabelPosition(
        preferredPosition: OverlayLabelPosition,
        params: WindowManager.LayoutParams,
        timerSizePx: Int,
        labelHeightPx: Int,
    ): OverlayLabelPosition {
        val metrics = appContext.resources.displayMetrics
        val requiredClearancePx = labelHeightPx + dp(LABEL_EDGE_CLEARANCE_DP)
        val hysteresisPx = dp(LABEL_EDGE_CLEARANCE_DP)
        val timerTopPx = currentTimerTop(params, labelHeightPx)
        val timerBottomPx = timerTopPx + timerSizePx
        val topClearancePx = timerTopPx
        val bottomClearancePx = metrics.heightPixels - timerBottomPx
        val currentRenderedPosition = renderedCollapsedLabelPosition ?: preferredPosition

        val resolvedPosition = when (preferredPosition) {
            OverlayLabelPosition.Top -> {
                if (currentRenderedPosition == OverlayLabelPosition.Bottom) {
                    if (topClearancePx >= requiredClearancePx + hysteresisPx) {
                        OverlayLabelPosition.Top
                    } else {
                        OverlayLabelPosition.Bottom
                    }
                } else {
                    if (topClearancePx < requiredClearancePx) {
                        OverlayLabelPosition.Bottom
                    } else {
                        OverlayLabelPosition.Top
                    }
                }
            }
            OverlayLabelPosition.Bottom -> {
                if (currentRenderedPosition == OverlayLabelPosition.Top) {
                    if (bottomClearancePx >= requiredClearancePx + hysteresisPx) {
                        OverlayLabelPosition.Bottom
                    } else {
                        OverlayLabelPosition.Top
                    }
                } else {
                    if (bottomClearancePx < requiredClearancePx) {
                        OverlayLabelPosition.Top
                    } else {
                        OverlayLabelPosition.Bottom
                    }
                }
            }
        }
        renderedCollapsedLabelPosition = resolvedPosition
        return resolvedPosition
    }

    private fun currentTimerTop(params: WindowManager.LayoutParams, labelHeightPx: Int): Int {
        return when (currentCollapsedLabelLayoutPosition()) {
            OverlayLabelPosition.Top -> params.y + labelHeightPx
            OverlayLabelPosition.Bottom,
            null -> params.y
        }
    }

    private fun currentCollapsedLabelLayoutPosition(): OverlayLabelPosition? {
        val timerStack = overlayTimerStack ?: return null
        val labelView = overlayLabelView ?: return null
        if (labelView.visibility != View.VISIBLE || timerStack.childCount != 2) return null
        return when {
            timerStack.getChildAt(0) === labelView -> OverlayLabelPosition.Top
            timerStack.getChildAt(1) === labelView -> OverlayLabelPosition.Bottom
            else -> null
        }
    }

    private fun applyTimerStackLayout(
        timerStack: LinearLayout,
        view: OverlayTimerView,
        labelView: OverlayNameLabelView,
        layoutSpec: OverlayLayoutSpec,
        timerName: String,
    ) {
        val timerSizePx = layoutSpec.timerSizePx
        val labelHeightPx = if (layoutSpec.labelText != null) collapsedLabelHeightPx(latestState.overlaySize) else 0
        labelView.updateContent(
            text = timerName,
            overlaySize = latestState.overlaySize,
            visible = layoutSpec.labelText != null,
        )
        view.layoutParams = LinearLayout.LayoutParams(timerSizePx, timerSizePx)
        val needsRebuild = when (layoutSpec.renderedLabelPosition) {
            OverlayLabelPosition.Top -> timerStack.childCount != 2 ||
                timerStack.getChildAt(0) !== labelView ||
                timerStack.getChildAt(1) !== view
            OverlayLabelPosition.Bottom -> timerStack.childCount != 2 ||
                timerStack.getChildAt(0) !== view ||
                timerStack.getChildAt(1) !== labelView
            null -> timerStack.childCount != 1 || timerStack.getChildAt(0) !== view
        }
        if (needsRebuild) {
            timerStack.removeAllViews()
            if (layoutSpec.renderedLabelPosition == OverlayLabelPosition.Top) {
                timerStack.addView(labelView, LinearLayout.LayoutParams(timerSizePx, labelHeightPx))
            }
            timerStack.addView(view, LinearLayout.LayoutParams(timerSizePx, timerSizePx))
            if (layoutSpec.renderedLabelPosition == OverlayLabelPosition.Bottom) {
                timerStack.addView(labelView, LinearLayout.LayoutParams(timerSizePx, labelHeightPx))
            }
        } else if (layoutSpec.renderedLabelPosition != null) {
            labelView.layoutParams = LinearLayout.LayoutParams(timerSizePx, labelHeightPx)
        }
        timerStack.layoutParams = LinearLayout.LayoutParams(timerSizePx, layoutSpec.totalHeight)
    }

    private fun applyContainerChildOrder(layoutSpec: OverlayLayoutSpec) {
        val container = overlayContainer ?: return
        val timerStack = overlayTimerStack ?: return
        val panel = overlayPanel

        if (isPanelOpen && panel != null) {
            val panelLayoutParams = LinearLayout.LayoutParams(layoutSpec.panelWidthPx, layoutSpec.timerSizePx)
            val desiredFirst = if (isSnappedLeft) timerStack else panel
            val desiredSecond = if (isSnappedLeft) panel else timerStack
            val needsRebuild = container.childCount != 2 ||
                container.getChildAt(0) !== desiredFirst ||
                container.getChildAt(1) !== desiredSecond
            if (needsRebuild) {
                container.removeAllViews()
                if (isSnappedLeft) {
                    container.addView(timerStack, LinearLayout.LayoutParams(layoutSpec.timerSizePx, layoutSpec.totalHeight))
                    container.addView(panel, panelLayoutParams)
                } else {
                    container.addView(panel, panelLayoutParams)
                    container.addView(timerStack, LinearLayout.LayoutParams(layoutSpec.timerSizePx, layoutSpec.totalHeight))
                }
            } else {
                timerStack.layoutParams = LinearLayout.LayoutParams(layoutSpec.timerSizePx, layoutSpec.totalHeight)
                panel.layoutParams = panelLayoutParams
            }
            panel.isPanelOnRight = isSnappedLeft
            panel.invalidate()
        } else {
            val needsRebuild = container.childCount != 1 || container.getChildAt(0) !== timerStack
            if (needsRebuild) {
                container.removeAllViews()
                container.addView(timerStack, LinearLayout.LayoutParams(layoutSpec.timerSizePx, layoutSpec.totalHeight))
            } else {
                timerStack.layoutParams = LinearLayout.LayoutParams(layoutSpec.timerSizePx, layoutSpec.totalHeight)
            }
        }
    }

    private fun applyWindowSize(params: WindowManager.LayoutParams, layoutSpec: OverlayLayoutSpec) {
        if (params.width != layoutSpec.totalWidth) {
            params.width = layoutSpec.totalWidth
        }
        if (params.height != layoutSpec.totalHeight) {
            params.height = layoutSpec.totalHeight
        }
    }

    private fun removeOverlay() {
        val container = overlayContainer ?: return
        val wm = activeWindowManager ?: windowManager
        try {
            wm.removeView(container)
        } catch (_: IllegalArgumentException) {
        }
        overlayContainer = null
        overlayTimerStack = null
        overlayView = null
        overlayLabelView = null
        overlayPanel = null
        isPanelOpen = false
        panelCollapsedRestorePosition = null
        layoutParams = null
        activeWindowManager = null
        touchListener = null
        touchListenerTimerIndex = -1
        renderedCollapsedLabelPosition = null
        longPressHandler.removeCallbacksAndMessages(null)
        clearImeTracking()
        lastAppliedParamsX = Int.MIN_VALUE
        lastAppliedParamsY = Int.MIN_VALUE
        lastAppliedParamsWidth = -1
        lastAppliedParamsHeight = -1
        lastAppliedParamsFlags = -1
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
        clearPanelCollapsedRestorePosition()

        updateOverlayLayout(params)
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_OVERLAY_SNAPPED_LEFT, snapLeft)
            .putInt(KEY_OVERLAY_POS_Y, params.y)
            .apply()
    }

    private fun togglePanel(timerIndex: Int) {
        if (isPanelOpen) closePanel() else openPanel(timerIndex)
    }

    private fun clearPanelCollapsedRestorePosition() {
        panelCollapsedRestorePosition = null
    }

    private fun collapsedSnappedX(): Int {
        if (isSnappedLeft) return 0
        val collapsedWidth = sizePx(latestState.overlaySize)
        val metrics = appContext.resources.displayMetrics
        return max(0, metrics.widthPixels - collapsedWidth)
    }

    private fun openPanel(timerIndex: Int) {
        if (isPanelOpen) return
        val params = layoutParams ?: return
        panelCollapsedRestorePosition = OverlayPosition(params.x, params.y)

        panelFocusedTimerIndex = timerIndex

        val panel = OverlayPanelView(appContext, isSnappedLeft)
        overlayPanel = panel
        isPanelOpen = true

        if (!isSnappedLeft) {
            params.x -= dp(PANEL_WIDTH_DP)
        }
        updatePanelContent()
        refreshCurrentOverlay()
        clampPosition(params)
        updateOverlayLayout(params)
    }

    private fun closePanel() {
        if (!isPanelOpen) return
        val params = layoutParams ?: return
        val restorePosition = panelCollapsedRestorePosition

        overlayPanel = null
        isPanelOpen = false

        if (restorePosition != null) {
            params.x = restorePosition.x
            params.y = restorePosition.y
            clearPanelCollapsedRestorePosition()
        } else {
            params.x = collapsedSnappedX()
        }

        refreshCurrentOverlay()
        clampPosition(params)
        updateOverlayLayout(params)
    }

    private fun updatePanelSideInContainer(newSnappedLeft: Boolean) {
        isSnappedLeft = newSnappedLeft
        refreshCurrentOverlay()
    }

    private fun refreshCurrentOverlay() {
        val timerIndex = latestState.overlayTimerIndex ?: return
        val timer = latestState.timers.getOrNull(timerIndex) ?: return
        updateOverlay(timerIndex, timer)
    }

    private fun onExpandedDragStarted() {
        if (isPanelOpen) {
            clearPanelCollapsedRestorePosition()
        }
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
                    TimerStatus.Overtime, TimerStatus.Finished -> sendServiceAction(TimerService.ACTION_DISMISS_FINISHED, panelFocusedTimerIndex, false)
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
        refreshCurrentOverlay()
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
        refreshCurrentOverlay()
    }

    private fun clearImeTracking() {
        imeRestorePosition = null
        overlayAutoMovedForIme = false
        overlayMovedByUserWhileImeVisible = false
    }

    private fun updateOverlayLayout(params: WindowManager.LayoutParams) {
        val container = overlayContainer ?: return
        val wm = activeWindowManager ?: windowManager
        if (params.x == lastAppliedParamsX &&
            params.y == lastAppliedParamsY &&
            params.width == lastAppliedParamsWidth &&
            params.height == lastAppliedParamsHeight &&
            params.flags == lastAppliedParamsFlags) return
        lastAppliedParamsX = params.x
        lastAppliedParamsY = params.y
        lastAppliedParamsWidth = params.width
        lastAppliedParamsHeight = params.height
        lastAppliedParamsFlags = params.flags
        try {
            wm.updateViewLayout(container, params)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun openAppToTimer(timerIndex: Int) {
        val intent = Intent(appContext, EInkMainActivity::class.java)
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

    private fun maybeSwapPanelDuringDrag(params: WindowManager.LayoutParams, dragDeltaX: Int) {
        if (!isPanelOpen || dragDeltaX == 0) return
        val panelWidthPx = dp(PANEL_WIDTH_DP)
        val maxX = max(0, appContext.resources.displayMetrics.widthPixels - params.width)
        when {
            isSnappedLeft && dragDeltaX > 0 && params.x >= maxX -> {
                isSnappedLeft = false
                params.x -= panelWidthPx
            }
            !isSnappedLeft && dragDeltaX < 0 && params.x <= 0 -> {
                isSnappedLeft = true
                params.x += panelWidthPx
            }
        }
    }

    private fun collapsedLabelHeightPx(size: OverlaySize): Int {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = collapsedLabelTextSizePx(size)
            isFakeBoldText = true
        }
        val fontMetrics = paint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        return ceil(textHeight + (collapsedLabelVerticalPaddingPx(size) * 2f)).toInt()
    }

    private fun collapsedLabelTextSizePx(size: OverlaySize): Float = when (size) {
        OverlaySize.Small -> dp(11).toFloat()
        OverlaySize.Medium -> dp(13).toFloat()
        OverlaySize.Large -> dp(15).toFloat()
    }

    private fun collapsedLabelHorizontalPaddingPx(size: OverlaySize): Int = when (size) {
        OverlaySize.Small -> dp(6)
        OverlaySize.Medium -> dp(8)
        OverlaySize.Large -> dp(10)
    }

    private fun collapsedLabelVerticalPaddingPx(size: OverlaySize): Int = when (size) {
        OverlaySize.Small -> dp(3)
        OverlaySize.Medium -> dp(4)
        OverlaySize.Large -> dp(5)
    }

    private enum class PanelAction { PrevTimer, NextTimer, TogglePauseResume, ConfirmReset, OpenApp }

    private data class OverlayPosition(val x: Int, val y: Int)

    private data class OverlayLayoutSpec(
        val timerSizePx: Int,
        val panelWidthPx: Int,
        val labelText: String?,
        val renderedLabelPosition: OverlayLabelPosition?,
        val totalWidth: Int,
        val totalHeight: Int,
    )

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
                    val totalDeltaX = params.x - initialX
                    val totalDeltaY = params.y - initialY
                    if (!dragStarted && hasExceededTouchSlop(totalDeltaX.toFloat(), totalDeltaY.toFloat())) {
                        dragStarted = true
                        TimerOverlayManager.longPressHandler.removeCallbacks(longPressRunnable)
                        TimerOverlayManager.markOverlayMovedByUserDuringIme()
                        TimerOverlayManager.onExpandedDragStarted()
                    }
                    if (dragStarted) {
                        TimerOverlayManager.maybeSwapPanelDuringDrag(params, dx.toInt())
                    }
                    TimerOverlayManager.refreshCurrentOverlay()
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
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
            isFakeBoldText = true
        }

        private var timer: TimerInstance = TimerInstance(id = 0)
        private var style: OverlayStyle = OverlayStyle.Ring
        private var overlaySize: OverlaySize = OverlaySize.Medium
        private var lastDisplayBucket: Long = Long.MIN_VALUE
        private var blinkTick: Boolean = false
        private var blinkMode: String = "blink"

        fun setBlinkTick(tick: Boolean, mode: String) {
            blinkTick = tick
            blinkMode = mode
            invalidate()
        }

        fun render(timer: TimerInstance, style: OverlayStyle, overlaySize: OverlaySize) {
            val newBucket = computeDisplayBucket(timer, style, overlaySize)
            this.timer = timer
            this.style = style
            this.overlaySize = overlaySize
            if (newBucket != lastDisplayBucket) {
                lastDisplayBucket = newBucket
                invalidate()
            }
        }

        private fun computeDisplayBucket(timer: TimerInstance, style: OverlayStyle, overlaySize: OverlaySize): Long {
            val timeBucket = when {
                timer.status == TimerStatus.Overtime -> timer.remainingMillis / 15_000L
                timer.remainingMillis > 60_000L -> timer.remainingMillis / 60_000L
                else -> timer.remainingMillis / 15_000L
            }
            return timeBucket * 1_000_000L +
                timer.status.ordinal * 1_000L +
                style.ordinal * 10L +
                overlaySize.ordinal.toLong()
        }

        // No-op: indicator is not drawn on e-ink overlay; haptic confirms the action
        fun showStatusIndicator(@Suppress("UNUSED_PARAMETER") isPaused: Boolean) = Unit

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val isElapsed = timer.status == TimerStatus.Overtime || timer.status == TimerStatus.Finished
            val invertColors = isElapsed && blinkMode == "blink" && blinkTick
            val showExclamation = isElapsed && blinkMode == "exclamation" && blinkTick

            val bgColor = if (invertColors) Color.BLACK else Color.WHITE
            val fgColor = if (invertColors) Color.WHITE else Color.BLACK

            fillPaint.color = bgColor
            canvas.drawRect(0f, 0f, w, h, fillPaint)

            val border = max(2f, w * 0.04f)
            outlinePaint.strokeWidth = border
            outlinePaint.color = fgColor
            canvas.drawRect(border / 2f, border / 2f, w - border / 2f, h - border / 2f, outlinePaint)

            val timeStr = when {
                showExclamation -> "!"
                timer.status == TimerStatus.Overtime -> "+${timer.displayMillis.formatClockTime()}"
                else -> timer.displayMillis.formatClockTime()
            }
            textPaint.color = fgColor
            textPaint.textSize = w * 0.26f
            val baseline = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(timeStr, w / 2f, baseline, textPaint)
        }
    }

    private class OverlayNameLabelView(context: Context) : View(context) {
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CC000000")
            style = Paint.Style.FILL
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        private val backgroundRect = RectF()

        private var text: String = ""
        private var overlaySize: OverlaySize = OverlaySize.Medium
        private var isLabelVisible: Boolean = false

        fun updateContent(text: String, overlaySize: OverlaySize, visible: Boolean) {
            this.text = text
            this.overlaySize = overlaySize
            this.isLabelVisible = visible
            visibility = if (visible) VISIBLE else GONE
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!isLabelVisible || text.isBlank()) return

            val horizontalPadding = TimerOverlayManager.collapsedLabelHorizontalPaddingPx(overlaySize).toFloat()
            val cornerRadius = height / 2f
            backgroundRect.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, backgroundPaint)

            textPaint.textSize = TimerOverlayManager.collapsedLabelTextSizePx(overlaySize)
            val availableWidth = (width - horizontalPadding * 2f).coerceAtLeast(0f)
            val displayText = truncate(text, availableWidth)
            val baseline = height / 2f - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(displayText, width / 2f, baseline, textPaint)
        }

        private fun truncate(value: String, maxWidth: Float): String {
            if (textPaint.measureText(value) <= maxWidth) return value
            var trimmed = value
            while (trimmed.isNotEmpty() && textPaint.measureText("$trimmed…") > maxWidth) {
                trimmed = trimmed.dropLast(1)
            }
            return if (trimmed.isEmpty()) "…" else "$trimmed…"
        }
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
                canvas.drawText(when { isOvertime -> "✕"; isPaused -> "▶"; else -> "⏸" }, thirdW * 0.5f, iconY, textPaint)
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

}
