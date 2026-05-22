package com.fabiantorrestech.visualtimerplus.ui.eink

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fabiantorrestech.visualtimerplus.R
import com.fabiantorrestech.visualtimerplus.timer.TimerAction
import com.fabiantorrestech.visualtimerplus.timer.TimerController
import com.fabiantorrestech.visualtimerplus.timer.TimerInstance
import com.fabiantorrestech.visualtimerplus.timer.TimerRepository
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import kotlinx.coroutines.launch

class EInkActiveTimerActivity : ComponentActivity() {

    private var timerIndex: Int = 0
    private lateinit var controller: TimerController
    private lateinit var timerView: EInkTimerView
    private lateinit var timerNameText: TextView
    private lateinit var startPauseButton: TextView
    private lateinit var resetButton: TextView
    private lateinit var setTimeButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eink_active_timer)

        timerIndex = intent.getIntExtra(EXTRA_TIMER_INDEX, 0)
        controller = TimerController(this)

        timerView = findViewById(R.id.timerView)
        timerNameText = findViewById(R.id.timerNameText)
        startPauseButton = findViewById(R.id.startPauseButton)
        resetButton = findViewById(R.id.resetButton)
        setTimeButton = findViewById(R.id.setTimeButton)

        findViewById<TextView>(R.id.backButton).setOnClickListener { finish() }

        startPauseButton.setOnClickListener {
            val timer = TimerRepository.getTimer(timerIndex)
            when (timer.status) {
                TimerStatus.Idle, TimerStatus.Paused -> controller.dispatch(
                    if (timer.status == TimerStatus.Paused)
                        TimerAction.Resume(timerIndex)
                    else
                        TimerAction.Start(timerIndex)
                )
                TimerStatus.Running -> controller.dispatch(TimerAction.Pause(timerIndex))
                TimerStatus.Overtime, TimerStatus.Finished ->
                    controller.dispatch(TimerAction.DismissFinished(timerIndex))
            }
        }

        resetButton.setOnClickListener {
            controller.dispatch(TimerAction.Reset(timerIndex))
        }

        setTimeButton.setOnClickListener {
            val timer = TimerRepository.getTimer(timerIndex)
            if (timer.status == TimerStatus.Running || timer.status == TimerStatus.Overtime) return@setOnClickListener
            startActivityForResult(
                Intent(this, EInkSetTimeActivity::class.java)
                    .putExtra(EInkSetTimeActivity.EXTRA_TIMER_INDEX, timerIndex)
                    .putExtra(EInkSetTimeActivity.EXTRA_CURRENT_DURATION_MS, timer.selectedDurationMillis),
                REQUEST_SET_TIME,
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var lastBucket = Long.MIN_VALUE
                TimerRepository.state.collect { state ->
                    val timer = state.timers.getOrNull(timerIndex)
                    if (timer == null) {
                        finish()
                        return@collect
                    }
                    val bucket = einkTimerBucket(timer)
                    val controlsChanged = timer.status.ordinal != (lastBucket % 10).toInt()
                    if (bucket != lastBucket) {
                        lastBucket = bucket
                        timerView.update(timer)
                    }
                    if (controlsChanged || bucket != lastBucket) {
                        updateControls(timer)
                        timerNameText.text = timer.activeTimerName.ifBlank { "Timer ${timerIndex + 1}" }
                    }
                }
            }
        }

        // Force initial draw
        val initialTimer = TimerRepository.getTimer(timerIndex)
        timerView.update(initialTimer)
        updateControls(initialTimer)
        timerNameText.text = initialTimer.activeTimerName.ifBlank { "Timer ${timerIndex + 1}" }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SET_TIME && resultCode == RESULT_OK) {
            val durationMs = data?.getLongExtra(EInkSetTimeActivity.RESULT_DURATION_MS, 0L) ?: 0L
            if (durationMs > 0L) {
                controller.dispatch(TimerAction.SetDurationExact(durationMs, timerIndex))
                val timer = TimerRepository.getTimer(timerIndex)
                timerView.update(timer)
                updateControls(timer)
            }
        }
    }

    private fun updateControls(timer: TimerInstance) {
        val canSet = timer.status == TimerStatus.Idle || timer.status == TimerStatus.Paused
        setTimeButton.alpha = if (canSet) 1f else 0.3f
        setTimeButton.isEnabled = canSet

        when (timer.status) {
            TimerStatus.Idle -> {
                startPauseButton.text = "START"
                startPauseButton.setTextColor(0xFFFFFFFF.toInt())
                startPauseButton.setBackgroundColor(0xFF000000.toInt())
                startPauseButton.isEnabled = timer.selectedDurationMillis > 0L
                startPauseButton.alpha = if (timer.selectedDurationMillis > 0L) 1f else 0.4f
            }
            TimerStatus.Running -> {
                startPauseButton.text = "PAUSE"
                startPauseButton.setTextColor(0xFF000000.toInt())
                startPauseButton.setBackgroundColor(0xFFFFFFFF.toInt())
                startPauseButton.isEnabled = true
                startPauseButton.alpha = 1f
            }
            TimerStatus.Paused -> {
                startPauseButton.text = "RESUME"
                startPauseButton.setTextColor(0xFFFFFFFF.toInt())
                startPauseButton.setBackgroundColor(0xFF000000.toInt())
                startPauseButton.isEnabled = true
                startPauseButton.alpha = 1f
            }
            TimerStatus.Overtime, TimerStatus.Finished -> {
                startPauseButton.text = "DISMISS"
                startPauseButton.setTextColor(0xFF000000.toInt())
                startPauseButton.setBackgroundColor(0xFFFFFFFF.toInt())
                startPauseButton.isEnabled = true
                startPauseButton.alpha = 1f
            }
        }
    }

    companion object {
        const val EXTRA_TIMER_INDEX = "timer_index"
        private const val REQUEST_SET_TIME = 1001

        fun einkTimerBucket(timer: TimerInstance): Long {
            val timeBucket = when {
                timer.status == TimerStatus.Overtime -> timer.remainingMillis / 15_000L
                timer.remainingMillis > 60_000L -> timer.remainingMillis / 60_000L
                else -> timer.remainingMillis / 15_000L
            }
            return timeBucket * 10L + timer.status.ordinal
        }
    }
}
