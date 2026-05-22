package com.fabiantorrestech.visualtimerplus.ui.eink

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.fabiantorrestech.visualtimerplus.timer.TimerInstance
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import com.fabiantorrestech.visualtimerplus.util.formatClockTime

class EInkTimerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
    }
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }

    private var timeText: String = "00:00"
    private var statusText: String = "READY"
    private var progressFraction: Float = 0f

    fun update(timer: TimerInstance) {
        timeText = when (timer.status) {
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
            TimerStatus.Running, TimerStatus.Paused -> {
                val total = timer.selectedDurationMillis.coerceAtLeast(1L)
                (timer.remainingMillis.toFloat() / total).coerceIn(0f, 1f)
            }
            TimerStatus.Idle -> {
                val total = timer.selectedDurationMillis.coerceAtLeast(1L)
                (timer.remainingMillis.toFloat() / total).coerceIn(0f, 1f)
            }
            TimerStatus.Overtime -> 0f
            TimerStatus.Finished -> 0f
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.drawColor(Color.WHITE)

        // Large time text — centred vertically in the upper 55% of the view
        val timeTextSize = (w * 0.20f).coerceAtMost(h * 0.30f)
        textPaint.textSize = timeTextSize
        val timeCenterY = h * 0.38f
        val timeBaseline = timeCenterY - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(timeText, w / 2f, timeBaseline, textPaint)

        // Status text below the time
        val statusTextSize = (w * 0.065f).coerceAtMost(h * 0.085f)
        textPaint.textSize = statusTextSize
        canvas.drawText(statusText, w / 2f, h * 0.56f, textPaint)

        // Progress blocks — 20 discrete segments across 90% of the width
        val blockCount = 20
        val totalBlockWidth = w * 0.88f
        val blockSpacing = totalBlockWidth / blockCount
        val blockW = blockSpacing * 0.68f
        val blockH = h * 0.09f
        val blockTop = h * 0.68f
        val startX = (w - totalBlockWidth) / 2f
        val filledCount = (progressFraction * blockCount).toInt()

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
}
