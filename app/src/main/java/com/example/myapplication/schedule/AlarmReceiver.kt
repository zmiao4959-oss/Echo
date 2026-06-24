package com.example.myapplication.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 接收 AlarmManager 闹钟广播，启动 ReminderService。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 优先从 extra 获取，退回从 URI 解析
        val taskId = intent.getStringExtra("task_id")
            ?: intent.data?.lastPathSegment
            ?: run {
                Log.w("AlarmReceiver", "No task_id in intent extras or URI")
                return
            }
        Log.i("AlarmReceiver", "⏰ Alarm fired for task: ${taskId.take(8)}")

        val serviceIntent = Intent(context, ReminderService::class.java).apply {
            putExtra("task_id", taskId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
