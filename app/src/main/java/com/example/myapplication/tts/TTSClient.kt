package com.example.myapplication.tts

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * 豆包 TTS 客户端 — 移植自 clawspeaker tts/client.py。
 */
class TTSClient(private val config: TTSConfig) {

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(config.timeoutSec.toLong(), TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val successCodes = setOf(0, 20000000)

    /**
     * 合成音频：解析剧本 → 多段合成 → 拼接 MP3 字节。
     */
    suspend fun synthesize(
        script: String,
        sectionId: String? = null
    ): ByteArray = withContext(Dispatchers.IO) {
        var segments = TTSParser.parseMoodScript(script)
        if (segments.isEmpty()) throw IllegalArgumentException("no speakable segments in script")

        if (config.mergeSameMood) {
            val before = segments.size
            segments = TTSParser.mergeAdjacentSegments(segments)
            if (segments.size < before) {
                Log.i("TTS", "Merged $before -> ${segments.size} segments")
            }
        }

        val resolvedSectionId = if (config.useSectionId && sectionId != null
            && !config.speaker.startsWith("S_")) sectionId else null

        val audioChunks = mutableListOf<ByteArray>()
        for ((i, seg) in segments.withIndex()) {
            Log.i("TTS", "Segment ${i + 1}/${segments.size}: mood=${seg.mood?.take(20)}, text_len=${seg.text.length}")
            val payload = TTSParser.buildPayload(
                seg,
                speaker = config.speaker,
                uid = config.uid,
                sectionId = resolvedSectionId,
                disableMarkdownFilter = config.disableMarkdownFilter
            )
            audioChunks.add(requestAudio(payload))
        }

        val audio = audioChunks.fold(ByteArray(0)) { acc, chunk -> acc + chunk }
        if (audio.isEmpty()) throw RuntimeException("no audio data received")
        audio
    }

    private fun requestAudio(payload: Map<String, Any>): ByteArray {
        val body = gson.toJson(payload).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(config.url)
            .addHeader("X-Api-Key", config.apiKey)
            .addHeader("X-Api-Resource-Id", config.resourceId)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("TTS API error ${response.code}: ${response.body?.string()}")
        }

        val reader = BufferedReader(InputStreamReader(response.body?.byteStream() ?: throw RuntimeException("No response stream")))
        val audioChunks = mutableListOf<ByteArray>()

        reader.useLines { lines ->
            for (line in lines) {
                if (line.isBlank()) continue
                val event = try {
                    JsonParser.parseString(line).asJsonObject
                } catch (e: Exception) { continue }

                val code = event.get("code")?.takeIf { !it.isJsonNull }?.asInt ?: continue
                if (code !in successCodes) {
                    val msg = event.get("message")?.takeIf { !it.isJsonNull }?.asString ?: "unknown"
                    throw RuntimeException("TTS failed: code=$code, message=$msg")
                }
                val data = event.get("data")?.takeIf { !it.isJsonNull }?.asString ?: continue
                audioChunks.add(Base64.decode(data, Base64.DEFAULT))
            }
        }

        response.close()
        return audioChunks.fold(ByteArray(0)) { acc, chunk -> acc + chunk }
    }
}
