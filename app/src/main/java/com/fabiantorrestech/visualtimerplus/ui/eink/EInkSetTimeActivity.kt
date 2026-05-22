package com.fabiantorrestech.visualtimerplus.ui.eink

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.fabiantorrestech.visualtimerplus.R
import com.fabiantorrestech.visualtimerplus.timer.MAX_DURATION_MILLIS
import java.util.Locale

class EInkSetTimeActivity : ComponentActivity() {

    private var hours: Int = 0
    private var minutes: Int = 0
    private var seconds: Int = 0
    private lateinit var timeDisplay: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eink_set_time)

        val currentDurationMs = intent.getLongExtra(EXTRA_CURRENT_DURATION_MS, 0L)
        val totalSeconds = (currentDurationMs / 1000L).coerceAtLeast(0L)
        hours = (totalSeconds / 3600L).toInt().coerceIn(0, 99)
        minutes = ((totalSeconds % 3600L) / 60L).toInt()
        seconds = (totalSeconds % 60L).toInt()

        timeDisplay = findViewById(R.id.timeDisplay)
        updateDisplay()

        findViewById<TextView>(R.id.backButton).setOnClickListener { finish() }
        findViewById<TextView>(R.id.cancelButton).setOnClickListener { finish() }

        findViewById<TextView>(R.id.confirmButton).setOnClickListener {
            val durationMs = (hours * 3600L + minutes * 60L + seconds) * 1000L
            if (durationMs > 0L) {
                setResult(
                    RESULT_OK,
                    Intent().putExtra(RESULT_DURATION_MS, durationMs.coerceAtMost(MAX_DURATION_MILLIS)),
                )
            }
            finish()
        }

        // Hours
        findViewById<TextView>(R.id.hoursUp).setOnClickListener {
            hours = (hours + 1).coerceAtMost(99)
            clampMinutesSeconds()
            updateDisplay()
        }
        findViewById<TextView>(R.id.hoursDown).setOnClickListener {
            hours = (hours - 1).coerceAtLeast(0)
            updateDisplay()
        }

        // Minutes
        findViewById<TextView>(R.id.minutesUp).setOnClickListener {
            minutes = (minutes + 1) % 60
            updateDisplay()
        }
        findViewById<TextView>(R.id.minutesDown).setOnClickListener {
            minutes = if (minutes == 0) 59 else minutes - 1
            updateDisplay()
        }

        // Seconds
        findViewById<TextView>(R.id.secondsUp).setOnClickListener {
            seconds = (seconds + 1) % 60
            updateDisplay()
        }
        findViewById<TextView>(R.id.secondsDown).setOnClickListener {
            seconds = if (seconds == 0) 59 else seconds - 1
            updateDisplay()
        }
    }

    private fun clampMinutesSeconds() {
        val maxTotalSeconds = MAX_DURATION_MILLIS / 1000L
        val totalSeconds = hours * 3600L + minutes * 60L + seconds
        if (totalSeconds > maxTotalSeconds) {
            minutes = 0
            seconds = 0
        }
    }

    private fun updateDisplay() {
        timeDisplay.text = if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    companion object {
        const val EXTRA_TIMER_INDEX = "timer_index"
        const val EXTRA_CURRENT_DURATION_MS = "current_duration_ms"
        const val RESULT_DURATION_MS = "result_duration_ms"
    }
}
