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

    // ── 主题配置 ──
    var themeKey: String
        get() = prefs.getString(KEY_THEME, "warm_tea") ?: "warm_tea"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    // ── 字体配置 ──
    var fontKey: String
        get() = prefs.getString(KEY_FONT, "default") ?: "default"
        set(value) = prefs.edit().putString(KEY_FONT, value).apply()

    // ── 天气城市 ──
    var weatherCity: String
        get() = prefs.getString(KEY_WEATHER_CITY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEATHER_CITY, value).apply()

    // ── 卡片透明度 ──
    var cardOpacity: Int
        get() = prefs.getInt(KEY_CARD_OPACITY, 30)
        set(value) = prefs.edit().putInt(KEY_CARD_OPACITY, value).apply()

    // ── 检索模式 ──
    var retrievalMode: String
        get() = prefs.getString(KEY_RETRIEVAL_MODE, "rule_only") ?: "rule_only"
        set(value) = prefs.edit().putString(KEY_RETRIEVAL_MODE, value).apply()

    // ── Embedding API 配置 ──
    var embeddingApiKey: String
        get() = prefs.getString(KEY_EMBEDDING_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_EMBEDDING_API_KEY, value).apply()

    var embeddingBaseUrl: String
        get() = prefs.getString(KEY_EMBEDDING_BASE_URL, "https://ark.cn-beijing.volces.com/api/v3") ?: "https://ark.cn-beijing.volces.com/api/v3"
        set(value) = prefs.edit().putString(KEY_EMBEDDING_BASE_URL, value).apply()

    var embeddingModel: String
        get() = prefs.getString(KEY_EMBEDDING_MODEL, "doubao-embedding-vision-251215") ?: "doubao-embedding-vision-251215"
        set(value) = prefs.edit().putString(KEY_EMBEDDING_MODEL, value).apply()

    val isEmbeddingConfigured: Boolean
        get() = embeddingApiKey.isNotBlank()

    // ── 隐私开关 ──
    var remoteSemanticEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMOTE_SEMANTIC, true)
        set(value) = prefs.edit().putBoolean(KEY_REMOTE_SEMANTIC, value).apply()

    var semanticSensitiveFilter: Boolean
        get() = prefs.getBoolean(KEY_SEMANTIC_SENSITIVE_FILTER, true)
        set(value) = prefs.edit().putBoolean(KEY_SEMANTIC_SENSITIVE_FILTER, value).apply()

    // ── 主动陪伴 ──
    var companionEnabled: Boolean
        get() = prefs.getBoolean(KEY_COMPANION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_COMPANION_ENABLED, value).apply()

    var companionQuietStart: Int  // 静默开始小时（0-23）
        get() = prefs.getInt(KEY_COMPANION_QUIET_START, 23)
        set(value) = prefs.edit().putInt(KEY_COMPANION_QUIET_START, value).apply()

    var companionQuietEnd: Int  // 静默结束小时（0-23）
        get() = prefs.getInt(KEY_COMPANION_QUIET_END, 7)
        set(value) = prefs.edit().putInt(KEY_COMPANION_QUIET_END, value).apply()

    var companionAllowVoice: Boolean
        get() = prefs.getBoolean(KEY_COMPANION_ALLOW_VOICE, false)
        set(value) = prefs.edit().putBoolean(KEY_COMPANION_ALLOW_VOICE, value).apply()

    // ── 卡片圆角半径（dp） ──
    var cardCornerRadiusDp: Float
        get() = prefs.getFloat(KEY_CARD_CORNER_RADIUS, 20f)
        set(value) = prefs.edit().putFloat(KEY_CARD_CORNER_RADIUS, value).apply()

    // ── 卡片纹理（按类别） ──
    fun getCardTextureKey(category: String): String =
        prefs.getString("card_texture_$category", "none") ?: "none"

    fun setCardTextureKey(category: String, key: String) {
        prefs.edit().putString("card_texture_$category", key).apply()
    }

    // ── 生活记录分时段纹理 ──
    /** 是否启用分时段纹理（仅 LIFE_RECORD 卡片生效） */
    var lifeRecordUseTimeTexture: Boolean
        get() = prefs.getBoolean(KEY_LIFE_RECORD_USE_TIME_TEXTURE, false)
        set(value) = prefs.edit().putBoolean(KEY_LIFE_RECORD_USE_TIME_TEXTURE, value).apply()

    /** 获取某个时间段的纹理 key，未设置时返回 "none" */
    fun getLifeRecordPeriodTextureKey(period: String): String =
        prefs.getString("card_texture_life_record_$period", "none") ?: "none"

    fun setLifeRecordPeriodTextureKey(period: String, key: String) {
        prefs.edit().putString("card_texture_life_record_$period", key).apply()
    }

    // ── 生活记录文字层分时段纹理（左滑后的文字层背景） ──
    fun getLifeRecordTextLayerPeriodTextureKey(period: String): String =
        prefs.getString("card_texture_life_record_text_$period", "none") ?: "none"

    fun setLifeRecordTextLayerPeriodTextureKey(period: String, key: String) {
        prefs.edit().putString("card_texture_life_record_text_$period", key).apply()
    }

    // ── 页面纹理（四个底栏页） ──
    fun getPageTextureKey(category: String): String =
        prefs.getString("page_texture_$category", "none") ?: "none"

    fun setPageTextureKey(category: String, key: String) {
        prefs.edit().putString("page_texture_$category", key).apply()
    }

    // ── 背景配置 ──
    var backgroundKey: String
        get() = prefs.getString(KEY_BACKGROUND, "bg_default_1") ?: "bg_default_1"
        set(value) = prefs.edit().putString(KEY_BACKGROUND, value).apply()

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
        private const val KEY_THEME = "theme_key"
        private const val KEY_FONT = "font_key"
        private const val KEY_WEATHER_CITY = "weather_city"
        private const val KEY_BACKGROUND = "background_key"
        private const val KEY_CARD_OPACITY = "card_opacity"
        private const val KEY_CARD_CORNER_RADIUS = "card_corner_radius"
        private const val KEY_LIFE_RECORD_USE_TIME_TEXTURE = "life_record_use_time_texture"
        private const val KEY_RETRIEVAL_MODE = "retrieval_mode"
        private const val KEY_EMBEDDING_API_KEY = "embedding_api_key"
        private const val KEY_EMBEDDING_BASE_URL = "embedding_base_url"
        private const val KEY_EMBEDDING_MODEL = "embedding_model"
        private const val KEY_REMOTE_SEMANTIC = "remote_semantic_enabled"
        private const val KEY_SEMANTIC_SENSITIVE_FILTER = "semantic_sensitive_filter"
        private const val KEY_COMPANION_ENABLED = "companion_enabled"
        private const val KEY_COMPANION_QUIET_START = "companion_quiet_start"
        private const val KEY_COMPANION_QUIET_END = "companion_quiet_end"
        private const val KEY_COMPANION_ALLOW_VOICE = "companion_allow_voice"
        private const val KEY_AVATAR_PATH = "avatar_path"
    }

    // ── 头像配置 ──
    var avatarPath: String?
        get() = prefs.getString(KEY_AVATAR_PATH, null)
        set(value) {
            if (value != null) prefs.edit().putString(KEY_AVATAR_PATH, value).apply()
            else prefs.edit().remove(KEY_AVATAR_PATH).apply()
        }
}
