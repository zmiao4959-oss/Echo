package com.example.myapplication.tts

/**
 * TTS 配置。
 */
data class TTSConfig(
    val apiKey: String,
    val resourceId: String = "seed-tts-2.0",
    val speaker: String = "zh_female_vv_uranus_bigtts",
    val url: String = "https://openspeech.bytedance.com/api/v3/tts/unidirectional",
    val uid: String = "clawspeaker_android",
    val timeoutSec: Int = 60,
    val mergeSameMood: Boolean = true,
    val useSectionId: Boolean = true,
    val disableMarkdownFilter: Boolean = true
)
