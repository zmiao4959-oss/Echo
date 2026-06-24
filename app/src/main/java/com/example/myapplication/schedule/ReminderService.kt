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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID) ?: run {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startAsForeground()
        scope.launch {
            acquireWakeLock()
            try {
                AlarmReceiver().executeReminder(this@ReminderService, taskId)
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
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
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
        startForeground(AlarmReceiver.NOTIFICATION_ID, notification)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ClawSpeaker:ReminderWakeLock"
        ).apply { acquire(3 * 60 * 1000L) }
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
        private const val TAG = "ReminderService"
    }
}
