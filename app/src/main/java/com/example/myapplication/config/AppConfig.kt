package com.example.myapplication.config

import android.content.Context
import android.content.SharedPreferences

/**
 * App 全局配置，基于 SharedPreferences。
 * 所有 API 参数通过设置页面填写。
 */
class AppConfig(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("clawspeaker_config", Context.MODE_PRIVATE)

    // ── LLM 配置 ──
    var llmBaseUrl: String
        get() = prefs.getString(KEY_LLM_BASE_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LLM_BASE_URL, value).apply()

    var llmApiKey: String
        get() = prefs.getString(KEY_LLM_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LLM_API_KEY, value).apply()

    var llmModel: String
        get() = prefs.getString(KEY_LLM_MODEL, "deepseek-chat") ?: "deepseek-chat"
        set(value) = prefs.edit().putString(KEY_LLM_MODEL, value).apply()

    var llmTemperature: Float
        get() = prefs.getFloat(KEY_LLM_TEMPERATURE, 0.7f)
        set(value) = prefs.edit().putFloat(KEY_LLM_TEMPERATURE, value).apply()

    var llmMaxTokens: Int
        get() = prefs.getInt(KEY_LLM_MAX_TOKENS, 4096)
        set(value) = prefs.edit().putInt(KEY_LLM_MAX_TOKENS, value).apply()

    // ── Agent 配置 ──
    var maxToolRounds: Int
        get() = prefs.getInt(KEY_MAX_TOOL_ROUNDS, 10)
        set(value) = prefs.edit().putInt(KEY_MAX_TOOL_ROUNDS, value).apply()

    var maxContextTokens: Int
        get() = prefs.getInt(KEY_MAX_CONTEXT_TOKENS, 32000)
        set(value) = prefs.edit().putInt(KEY_MAX_CONTEXT_TOKENS, value).apply()

    var compactionKeepMessages: Int
        get() = prefs.getInt(KEY_COMPACTION_KEEP, 10)
        set(value) = prefs.edit().putInt(KEY_COMPACTION_KEEP, value).apply()

    // ── TTS 配置 ──
    var ttsApiKey: String
        get() = prefs.getString(KEY_TTS_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TTS_API_KEY, value).apply()

    var ttsResourceId: String
        get() = prefs.getString(KEY_TTS_RESOURCE_ID, "seed-tts-2.0") ?: "seed-tts-2.0"
        set(value) = prefs.edit().putString(KEY_TTS_RESOURCE_ID, value).apply()

    var ttsSpeaker: String
        get() = prefs.getString(KEY_TTS_SPEAKER, "zh_female_vv_uranus_bigtts") ?: "zh_female_vv_uranus_bigtts"
        set(value) = prefs.edit().putString(KEY_TTS_SPEAKER, value).apply()

    var ttsUrl: String
        get() = prefs.getString(KEY_TTS_URL, "https://openspeech.bytedance.com/api/v3/tts/unidirectional") ?: ""
        set(value) = prefs.edit().putString(KEY_TTS_URL, value).apply()

    var ttsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TTS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_TTS_ENABLED, value).apply()

    // ── 便捷方法 ──
    val isLLMConfigured: Boolean
        get() = llmBaseUrl.isNotBlank() && llmApiKey.isNotBlank()

    val isTTSConfigured: Boolean
        get() = ttsApiKey.isNotBlank()

    companion object {
        private const val KEY_LLM_BASE_URL = "llm_base_url"
        private const val KEY_LLM_API_KEY = "llm_api_key"
        private const val KEY_LLM_MODEL = "llm_model"
        private const val KEY_LLM_TEMPERATURE = "llm_temperature"
        private const val KEY_LLM_MAX_TOKENS = "llm_max_tokens"
        private const val KEY_MAX_TOOL_ROUNDS = "max_tool_rounds"
        private const val KEY_MAX_CONTEXT_TOKENS = "max_context_tokens"
        private const val KEY_COMPACTION_KEEP = "compaction_keep"
        private const val KEY_TTS_API_KEY = "tts_api_key"
        private const val KEY_TTS_RESOURCE_ID = "tts_resource_id"
        private const val KEY_TTS_SPEAKER = "tts_speaker"
        private const val KEY_TTS_URL = "tts_url"
        private const val KEY_TTS_ENABLED = "tts_enabled"
    }
}
