package com.fabiantorrestech.visualtimerplus.ui.eink

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fabiantorrestech.visualtimerplus.R
import com.fabiantorrestech.visualtimerplus.notification.TimerNotificationManager
import com.fabiantorrestech.visualtimerplus.overlay.TimerOverlayManager
import com.fabiantorrestech.visualtimerplus.timer.AppState
import com.fabiantorrestech.visualtimerplus.timer.MAX_TIMERS
import com.fabiantorrestech.visualtimerplus.timer.findNextAvailableTimerSlot
import com.fabiantorrestech.visualtimerplus.timer.TimerAction
import com.fabiantorrestech.visualtimerplus.timer.TimerController
import com.fabiantorrestech.visualtimerplus.timer.TimerInstance
import com.fabiantorrestech.visualtimerplus.timer.TimerRepository
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import com.fabiantorrestech.visualtimerplus.util.formatClockTime
import kotlinx.coroutines.launch

class EInkMainActivity : ComponentActivity() {

    private lateinit var controller: TimerController
    private lateinit var adapter: TimerListAdapter
    private lateinit var addButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eink_main)

        controller = TimerController(this)
        TimerOverlayManager.setAppForeground(true)

        val recyclerView = findViewById<RecyclerView>(R.id.timerList)
        addButton = findViewById(R.id.addTimerButton)

        adapter = TimerListAdapter { timerIndex ->
            openTimer(timerIndex)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        addButton.setOnClickListener {
            val state = TimerRepository.getState()
            if (state.findNextAvailableTimerSlot() == null) return@setOnClickListener
            controller.dispatch(TimerAction.AddTimer)
            val newIndex = TimerRepository.getState().timers.lastIndex
            openTimer(newIndex)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var lastBuckets = emptyList<Long>()
                TimerRepository.state.collect { state ->
                    val newBuckets = state.timers.map { timerDisplayBucket(it) }
                    if (newBuckets.size != lastBuckets.size) {
                        adapter.setTimers(state.timers)
                    } else {
                        newBuckets.forEachIndexed { i, bucket ->
                            if (i < lastBuckets.size && bucket != lastBuckets[i]) {
                                adapter.notifyItemChanged(i)
                            }
                        }
                    }
                    lastBuckets = newBuckets
                    updateAddButton(state)
                }
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        TimerOverlayManager.setAppForeground(true)
        TimerRepository.setAppForeground(true)
        adapter.setTimers(TimerRepository.getState().timers)
        updateAddButton(TimerRepository.getState())
    }

    override fun onPause() {
        super.onPause()
        TimerOverlayManager.setAppForeground(false)
        TimerRepository.setAppForeground(false)
    }

    private fun handleIntent(intent: Intent?) {
        val targetIndex = intent?.getIntExtra(TimerNotificationManager.EXTRA_TARGET_TIMER_INDEX, -1) ?: -1
        if (targetIndex >= 0 && targetIndex < TimerRepository.getState().timers.size) {
            openTimer(targetIndex)
        }
    }

    private fun openTimer(index: Int) {
        startActivity(
            Intent(this, EInkActiveTimerActivity::class.java)
                .putExtra(EInkActiveTimerActivity.EXTRA_TIMER_INDEX, index)
        )
    }

    private fun updateAddButton(state: AppState) {
        addButton.isEnabled = state.findNextAvailableTimerSlot() != null
        addButton.alpha = if (addButton.isEnabled) 1f else 0.4f
        val remaining = MAX_TIMERS - state.timers.count { it.status != TimerStatus.Idle }
        addButton.text = if (remaining <= 0) "MAX TIMERS REACHED" else "+ ADD TIMER"
    }
}

private fun timerDisplayBucket(timer: TimerInstance): Long {
    val timeBucket = when {
        timer.status == TimerStatus.Overtime -> timer.remainingMillis / 15_000L
        timer.remainingMillis > 60_000L -> timer.remainingMillis / 60_000L
        else -> timer.remainingMillis / 15_000L
    }
    return timeBucket * 10L + timer.status.ordinal
}

private class TimerListAdapter(
    private val onTimerClick: (Int) -> Unit,
) : RecyclerView.Adapter<TimerListAdapter.ViewHolder>() {

    private val timers = mutableListOf<TimerInstance>()

    fun setTimers(newTimers: List<TimerInstance>) {
        timers.clear()
        timers.addAll(newTimers)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_eink_timer, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val timer = timers.getOrNull(position) ?: return
        holder.bind(timer, position, onTimerClick)
    }

    override fun getItemCount(): Int = timers.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.timerName)
        private val statusText: TextView = itemView.findViewById(R.id.timerStatus)
        private val timeText: TextView = itemView.findViewById(R.id.timerTime)

        fun bind(timer: TimerInstance, index: Int, onClick: (Int) -> Unit) {
            nameText.text = timer.activeTimerName.ifBlank { "Timer ${index + 1}" }
            statusText.text = when (timer.status) {
                TimerStatus.Running -> "RUNNING"
                TimerStatus.Paused -> "PAUSED"
                TimerStatus.Overtime -> "OVERTIME"
                TimerStatus.Finished -> "DONE"
                TimerStatus.Idle -> "IDLE"
            }
            timeText.text = when (timer.status) {
                TimerStatus.Overtime -> "+${timer.displayMillis.formatClockTime()}"
                else -> timer.displayMillis.formatClockTime()
            }
            itemView.setOnClickListener { onClick(index) }
        }
    }
}
