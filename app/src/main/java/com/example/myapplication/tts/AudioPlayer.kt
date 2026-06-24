package com.example.myapplication.tts

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 音频播放器 — 封装 MediaPlayer。
 */
class AudioPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var currentFile: File? = null

    /**
     * 播放 MP3 字节数据（先写入临时文件，再用 MediaPlayer 播放）。
     */
    suspend fun play(audioData: ByteArray): Boolean = withContext(Dispatchers.IO) {
        stop()

        return@withContext try {
            val tempFile = File(context.cacheDir, "tts_output_${System.currentTimeMillis()}.mp3")
            tempFile.writeBytes(audioData)
            currentFile = tempFile

            MediaPlayer().also { mp ->
                mediaPlayer = mp
                mp.setDataSource(tempFile.absolutePath)
                mp.prepare()
                mp.start()
                mp.setOnCompletionListener {
                    Log.d("AudioPlayer", "Playback completed")
                    tempFile.delete()
                }
                mp.setOnErrorListener { _, what, extra ->
                    Log.e("AudioPlayer", "Playback error: what=$what extra=$extra")
                    tempFile.delete()
                    false
                }
            }
            true
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to play audio", e)
            false
        }
    }

    /** 停止播放 */
    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
        } catch (_: Exception) { }
    }

    /** 是否正在播放 */
    val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true
}
