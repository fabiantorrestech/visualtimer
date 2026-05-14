package com.fabiantorrestech.visualtimerplus.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduledTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val scheduleId = intent.getLongExtra(ScheduledTimerManager.EXTRA_SCHEDULE_ID, -1L)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (scheduleId >= 0L) {
                    ScheduledTimerManager.handleAlarmFire(context.applicationContext, scheduleId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
