package com.fabiantorrestech.visualtimerplus.ui.eink

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fabiantorrestech.visualtimerplus.R
import com.fabiantorrestech.visualtimerplus.db.ScheduledTimerEntity
import com.fabiantorrestech.visualtimerplus.db.ScheduledTimerOutcome
import com.fabiantorrestech.visualtimerplus.db.ScheduledTimerTimingMode
import com.fabiantorrestech.visualtimerplus.db.ScheduledTimerType
import com.fabiantorrestech.visualtimerplus.timer.TimerAction
import com.fabiantorrestech.visualtimerplus.timer.TimerController
import com.fabiantorrestech.visualtimerplus.timer.TimerInstance
import com.fabiantorrestech.visualtimerplus.overlay.TimerOverlayManager
import com.fabiantorrestech.visualtimerplus.timer.TimerRepository
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import com.fabiantorrestech.visualtimerplus.schedule.ScheduledTimerManager
import com.fabiantorrestech.visualtimerplus.util.formatEndTimeFromNow
import com.fabiantorrestech.visualtimerplus.util.formatWallClockEndTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar

class EInkActiveTimerActivity : ComponentActivity() {

    private var timerIndex: Int = 0
    private lateinit var controller: TimerController
    private lateinit var timerView: EInkTimerView
    private lateinit var timerNameText: TextView
    private lateinit var endTimeText: TextView
    private lateinit var renameButton: TextView
    private lateinit var startPauseButton: TextView
    private lateinit var resetButton: TextView
    private lateinit var setTimeButton: TextView
    private lateinit var headerBar: View
    private lateinit var headerDivider: View
    private lateinit var controlsDivider: View
    private lateinit var controlsBar: View
    private var isUiVisible = true
    private lateinit var scheduleButton: TextView
    private var pendingScheduledEpochMs: Long? = null

    private val blinkHandler = Handler(Looper.getMainLooper())
    private var blinkTick = false
    private var isBlinkRunning = false
    private val blinkRunnable = object : Runnable {
        override fun run() {
            blinkTick = !blinkTick
            timerView.setBlinkTick(blinkTick)
            blinkHandler.postDelayed(this, 2000L)
        }
    }

    override fun onResume() {
        super.onResume()
        TimerOverlayManager.setAppForeground(true)
        TimerRepository.setAppForeground(true)
        applySettings()
    }

    override fun onPause() {
        super.onPause()
        TimerOverlayManager.setAppForeground(false)
        TimerRepository.setAppForeground(false)
    }

    override fun onStop() {
        super.onStop()
        stopBlinking()
    }

    private fun applySettings() {
        val prefs = getSharedPreferences("visual_timer_prefs", MODE_PRIVATE)
        timerView.applySettings(prefs)
        if (prefs.getBoolean(EInkSettingsActivity.PREF_KEEP_AWAKE, true)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun startBlinking() {
        if (isBlinkRunning) return
        isBlinkRunning = true
        blinkTick = false
        blinkHandler.post(blinkRunnable)
    }

    private fun stopBlinking() {
        if (!isBlinkRunning) return
        isBlinkRunning = false
        blinkHandler.removeCallbacks(blinkRunnable)
        timerView.setBlinkTick(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eink_active_timer)

        timerIndex = intent.getIntExtra(EXTRA_TIMER_INDEX, 0)
        controller = TimerController(this)

        timerView = findViewById(R.id.timerView)
        timerNameText = findViewById(R.id.timerNameText)
        endTimeText = findViewById(R.id.endTimeText)
        renameButton = findViewById(R.id.renameButton)
        startPauseButton = findViewById(R.id.startPauseButton)
        resetButton = findViewById(R.id.resetButton)
        setTimeButton = findViewById(R.id.setTimeButton)
        headerBar = findViewById(R.id.headerBar)
        headerDivider = findViewById(R.id.headerDivider)
        controlsDivider = findViewById(R.id.controlsDivider)
        controlsBar = findViewById(R.id.controlsBar)

        scheduleButton = findViewById(R.id.scheduleButton)
        scheduleButton.setOnClickListener { showSchedulePicker() }

        timerView.setOnClickListener { toggleUi() }

        findViewById<TextView>(R.id.backButton).setOnClickListener { finish() }

        renameButton.setOnClickListener { showRenameDialog() }

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
                    .putExtra(EInkSetTimeActivity.EXTRA_CURRENT_DURATION_MS, timer.selectedDurationMillis),
                REQUEST_SET_TIME,
            )
        }

        val prefs = getSharedPreferences("visual_timer_prefs", MODE_PRIVATE)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var lastBucket = Long.MIN_VALUE
                var prevStatus: TimerStatus? = null
                TimerRepository.state.collect { state ->
                    val timer = state.timers.getOrNull(timerIndex)
                    if (timer == null) {
                        finish()
                        return@collect
                    }

                    val isElapsed = timer.status == TimerStatus.Overtime || timer.status == TimerStatus.Finished
                    val wasElapsed = prevStatus == TimerStatus.Overtime || prevStatus == TimerStatus.Finished
                    if (isElapsed && !wasElapsed) {
                        setUiVisible(true)
                        startBlinking()
                    } else if (!isElapsed && wasElapsed) {
                        stopBlinking()
                    }
                    prevStatus = timer.status

                    val intervalSec = prefs.getString(EInkSettingsActivity.PREF_UPDATE_INTERVAL, EInkSettingsActivity.DEFAULT_UPDATE_INTERVAL)?.toLongOrNull() ?: 15L
                    val bucket = einkTimerBucket(timer, intervalSec)
                    val controlsChanged = timer.status.ordinal != (lastBucket % 10).toInt()
                    if (bucket != lastBucket) {
                        lastBucket = bucket
                        timerView.update(timer)
                    }
                    if (controlsChanged || bucket != lastBucket) {
                        updateControls(timer)
                        updateEndTime(timer)
                        timerNameText.text = timer.activeTimerName.ifBlank { "Timer ${timerIndex + 1}" }
                    }
                }
            }
        }

        applySettings()

        // Force initial draw
        val initialTimer = TimerRepository.getTimer(timerIndex)
        timerView.update(initialTimer)
        updateControls(initialTimer)
        updateEndTime(initialTimer)
        timerNameText.text = initialTimer.activeTimerName.ifBlank { "Timer ${timerIndex + 1}" }
        if (initialTimer.status == TimerStatus.Overtime) startBlinking()
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

    private fun showRenameDialog() {
        val editText = EditText(this).apply {
            setText(TimerRepository.getTimer(timerIndex).activeTimerName)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            hint = "Timer name"
            selectAll()
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("RENAME TIMER")
            .setView(editText)
            .setPositiveButton("SAVE") { _, _ ->
                val name = editText.text.toString().trim()
                controller.dispatch(TimerAction.SetActiveTimerName(name, timerIndex))
                timerNameText.text = name.ifBlank { "Timer ${timerIndex + 1}" }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showSchedulePicker() {
        val timer = TimerRepository.getTimer(timerIndex)
        if (timer.status != TimerStatus.Idle || timer.selectedDurationMillis <= 0L) return
        val now = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        val cal = Calendar.getInstance().apply {
                            set(year, month, day, hour, minute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val epochMs = cal.timeInMillis
                        if (epochMs <= System.currentTimeMillis()) return@TimePickerDialog
                        pendingScheduledEpochMs = epochMs
                        val name = TimerRepository.getTimer(timerIndex).activeTimerName.ifBlank { "Timer ${timerIndex + 1}" }
                        lifecycleScope.launch(Dispatchers.IO) {
                            val zoneId = ZoneId.systemDefault()
                            val localDate = Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate()
                            val localTime = Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalTime()
                            val entity = ScheduledTimerEntity(
                                name = name,
                                type = ScheduledTimerType.OneTime.name,
                                oneTimeDateEpochDay = localDate.toEpochDay(),
                                weekdayMask = 0,
                                startTimeMinutes = localTime.hour * 60 + localTime.minute,
                                timingMode = ScheduledTimerTimingMode.Duration.name,
                                durationMillis = timer.selectedDurationMillis,
                                lastOutcome = ScheduledTimerOutcome.None.name,
                            )
                            ScheduledTimerManager.upsertSchedule(this@EInkActiveTimerActivity, entity)
                        }
                        updateScheduledDisplay(epochMs)
                    },
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE),
                    true,
                ).show()
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun updateScheduledDisplay(epochMs: Long) {
        endTimeText.visibility = View.VISIBLE
        endTimeText.text = "SCHED ~${formatWallClockEndTime(epochMs)}"
    }

    private fun setUiVisible(visible: Boolean) {
        isUiVisible = visible
        val vis = if (visible) View.VISIBLE else View.GONE
        headerBar.visibility = vis
        headerDivider.visibility = vis
        controlsDivider.visibility = vis
        controlsBar.visibility = vis
    }

    private fun toggleUi() {
        setUiVisible(!isUiVisible)
    }

    private fun updateEndTime(timer: TimerInstance) {
        // Show pending schedule label when Idle with a future schedule set
        val scheduledMs = pendingScheduledEpochMs
        if (timer.status == TimerStatus.Idle && scheduledMs != null && scheduledMs > System.currentTimeMillis()) {
            updateScheduledDisplay(scheduledMs)
            return
        }
        // Clear stale schedule if timer left Idle state
        if (timer.status != TimerStatus.Idle) pendingScheduledEpochMs = null

        val show = timer.status != TimerStatus.Finished &&
                timer.status != TimerStatus.Overtime &&
                timer.remainingMillis > 0L
        endTimeText.visibility = if (show) View.VISIBLE else View.INVISIBLE
        if (show) {
            val remainMs = when (timer.status) {
                TimerStatus.Running -> {
                    val targetEnd = timer.targetEndTimeMillis
                        ?: (System.currentTimeMillis() + timer.remainingMillis)
                    (targetEnd - System.currentTimeMillis()).coerceAtLeast(0L)
                }
                else -> timer.remainingMillis
            }
            endTimeText.text = "ENDS ${formatEndTimeFromNow(remainMs, showSeconds = true)}"
        }
    }

    private fun updateControls(timer: TimerInstance) {
        val canSet = timer.status == TimerStatus.Idle || timer.status == TimerStatus.Paused
        setTimeButton.alpha = if (canSet) 1f else 0.3f
        setTimeButton.isEnabled = canSet

        val canSchedule = timer.status == TimerStatus.Idle && timer.selectedDurationMillis > 0L
        scheduleButton.alpha = if (canSchedule) 1f else 0.3f
        scheduleButton.isEnabled = canSchedule

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

        fun einkTimerBucket(timer: TimerInstance, intervalSeconds: Long = 15L): Long {
            val intervalMs = if (timer.status == TimerStatus.Running && timer.remainingMillis < 60_000L) {
                1_000L
            } else {
                intervalSeconds * 1_000L
            }
            return (timer.remainingMillis / intervalMs) * 10L + timer.status.ordinal
        }
    }
}
