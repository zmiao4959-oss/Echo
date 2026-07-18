package com.example.myapplication.schedule

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import com.example.myapplication.config.AppConfig
import com.example.myapplication.data.repository.LifeRecordRepository
import com.example.myapplication.policy.GentleRecordReminderPolicy
import com.example.myapplication.ui.MainActivity
import com.example.myapplication.ui.today.QuickRecordRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 每日轻提醒。使用非精确闹钟，不要求用户授予精确闹钟权限。
 */
object GentleRecordReminderScheduler {

    const val ACTION_GENTLE_RECORD_REMINDER =
        "com.example.myapplication.GENTLE_RECORD_REMINDER"

    private const val TAG = "GentleRecordReminder"
    private const val REQUEST_CODE = 7310
    private const val NOTIFICATION_ID = 9101
    private const val CHANNEL_ID = "gentle_record_reminder"
    private val adaptiveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun scheduleNext(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ) {
        val config = AppConfig(context)
        if (!config.gentleRecordReminderEnabled) {
            cancel(context)
            return
        }

        scheduleAt(
            context = context.applicationContext,
            nowMillis = nowMillis,
            hour = config.gentleRecordReminderHour,
            minute = config.gentleRecordReminderMinute,
            timeZone = timeZone
        )

        // 先同步保底排期，再从本地记录中学习时间并替换同一个闹钟。
        adaptiveScope.launch {
            try {
                val preferred = preferredTime(context.applicationContext, nowMillis, timeZone)
                scheduleAt(
                    context = context.applicationContext,
                    nowMillis = nowMillis,
                    hour = preferred.hour,
                    minute = preferred.minute,
                    timeZone = timeZone
                )
            } catch (error: Exception) {
                Log.e(TAG, "Failed to learn preferred reminder time; fallback remains scheduled", error)
            }
        }
    }

    /** Returns the currently learned time for settings/status UI without scheduling an alarm. */
    suspend fun preferredTime(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): GentleRecordReminderPolicy.TimeOfDay {
        val config = AppConfig(context.applicationContext)
        val timestamps = LifeRecordRepository().getAll()
            .asSequence()
            .map { it.createdAt }
            .filter { it in 1..nowMillis }
            .toList()
        return GentleRecordReminderPolicy.preferredReminderTime(
            recordedAtMillis = timestamps,
            fallbackHour = config.gentleRecordReminderHour,
            fallbackMinute = config.gentleRecordReminderMinute,
            timeZone = timeZone
        )
    }

    private fun scheduleAt(
        context: Context,
        nowMillis: Long,
        hour: Int,
        minute: Int,
        timeZone: TimeZone
    ) {
        val triggerAt = GentleRecordReminderPolicy.nextTriggerAtMillis(
            nowMillis = nowMillis,
            hour = hour,
            minute = minute,
            timeZone = timeZone
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = reminderPendingIntent(context)

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent
        )
        Log.d(
            TAG,
            "Next gentle reminder scheduled at ${Date(triggerAt)} ($hour:${minute.toString().padStart(2, '0')})"
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = reminderPendingIntent(context)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    suspend fun handleTrigger(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val appContext = context.applicationContext
        val config = AppConfig(appContext)
        try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis))
            val recordCount = LifeRecordRepository().getByDate(today).size
            if (GentleRecordReminderPolicy.shouldNotify(
                    enabled = config.gentleRecordReminderEnabled,
                    recordCountToday = recordCount
                )
            ) {
                showNotification(appContext, GentleRecordReminderPolicy.messageFor(today))
            } else {
                Log.d(TAG, "Gentle reminder stayed silent; enabled=${config.gentleRecordReminderEnabled}, records=$recordCount")
            }
        } catch (error: Exception) {
            // 读取状态不确定时宁可保持安静，避免误提醒。
            Log.e(TAG, "Failed to evaluate gentle reminder", error)
        } finally {
            scheduleNext(appContext)
        }
    }

    private fun showNotification(context: Context, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Notification permission is unavailable; reminder stayed silent")
            return
        }

        ensureNotificationChannel(context)
        val clickIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                action = QuickRecordRoute.ACTION_OPEN_QUICK_RECORD
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("给今天留一句话")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(clickIntent)
            .addAction(
                R.drawable.ic_today,
                context.getString(R.string.gentle_record_reminder_write_action),
                clickIntent
            )
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "温和记录提醒",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "仅在当天还没有记录时轻轻提醒"
                }
            )
        }
    }

    private fun reminderPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_GENTLE_RECORD_REMINDER
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
