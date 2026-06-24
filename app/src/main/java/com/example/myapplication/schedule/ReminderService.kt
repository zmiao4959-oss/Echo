package com.example.myapplication.schedule

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.llm.LLMMessage
import com.example.myapplication.llm.OpenAICompatProvider
import com.example.myapplication.memory.FileStore
import com.example.myapplication.tts.TTSClient
import com.example.myapplication.tts.TTSConfig
import com.example.myapplication.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 提醒前台服务 — LLM 生成提醒文字 + TTS 语音播报 + 通知。
 */
class ReminderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getStringExtra("task_id") ?: run {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()

        // 立即进入前台，显示"生成中"通知
        val generatingNotif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⏰ 小爪提醒")
            .setContentText("正在生成提醒…")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, generatingNotif)

        // 异步执行提醒流程
        scope.launch {
            executeReminder(taskId)
        }

        return START_NOT_STICKY
    }

    private suspend fun executeReminder(taskId: String) {
        acquireWakeLock()

        try {
            // 1. 读取任务
            val tasks = ScheduleStore.listAll()
            val task = tasks.find { it.id == taskId }
            if (task == null) {
                showFallbackNotification("提醒任务不存在")
                return
            }

            val appConfig = MyApplication.instance.appConfig

            // 2. 构建系统提示
            val now = LocalDateTime.now()
            val timeStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            val staticPrompt = FileStore.buildStaticSystemPrompt()
            val systemPrompt = """
$staticPrompt

## Runtime Info
- Current time: $timeStr
- Platform: Android
- Context: This is a scheduled reminder. Generate a friendly, concise reminder message in Chinese based on the user's prompt. Keep it under 200 characters. Be warm and encouraging.
            """.trimIndent()

            // 3. 调用 LLM
            val llmResponse = try {
                val provider = OpenAICompatProvider(
                    apiKey = appConfig.llmApiKey,
                    baseUrl = appConfig.llmBaseUrl,
                    model = appConfig.llmModel
                )
                provider.chat(
                    messages = listOf(
                        LLMMessage(role = "system", content = systemPrompt),
                        LLMMessage(role = "user", content = task.prompt)
                    ),
                    temperature = appConfig.llmTemperature,
                    maxTokens = 200
                )
            } catch (e: Exception) {
                Log.e(TAG, "LLM call failed", e)
                // 降级：直接使用提示词原文
                showFallbackNotification(task.prompt)
                return
            }

            val reminderText = llmResponse.content.ifBlank { task.prompt }

            // 4. 更新通知
            showReminderNotification(reminderText)

            // 5. TTS 语音播报
            if (appConfig.isTTSConfigured && appConfig.ttsEnabled) {
                try {
                    synthesizeAndPlay(appConfig, reminderText)
                } catch (e: Exception) {
                    Log.e(TAG, "TTS failed", e)
                    // TTS 失败不影响通知显示，已经展示了文字提醒
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Reminder execution failed", e)
            showFallbackNotification("提醒生成失败: ${e.message}")
        } finally {
            releaseWakeLock()
            // 保留通知可点击，但移除前台服务状态
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                stopForeground(false)
            }
            stopSelf()
        }
    }

    private fun showReminderNotification(text: String) {
        val clickIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayText = if (text.length > 200) text.take(200) + "…" else text

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⏰ 小爪提醒")
            .setContentText(displayText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(clickIntent)
            .setOngoing(false)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notif)
    }

    private fun showFallbackNotification(text: String) {
        showReminderNotification(text)
    }

    private suspend fun synthesizeAndPlay(appConfig: com.example.myapplication.config.AppConfig, text: String) {
        val ttsClient = TTSClient(TTSConfig(
            apiKey = appConfig.ttsApiKey,
            resourceId = appConfig.ttsResourceId,
            speaker = appConfig.ttsSpeaker,
            url = appConfig.ttsUrl
        ))
        val audio = ttsClient.synthesize(text)

        // 写入临时文件并用 USAGE_ALARM 属性播放
        val tempFile = File(cacheDir, "reminder_${System.currentTimeMillis()}.mp3")
        tempFile.writeBytes(audio)

        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setDataSource(tempFile.absolutePath)
            prepare()
            setOnCompletionListener {
                it.release()
                tempFile.delete()
            }
            setOnErrorListener { mp, _, _ ->
                mp.release()
                tempFile.delete()
                true
            }
        }
        player.start()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ClawSpeaker:ReminderWakeLock"
        ).apply {
            acquire(5 * 60 * 1000L) // 最多持有 5 分钟
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock release failed", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
    }

    companion object {
        const val TAG = "ReminderService"
        const val CHANNEL_ID = "claw_reminder"
        const val NOTIFICATION_ID = 9001

        /** 创建通知渠道（在 Application.onCreate 中调用一次） */
        fun createNotificationChannel(context: Context = MyApplication.instance) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "小爪提醒",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "定时提醒通知"
                    setSound(null, null) // 使用 TTS 语音，不播放系统提示音
                }
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun createNotificationChannel() {
        Companion.createNotificationChannel(this)
    }
}
