package com.example.myapplication.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 开机广播 — 恢复所有定时闹钟。
 */
class BootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d("BootReceiver", "BOOT_COMPLETED — rescheduling tasks")
        GentleRecordReminderScheduler.scheduleNext(context)
        scope.launch {
            ScheduleEngine.rescheduleAll(context)
            PlanScheduler.rescheduleAll(context)
        }
    }
}
