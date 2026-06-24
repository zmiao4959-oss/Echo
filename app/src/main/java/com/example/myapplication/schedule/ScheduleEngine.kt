package com.example.myapplication.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * 定时引擎 — 通过 AlarmManager 调度提醒。
 * 优先使用 setAlarmClock()（无需特殊权限，闹钟级优先级）。
 */
object ScheduleEngine {

    private const val TAG = "ScheduleEngine"
    private const val REQUEST_CODE_BASE = 7000

    /** 检查是否有精确闹钟权限（Android 12+） */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
            return alarmManager.canScheduleExactAlarms()
        }
        return true // API < 31 默认有权限
    }

    /** 跳转到系统精确闹钟权限设置页 */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    /** 为单个任务设置闹钟 */
    fun schedule(context: Context, task: ScheduledTask) {
        if (!task.enabled) {
            Log.d(TAG, "Task ${task.id} is disabled, skipping")
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerTime = calculateNextTrigger(task.hour, task.minute, task.daysOfWeek)
        if (triggerTime <= System.currentTimeMillis()) {
            Log.w(TAG, "Trigger time for ${task.timeFormatted} is in the past, not scheduling")
            return
        }

        val pendingIntent = buildPendingIntent(context, task)

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timeStr = sdf.format(Date(triggerTime))

        try {
            // 优先使用 setAlarmClock：无需 SCHEDULE_EXACT_ALARM 权限，系统最高优先级
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                pendingIntent
            )
            Log.i(TAG, "✅ Scheduled via setAlarmClock: ${task.timeFormatted} → $timeStr (id=${task.id.take(8)})")
        } catch (e: SecurityException) {
            Log.w(TAG, "setAlarmClock failed, trying setExactAndAllowWhileIdle...")
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                Log.i(TAG, "✅ Scheduled via setExactAndAllowWhileIdle: ${task.timeFormatted} → $timeStr")
            } catch (e2: SecurityException) {
                Log.e(TAG, "❌ No permission to schedule exact alarm! User must grant in system settings.", e2)
            }
        }
    }

    /** 取消任务的闹钟 */
    fun cancel(context: Context, task: ScheduledTask) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + task.id.hashCode().and(0x7FFF),
            Intent(context, AlarmReceiver::class.java).apply {
                data = android.net.Uri.parse("claw://schedule/${task.id}")
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
            Log.d(TAG, "Cancelled task ${task.id.take(8)}")
        }
    }

    /** 恢复所有已启用任务的闹钟（启动 / BOOT_COMPLETED 时调用） */
    suspend fun rescheduleAll(context: Context) {
        Log.d(TAG, "Rescheduling all enabled tasks...")
        val tasks = ScheduleStore.listAll()
        var count = 0
        for (task in tasks) {
            if (task.enabled) {
                schedule(context, task)
                count++
            }
        }
        Log.i(TAG, "Rescheduled $count/${tasks.size} tasks")
    }

    private fun buildPendingIntent(context: Context, task: ScheduledTask): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            data = android.net.Uri.parse("claw://schedule/${task.id}")
            putExtra("task_id", task.id)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + task.id.hashCode().and(0x7FFF),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 计算下一次触发时间（Unix 毫秒） */
    internal fun calculateNextTrigger(hour: Int, minute: Int, daysOfWeek: Set<Int>): Long {
        val now = LocalDateTime.now()
        val zoneId = ZoneId.systemDefault()

        fun LocalDateTime.toOurDay(): Int = when (this.dayOfWeek) {
            DayOfWeek.SUNDAY -> 1
            DayOfWeek.MONDAY -> 2
            DayOfWeek.TUESDAY -> 3
            DayOfWeek.WEDNESDAY -> 4
            DayOfWeek.THURSDAY -> 5
            DayOfWeek.FRIDAY -> 6
            DayOfWeek.SATURDAY -> 7
        }

        val base = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)

        for (i in 0 until 14) {
            val checkTime = base.plusDays(i.toLong())
            if (checkTime.toOurDay() in daysOfWeek && checkTime.isAfter(now)) {
                return checkTime.atZone(zoneId).toInstant().toEpochMilli()
            }
        }

        return now.plusDays(1).withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            .atZone(zoneId).toInstant().toEpochMilli()
    }
}
