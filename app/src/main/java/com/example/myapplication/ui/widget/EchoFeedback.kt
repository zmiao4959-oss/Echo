package com.example.myapplication.ui.widget

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import com.example.myapplication.MyApplication
import com.example.myapplication.ui.ThemeManager
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

/** Echo 的统一声音与触觉语言。音色在运行时合成，不依赖系统提示音。 */
object EchoFeedback {
    enum class Kind { CAPTURE, OPEN, CONNECT, COMPLETE, DELETE }

    private val audioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "echo-feedback").apply { isDaemon = true }
    }

    fun play(view: View, kind: Kind) {
        val config = (view.context.applicationContext as? MyApplication)?.appConfig
        if (config?.interactionHapticsEnabled != false) haptic(view, kind)
        if (config?.interactionSoundsEnabled != true) return
        val audioManager = view.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val pan = if (view.rootView.width > 0) {
            ((view.x + view.width / 2f) / view.rootView.width * 2f - 1f).coerceIn(-1f, 1f)
        } else 0f
        val experience = ThemeManager.specFor(config.themeKey).experience
        audioExecutor.execute { synthesize(kind, pan, experience) }
    }

    private fun haptic(view: View, kind: Kind) {
        val enabled = runCatching {
            Settings.System.getInt(
                view.context.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED,
                1
            ) != 0
        }.getOrDefault(true)
        if (!enabled) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && kind != Kind.DELETE) {
            val constant = when (kind) {
                Kind.CAPTURE -> HapticFeedbackConstants.GESTURE_END
                Kind.CONNECT -> HapticFeedbackConstants.CLOCK_TICK
                Kind.COMPLETE -> HapticFeedbackConstants.CONFIRM
                Kind.OPEN -> HapticFeedbackConstants.CONTEXT_CLICK
                Kind.DELETE -> HapticFeedbackConstants.REJECT
            }
            view.performHapticFeedback(constant)
            return
        }

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (view.context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            view.context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = when (kind) {
            Kind.CAPTURE -> longArrayOf(0, 10, 32, 18)
            Kind.CONNECT -> longArrayOf(0, 7, 24, 7)
            Kind.COMPLETE -> longArrayOf(0, 10, 36, 22)
            Kind.DELETE -> longArrayOf(0, 28)
            Kind.OPEN -> longArrayOf(0, 8)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun synthesize(kind: Kind, pan: Float, experience: ThemeManager.Experience) {
        val sampleRate = 22_050
        val duration = when (kind) {
            Kind.CAPTURE -> 0.24
            Kind.CONNECT -> 0.18
            Kind.COMPLETE -> 0.30
            Kind.DELETE -> 0.12
            Kind.OPEN -> 0.14
        }
        val durationFactor = when (experience) {
            ThemeManager.Experience.FILM -> .78
            ThemeManager.Experience.CEDAR, ThemeManager.Experience.PAPER -> 1.08
            ThemeManager.Experience.ORBIT -> 1.18
            else -> 1.0
        }
        val frames = (sampleRate * duration * durationFactor).toInt()
        val pcm = ShortArray(frames * 2)
        val startHz = when (kind) {
            Kind.CAPTURE -> 420.0
            Kind.CONNECT -> 520.0
            Kind.COMPLETE -> 480.0
            Kind.DELETE -> 220.0
            Kind.OPEN -> 360.0
        }
        val endHz = when (kind) {
            Kind.CAPTURE -> 720.0
            Kind.CONNECT -> 660.0
            Kind.COMPLETE -> 820.0
            Kind.DELETE -> 150.0
            Kind.OPEN -> 480.0
        }
        val pitch = when (experience) {
            ThemeManager.Experience.PAPER -> .94
            ThemeManager.Experience.ARCHIVE -> .76
            ThemeManager.Experience.FILM -> .84
            ThemeManager.Experience.CEDAR -> .68
            ThemeManager.Experience.TIDE -> 1.04
            ThemeManager.Experience.ORBIT -> 1.20
            ThemeManager.Experience.GROVE -> .88
            ThemeManager.Experience.INK -> .72
        }
        val leftGain = ((1f - pan) * 0.5f).coerceIn(0.18f, 1f)
        val rightGain = ((1f + pan) * 0.5f).coerceIn(0.18f, 1f)
        var phase = 0.0
        for (frame in 0 until frames) {
            val t = frame.toDouble() / frames
            val hz = (startHz + (endHz - startHz) * t) * pitch
            phase += 2.0 * PI * hz / sampleRate
            val envelope = sin(PI * t).coerceAtLeast(0.0) * (1.0 - t * 0.35)
            val overtone = when (experience) {
                ThemeManager.Experience.ARCHIVE, ThemeManager.Experience.ORBIT -> 2.51
                ThemeManager.Experience.CEDAR, ThemeManager.Experience.INK -> 1.51
                ThemeManager.Experience.FILM -> 1.99
                else -> 2.01
            }
            val shimmer = sin(phase) * 0.72 + sin(phase * overtone) * 0.18
            val value = (shimmer * envelope * 2100).toInt().coerceIn(-32767, 32767)
            pcm[frame * 2] = (value * leftGain).toInt().toShort()
            pcm[frame * 2 + 1] = (value * rightGain).toInt().toShort()
        }
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcm, 0, pcm.size)
            track.setNotificationMarkerPosition(frames)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(audioTrack: AudioTrack) = audioTrack.release()
                override fun onPeriodicNotification(audioTrack: AudioTrack) = Unit
            })
            track.play()
        }
    }
}
