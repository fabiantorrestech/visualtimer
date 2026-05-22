package com.fabiantorrestech.visualtimerplus.ui.eink

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.fabiantorrestech.visualtimerplus.timer.TimerInstance
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import com.fabiantorrestech.visualtimerplus.util.formatApproxTime
import com.fabiantorrestech.visualtimerplus.util.formatClockTime
import kotlin.math.cos
import kotlin.math.sin

class EInkTimerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
    }
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val ringOval = RectF()

    private var timeText: String = "00:00"
    private var statusText: String = "READY"
    private var progressFraction: Float = 0f
    private var isElapsed: Boolean = false

    // Settings
    private var displayMode: String = EInkSettingsActivity.MODE_BARS
    private var elapsedMode: String = EInkSettingsActivity.MODE_BLINK

    // Blink state (driven by activity Handler)
    private var blinkTick: Boolean = false

    fun applySettings(prefs: SharedPreferences) {
        displayMode = prefs.getString(EInkSettingsActivity.PREF_DISPLAY_MODE, EInkSettingsActivity.MODE_BARS)
            ?: EInkSettingsActivity.MODE_BARS
        elapsedMode = prefs.getString(EInkSettingsActivity.PREF_ELAPSED_MODE, EInkSettingsActivity.MODE_BLINK)
            ?: EInkSettingsActivity.MODE_BLINK
        invalidate()
    }

    fun setBlinkTick(tick: Boolean) {
        blinkTick = tick
        invalidate()
    }

    fun update(timer: TimerInstance) {
        timeText = when (timer.status) {
            TimerStatus.Running -> timer.displayMillis.formatApproxTime()
            TimerStatus.Overtime -> "+${timer.displayMillis.formatClockTime()}"
            else -> timer.displayMillis.formatClockTime()
        }
        statusText = when (timer.status) {
            TimerStatus.Running -> "RUNNING"
            TimerStatus.Paused -> "PAUSED"
            TimerStatus.Overtime -> "OVERTIME"
            TimerStatus.Finished -> "DONE"
            TimerStatus.Idle -> if (timer.selectedDurationMillis > 0L) "READY" else "SET TIME"
        }
        progressFraction = when (timer.status) {
            TimerStatus.Running, TimerStatus.Paused, TimerStatus.Idle -> {
                val total = timer.selectedDurationMillis.coerceAtLeast(1L)
                (timer.remainingMillis.toFloat() / total).coerceIn(0f, 1f)
            }
            TimerStatus.Overtime, TimerStatus.Finished -> 0f
        }
        isElapsed = timer.status == TimerStatus.Overtime || timer.status == TimerStatus.Finished
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val inverted = isElapsed && elapsedMode == EInkSettingsActivity.MODE_BLINK && blinkTick
        val bg = if (inverted) Color.BLACK else Color.WHITE
        val fg = if (inverted) Color.WHITE else Color.BLACK

        canvas.drawColor(bg)

        // Exclamation overlay (mode B) — blink "!!" above time text
        val showExclamation = isElapsed && elapsedMode == EInkSettingsActivity.MODE_EXCLAMATION && blinkTick

        if (displayMode == EInkSettingsActivity.MODE_RADIAL) {
            drawRadial(canvas, w, h, fg, showExclamation)
        } else {
            drawBars(canvas, w, h, fg, showExclamation)
        }
    }

    private fun drawBars(canvas: Canvas, w: Float, h: Float, fg: Int, showExclamation: Boolean) {
        textPaint.color = fg

        if (showExclamation) {
            val exclSize = (w * 0.18f).coerceAtMost(h * 0.25f)
            textPaint.textSize = exclSize
            canvas.drawText("!!", w / 2f, h * 0.20f, textPaint)
        }

        val baseTimeTextSize = (w * 0.20f).coerceAtMost(h * 0.30f)
        val timeTextSize = if (timeText.count { it == ':' } >= 2) baseTimeTextSize * 0.9f else baseTimeTextSize
        textPaint.textSize = timeTextSize
        val timeCenterY = h * 0.38f
        val timeBaseline = timeCenterY - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(timeText, w / 2f, timeBaseline, textPaint)

        val statusTextSize = (w * 0.065f).coerceAtMost(h * 0.085f)
        textPaint.textSize = statusTextSize
        canvas.drawText(statusText, w / 2f, h * 0.56f, textPaint)

        val blockCount = 20
        val totalBlockWidth = w * 0.88f
        val blockSpacing = totalBlockWidth / blockCount
        val blockW = blockSpacing * 0.68f
        val blockH = h * 0.09f
        val blockTop = h * 0.68f
        val startX = (w - totalBlockWidth) / 2f
        val filledCount = (progressFraction * blockCount).toInt()

        blockPaint.color = fg
        for (i in 0 until blockCount) {
            val left = startX + i * blockSpacing
            val right = left + blockW
            if (i < filledCount) {
                blockPaint.style = Paint.Style.FILL
                canvas.drawRect(left, blockTop, right, blockTop + blockH, blockPaint)
            } else {
                blockPaint.style = Paint.Style.STROKE
                blockPaint.strokeWidth = 2f
                canvas.drawRect(left, blockTop, right, blockTop + blockH, blockPaint)
            }
        }
    }

    private fun drawRadial(canvas: Canvas, w: Float, h: Float, fg: Int, showExclamation: Boolean) {
        val minDim = minOf(w, h)
        val ringRadius = minDim * 0.36f
        val ringStroke = ringRadius * 0.42f
        val centerX = w / 2f
        val centerY = h * 0.44f

        ringOval.set(
            centerX - ringRadius, centerY - ringRadius,
            centerX + ringRadius, centerY + ringRadius,
        )

        // Filled arc (remaining time)
        arcPaint.color = fg
        arcPaint.strokeWidth = ringStroke
        if (progressFraction > 0f) {
            canvas.drawArc(ringOval, -90f, 360f * progressFraction, false, arcPaint)
        }

        // Thin outline for outer and inner ring border
        arcPaint.strokeWidth = 2f
        ringOval.set(
            centerX - ringRadius, centerY - ringRadius,
            centerX + ringRadius, centerY + ringRadius,
        )
        canvas.drawArc(ringOval, 0f, 360f, false, arcPaint)
        val innerR = ringRadius - ringStroke / 2f
        ringOval.set(centerX - innerR, centerY - innerR, centerX + innerR, centerY + innerR)
        canvas.drawArc(ringOval, 0f, 360f, false, arcPaint)

        // 60 tick marks at the outer rim (thicker at quarter-points)
        val outerR = ringRadius + ringStroke / 2f
        tickPaint.color = fg
        for (i in 0 until 60) {
            val isQuarter = i % 15 == 0
            val tickLen = if (isQuarter) ringRadius * 0.10f else ringRadius * 0.05f
            tickPaint.strokeWidth = if (isQuarter) 3f else 1.5f
            val angleRad = Math.toRadians(-90.0 + i * 6.0)
            val cosA = cos(angleRad).toFloat()
            val sinA = sin(angleRad).toFloat()
            canvas.drawLine(
                centerX + outerR * cosA, centerY + outerR * sinA,
                centerX + (outerR + tickLen) * cosA, centerY + (outerR + tickLen) * sinA,
                tickPaint,
            )
        }

        textPaint.color = fg

        if (showExclamation) {
            val exclSize = (w * 0.14f).coerceAtMost(h * 0.18f)
            textPaint.textSize = exclSize
            canvas.drawText("!!", centerX, centerY - ringRadius * 0.20f, textPaint)
        } else {
            val baseTimeSize = (ringRadius * 0.60f).coerceAtMost(h * 0.18f)
            val timeTextSize = if (timeText.count { it == ':' } >= 2) baseTimeSize * 0.9f else baseTimeSize
            textPaint.textSize = timeTextSize
            val timeBaseline = centerY - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(timeText, centerX, timeBaseline, textPaint)
        }

        val statusTextSize = (w * 0.065f).coerceAtMost(h * 0.085f)
        textPaint.textSize = statusTextSize
        canvas.drawText(statusText, centerX, h * 0.80f, textPaint)
    }
}
