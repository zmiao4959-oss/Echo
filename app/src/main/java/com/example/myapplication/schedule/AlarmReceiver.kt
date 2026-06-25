package com.example.myapplication.schedule

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
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
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 闹钟接收器 — 仅拉起前台提醒服务，避免在广播生命周期内执行联网和播放。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Phase 8: EchoPlan 闹钟 → 路由到 PlanScheduler
        val planId = intent.getStringExtra("plan_id")
        if (planId != null && intent.action == "com.example.myapplication.ECHO_PLAN_ALARM") {
            Log.i(TAG, "⏰ EchoPlan alarm: ${planId.take(8)}")
            PlanScheduler.handleTrigger(context, planId)
            return
        }

        val taskId = intent.getStringExtra("task_id")
            ?: intent.data?.lastPathSegment
            ?: run {
                Log.w(TAG, "No task_id in intent")
                return
            }
        Log.i(TAG, "⏰ Alarm fired for task: ${taskId.take(8)}")

        try {
            val serviceIntent = Intent(context, ReminderService::class.java).apply {
                putExtra(ReminderService.EXTRA_TASK_ID, taskId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start reminder foreground service", e)
        }
    }

    internal suspend fun executeReminder(context: Context, taskId: String) {
        ensureNotificationChannel(context)

        // 1. 读取任务
        val tasks = ScheduleStore.listAll()
        val task = tasks.find { it.id == taskId }
        if (task == null) {
            showNotification(context, "提醒任务不存在")
            return
        }

        // 2. 先显示通知（用提示词原文兜底），保证用户一定看到
        showNotification(context, task.prompt)

        val appConfig = MyApplication.instance.appConfig
        if (!appConfig.isLLMConfigured) {
            // LLM 未配置 → 就用原文，尝试 TTS
            tryTtsIfConfigured(context, appConfig, task.prompt)
            return
        }

        // 3. 异步调 LLM（带超时），成功后更新通知
        val now = LocalDateTime.now()
        val timeStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val staticPrompt = FileStore.buildStaticSystemPrompt()
        val systemPrompt = """
$staticPrompt

## Runtime Info
- Current time: $timeStr
- Context: This is a scheduled reminder. Generate a friendly, concise reminder in Chinese based on the user's prompt. Keep it under 200 characters. Be warm.
        """.trimIndent()

        val llmResult = withTimeoutOrNull(7_000L) {
            try {
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
                null
            }
        }

        val reminderText = if (llmResult != null && llmResult.content.isNotBlank()) {
            llmResult.content
        } else {
            Log.w(TAG, "LLM returned empty or timed out, using prompt text as fallback")
            task.prompt
        }

        // 4. 更新通知为 AI 生成的文字
        showNotification(context, reminderText)

        // 5. 尝试 TTS 播报
        tryTtsIfConfigured(context, appConfig, reminderText)
    }

    private fun showNotification(context: Context, text: String) {
        val clickIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayText = if (text.length > 200) text.take(200) + "…" else text

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⏰ 小爪提醒")
            .setContentText(displayText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(clickIntent)
            .setOngoing(false)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notif)
    }

    /**
     * TTS 合成后用 MediaPlayer 播放，显式请求音频焦点并设最大音量。
     * 挂起等待播完，防止进程提前被杀。
     */
    private suspend fun tryTtsIfConfigured(
        context: Context,
        appConfig: com.example.myapplication.config.AppConfig,
        text: String
    ) {
        if (!appConfig.isTTSConfigured) return // 闹钟不受聊天 TTS 开关影响
        try {
            val ttsClient = TTSClient(TTSConfig(
                apiKey = appConfig.ttsApiKey,
                resourceId = appConfig.ttsResourceId,
                speaker = appConfig.ttsSpeaker,
                url = appConfig.ttsUrl
            ))
            val audio = withTimeoutOrNull(10_000L) { ttsClient.synthesize(text) } ?: return

            val audioFile = File(context.filesDir, "reminder_tts.mp3")
            audioFile.writeBytes(audio)
            Log.d(TAG, "TTS audio: ${audio.size} bytes, duration ~${audio.size / 2000}s")

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // 提醒不能走普通媒体流：熄屏、静音/勿扰策略或蓝牙路由下，媒体流可能
            // 显示播放完成但没有可听见的输出。使用系统闹钟通道，并临时请求音频焦点。
            val streamType = AudioManager.STREAM_ALARM
            val maxVol = audioManager.getStreamMaxVolume(streamType)
            val curVol = audioManager.getStreamVolume(streamType)
            Log.d(TAG, "Alarm volume: $curVol/$maxVol")
            audioManager.setStreamVolume(streamType, maxVol, 0)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val focusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener { }
                    .build()
            } else {
                null
            }
            val focusResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.requestAudioFocus(focusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    streamType,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
            // Android 15+ 会拒绝没有前台服务的后台应用申请音频焦点。闹钟本身
            // 仍应尝试通过 USAGE_ALARM 播放；不能因为焦点请求被拒绝而直接跳过。
            val hasAudioFocus = focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!hasAudioFocus) {
                Log.w(TAG, "Audio focus denied; continuing alarm playback without focus")
            }

            // 播放并挂起等待播完
            try {
                kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                    val player = MediaPlayer().apply {
                        setAudioAttributes(audioAttributes)
                        try {
                            setDataSource(audioFile.absolutePath)
                            prepare()
                        } catch (e: Exception) {
                            Log.e(TAG, "MediaPlayer prepare failed: ${e.message}", e)
                            cont.resume(Unit) {}
                            return@suspendCancellableCoroutine
                        }
                        setOnPreparedListener {
                            Log.d(TAG, "MediaPlayer prepared, starting alarm playback...")
                            start()
                        }
                        setOnCompletionListener {
                            Log.d(TAG, "MediaPlayer completed normally")
                            it.release()
                            audioFile.delete()
                            cont.resume(Unit) {}
                        }
                        setOnErrorListener { mp, what, extra ->
                            Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                            mp.release()
                            audioFile.delete()
                            cont.resume(Unit) {}
                            true
                        }
                    }

                    cont.invokeOnCancellation {
                        try { player.stop(); player.release() } catch (_: Exception) {}
                        audioFile.delete()
                    }
                }
            } finally {
                if (hasAudioFocus) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioManager.abandonAudioFocusRequest(focusRequest!!)
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.abandonAudioFocus(null)
                    }
                }
                // 恢复原始音量
                try { audioManager.setStreamVolume(streamType, curVol, 0) } catch (_: Exception) {}
            }

            Log.d(TAG, "TTS playback finished")
        } catch (e: Exception) {
            Log.e(TAG, "TTS failed (notification already shown)", e)
        }
    }

    internal fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(NotificationChannel(
                    CHANNEL_ID, "小爪提醒", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "定时提醒通知"
                    setSound(null, null)
                })
            }
        }
    }

    companion object {
        const val TAG = "AlarmReceiver"
        const val CHANNEL_ID = "claw_reminder"
        const val NOTIFICATION_ID = 9001
    }
}
