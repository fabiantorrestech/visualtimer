package com.fabiantorrestech.visualtimerplus.ui.screen

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fabiantorrestech.visualtimerplus.R
import com.fabiantorrestech.visualtimerplus.timer.TimerAction
import com.fabiantorrestech.visualtimerplus.timer.TimerController
import com.fabiantorrestech.visualtimerplus.timer.TimerRepository
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import com.fabiantorrestech.visualtimerplus.util.formatClockTime
import kotlinx.coroutines.launch

class TimerFinishedActivity : ComponentActivity() {

    private lateinit var controller: TimerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_eink_timer_finished)
        controller = TimerController(applicationContext)

        val timerNameText = findViewById<TextView>(R.id.timerNameText)
        val overtimeText = findViewById<TextView>(R.id.overtimeText)
        val moreTimersText = findViewById<TextView>(R.id.moreTimersText)
        val dismissButton = findViewById<TextView>(R.id.dismissButton)
        val restartButton = findViewById<TextView>(R.id.restartButton)

        dismissButton.setOnClickListener {
            val state = TimerRepository.getState()
            val firstFinished = state.timers.indexOfFirst {
                it.status == TimerStatus.Finished || it.status == TimerStatus.Overtime
            }
            if (firstFinished >= 0) controller.dispatch(TimerAction.DismissFinished(firstFinished))
        }

        restartButton.setOnClickListener {
            val state = TimerRepository.getState()
            val firstFinished = state.timers.indexOfFirst {
                it.status == TimerStatus.Finished || it.status == TimerStatus.Overtime
            }
            if (firstFinished >= 0) controller.dispatch(TimerAction.Restart(firstFinished))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TimerRepository.state.collect { state ->
                    val finishedTimers = state.timers.filter {
                        it.status == TimerStatus.Finished || it.status == TimerStatus.Overtime
                    }

                    if (finishedTimers.isEmpty()) {
                        finish()
                        return@collect
                    }

                    val timer = finishedTimers.first()

                    if (timer.activeTimerName.isNotBlank()) {
                        timerNameText.text = timer.activeTimerName
                        timerNameText.visibility = View.VISIBLE
                    } else {
                        timerNameText.visibility = View.GONE
                    }

                    if (timer.status == TimerStatus.Overtime) {
                        overtimeText.text = "+${timer.currentOvertimeSegmentMillis.formatClockTime()}"
                        overtimeText.visibility = View.VISIBLE
                    } else {
                        overtimeText.visibility = View.GONE
                    }

                    if (finishedTimers.size > 1) {
                        moreTimersText.text = "+ ${finishedTimers.size - 1} more"
                        moreTimersText.visibility = View.VISIBLE
                    } else {
                        moreTimersText.visibility = View.GONE
                    }
                }
            }
        }
    }
}
