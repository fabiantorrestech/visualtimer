package com.fabiantorrestech.visualtimerplus.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
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
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import com.fabiantorrestech.visualtimerplus.util.formatClockTime
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

    private var overlayView: OverlayTimerView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var touchListener: OverlayTouchListener? = null
    private var touchListenerTimerIndex: Int = -1
    private var imeBottomInset = 0
    private var imeRestorePosition: OverlayPosition? = null
    private var overlayAutoMovedForIme = false
    private var overlayMovedByUserWhileImeVisible = false

    private const val PREFS_NAME = "visual_timer_prefs"
    private const val KEY_OVERLAY_SNAPPED_LEFT = "overlay_pos_left_edge"
    private const val KEY_OVERLAY_POS_Y = "overlay_pos_y"
    private const val OVERLAY_POS_UNSET = -1

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
        refreshOverlay()
    }

    fun canDrawOverlays(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun permissionSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

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
        updateOverlay(timerIndex, timer)
    }

    private fun ensureOverlayView() {
        if (overlayView != null && layoutParams != null) return

        val sizePx = sizePx(latestState.overlaySize)
        val overlay = OverlayTimerView(appContext)
        ViewCompat.setOnApplyWindowInsetsListener(overlay) { _, insets ->
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
        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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

        windowManager.addView(overlay, params)
        overlayView = overlay
        layoutParams = params
        ViewCompat.requestApplyInsets(overlay)
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
        if (params.width != sizePx || params.height != sizePx) {
            params.width = sizePx
            params.height = sizePx
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

        view.render(
            timer = timer,
            style = latestState.overlayStyle,
            overlaySize = latestState.overlaySize,
        )

        clampPosition(params)
        applyImeAvoidanceIfNeeded(params)
        updateOverlayLayout(params)
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
        }
        overlayView = null
        layoutParams = null
        touchListener = null
        touchListenerTimerIndex = -1
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
        params.x = if (params.x + (params.width / 2) < metrics.widthPixels / 2) 0 else maxX
        clampPosition(params)
        applyImeAvoidanceIfNeeded(params)
        updateOverlayLayout(params)
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_OVERLAY_SNAPPED_LEFT, params.x == 0)
            .putInt(KEY_OVERLAY_POS_Y, params.y)
            .apply()
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
        overlayView?.let { view ->
            windowManager.updateViewLayout(view, params)
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

    private class OverlayTouchListener(
        private val timerIndex: Int,
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var lastTouchX = 0f
        private var lastTouchY = 0f
        private var dragStarted = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val params = TimerOverlayManager.layoutParams ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    dragStarted = false
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
                        TimerOverlayManager.markOverlayMovedByUserDuringIme()
                    }
                    TimerOverlayManager.applyImeAvoidanceIfNeeded(params)
                    TimerOverlayManager.updateOverlayLayout(params)
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (!dragStarted) {
                        restorePosition(params)
                        TimerOverlayManager.openAppToTimer(timerIndex)
                    } else {
                        TimerOverlayManager.snapToEdge()
                    }
                    dragStarted = false
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
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

    private fun markOverlayMovedByUserDuringIme() {
        if (imeBottomInset > 0) {
            overlayMovedByUserWhileImeVisible = true
        }
    }

    private data class OverlayPosition(
        val x: Int,
        val y: Int,
    )

    private const val IME_CLEARANCE_DP = 16

    private class OverlayTimerView(context: Context) : View(context) {
        private val ringTrackColor = Color.parseColor("#33111111")
        private val timerTrackColor = Color.parseColor("#CC000000")
        private val ringRed = Color.parseColor("#EF4444")
        private val timerRedDark = Color.parseColor("#7F1D1D")
        private val pausedColor = Color.parseColor("#D4D4D8")
        private val overtimeColor = Color.parseColor("#DC2626")
        private val outlineColor = Color.parseColor("#33FFFFFF")

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        private val arcBounds = RectF()

        private var timer: TimerInstance = TimerInstance(id = 0)
        private var style: OverlayStyle = OverlayStyle.Ring
        private var overlaySize: OverlaySize = OverlaySize.Medium

        fun render(timer: TimerInstance, style: OverlayStyle, overlaySize: OverlaySize) {
            this.timer = timer
            this.style = style
            this.overlaySize = overlaySize
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

            if (timer.status == TimerStatus.Overtime) {
                postInvalidateOnAnimation()
            }
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
                canvas.drawArc(
                    arcBounds,
                    -90f,
                    progress.sweepSign * 360f * progress.primaryFraction,
                    false,
                    ringPaint,
                )
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
                    canvas.drawArc(
                        arcBounds,
                        -90f,
                        progress.sweepSign * 360f * progress.overlayFraction,
                        true,
                        fillPaint,
                    )
                }
            } else if (progress.primaryFraction > 0f) {
                fillPaint.color = accentColor()
                canvas.drawArc(
                    arcBounds,
                    -90f,
                    progress.sweepSign * 360f * progress.primaryFraction,
                    true,
                    fillPaint,
                )
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

        private fun progressSpec(timer: TimerInstance): OverlayProgressSpec {
            if (timer.status == TimerStatus.Overtime) {
                return OverlayProgressSpec(
                    primaryFraction = 1f,
                    overlayFraction = 0f,
                    showTwoLayer = false,
                    sweepSign = if (timer.settings.clockwiseModeEnabled) 1f else -1f,
                )
            }

            val displayMs = timer.displayMillis
            val useFullClock = timer.settings.fullClockMode && timer.status != TimerStatus.Idle
            val sweepSign = if (timer.settings.clockwiseModeEnabled) 1f else -1f

            if (useFullClock) {
                val total = timer.selectedDurationMillis.coerceAtLeast(1L)
                return OverlayProgressSpec(
                    primaryFraction = (displayMs.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                    overlayFraction = 0f,
                    showTwoLayer = false,
                    sweepSign = sweepSign,
                )
            }

            return when {
                displayMs >= ONE_HOUR_MILLIS * 2L -> OverlayProgressSpec(
                    primaryFraction = 1f,
                    overlayFraction = 1f,
                    showTwoLayer = true,
                    sweepSign = sweepSign,
                )
                displayMs > ONE_HOUR_MILLIS -> OverlayProgressSpec(
                    primaryFraction = 1f,
                    overlayFraction = ((displayMs - ONE_HOUR_MILLIS).toFloat() / ONE_HOUR_MILLIS).coerceIn(0f, 1f),
                    showTwoLayer = true,
                    sweepSign = sweepSign,
                )
                else -> OverlayProgressSpec(
                    primaryFraction = (displayMs.toFloat() / ONE_HOUR_MILLIS).coerceIn(0f, 1f),
                    overlayFraction = 0f,
                    showTwoLayer = false,
                    sweepSign = sweepSign,
                )
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

    private data class OverlayProgressSpec(
        val primaryFraction: Float,
        val overlayFraction: Float,
        val showTwoLayer: Boolean,
        val sweepSign: Float,
    )
}
