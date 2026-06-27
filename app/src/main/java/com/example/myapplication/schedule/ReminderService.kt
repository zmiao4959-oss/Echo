package com.example.myapplication.schedule

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myapplication.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 承载提醒的短生命周期前台服务。Android 15+ 要求后台音频焦点由前台服务持有，
 * 因此 TTS 合成和播放不能放在 BroadcastReceiver 中执行。
 */
class ReminderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    // 防止同一提醒被重复执行（某些 OEM 会延迟重复投递 AlarmManager 广播，
    // 可能在第一遍播放完成后才送达；冷却期内拒绝重复）
    private val activeReminders = mutableMapOf<String, Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID)
        val planId = intent?.getStringExtra(EXTRA_PLAN_ID)

        if (taskId == null && planId == null) {
            Log.w(TAG, "No task_id or plan_id in intent, stopping")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val dedupKey = taskId ?: planId!!

        // 防重入 + 冷却期：同一提醒完成后 30 秒内拒绝重复
        synchronized(activeReminders) {
            // 顺便清理已过期的 key
            val now = System.currentTimeMillis()
            activeReminders.entries.removeAll { it.value < now }

            val expiry = activeReminders[dedupKey]
            if (expiry != null && now < expiry) {
                Log.w(TAG, "Reminder in cooldown, ignoring duplicate: ${dedupKey.take(8)}")
                stopSelf(startId)
                return START_NOT_STICKY
            }
            activeReminders[dedupKey] = now + COOLDOWN_MS
        }

        startAsForeground()
        scope.launch {
            acquireWakeLock()
            try {
                when {
                    taskId != null -> executeScheduledReminder(taskId)
                    planId != null -> executePlanReminder(planId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Reminder execution failed", e)
            } finally {
                releaseWakeLock()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(false)
                }
                // 不立即移除 key —— 靠冷却期自然过期（COOLDOWN_MS 后不再拦截）
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    /** 处理 ScheduledTask 提醒（原有逻辑） */
    private suspend fun executeScheduledReminder(taskId: String) {
        AlarmReceiver().executeReminder(this@ReminderService, taskId)
    }

    /** 处理 EchoPlan 提醒：生成语音文本 → TTS 播报 */
    private suspend fun executePlanReminder(planId: String) {
        val voiceText = PlanScheduler.buildVoiceText(this@ReminderService, planId)
        if (voiceText.isNullOrBlank()) {
            Log.d(TAG, "No voice text for plan $planId, skipping TTS")
            return
        }
        val appConfig = com.example.myapplication.MyApplication.instance.appConfig
        if (!appConfig.isTTSConfigured) return

        Log.d(TAG, "Playing TTS for plan $planId: ${voiceText.take(60)}...")
        // 复用 AlarmReceiver 的 TTS 播放能力
        AlarmReceiver().tryTtsIfConfigured(this@ReminderService, appConfig, voiceText)
    }

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(AlarmReceiver.CHANNEL_ID) == null) {
                AlarmReceiver().ensureNotificationChannel(this)
            }
        }
        val notification = NotificationCompat.Builder(this, AlarmReceiver.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⏰ 小爪提醒")
            .setContentText("正在生成语音提醒…")
            .setOngoing(true)
            .build()
        startForeground(AlarmReceiver.FOREGROUND_NOTIFICATION_ID, notification)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ClawSpeaker:ReminderWakeLock"
        ).apply { acquire(5 * 60 * 1000L) }  // 5 分钟，给 LLM + TTS 足够时间
    }

    private fun releaseWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) { }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_PLAN_ID = "plan_id"
        private const val COOLDOWN_MS = 30_000L  // 同一提醒的冷却期
        private const val TAG = "ReminderService"
    }
}
