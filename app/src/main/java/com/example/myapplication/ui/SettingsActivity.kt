package com.example.myapplication.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.config.AppConfig

class SettingsActivity : AppCompatActivity() {

    private lateinit var config: AppConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        config = (application as MyApplication).appConfig

        // LLM 配置
        val llmBaseUrl = findViewById<EditText>(R.id.llm_base_url)
        val llmApiKey = findViewById<EditText>(R.id.llm_api_key)
        val llmModel = findViewById<EditText>(R.id.llm_model)
        val llmTemperature = findViewById<EditText>(R.id.llm_temperature)
        val llmMaxTokens = findViewById<EditText>(R.id.llm_max_tokens)

        // TTS 配置
        val ttsApiKey = findViewById<EditText>(R.id.tts_api_key)
        val ttsResourceId = findViewById<EditText>(R.id.tts_resource_id)
        val ttsSpeaker = findViewById<EditText>(R.id.tts_speaker)
        val ttsUrl = findViewById<EditText>(R.id.tts_url)

        // Agent 配置
        val maxToolRounds = findViewById<EditText>(R.id.max_tool_rounds)
        val maxContextTokens = findViewById<EditText>(R.id.max_context_tokens)

        // 加载当前配置
        llmBaseUrl.setText(config.llmBaseUrl)
        llmApiKey.setText(config.llmApiKey)
        llmModel.setText(config.llmModel)
        llmTemperature.setText(config.llmTemperature.toString())
        llmMaxTokens.setText(config.llmMaxTokens.toString())
        ttsApiKey.setText(config.ttsApiKey)
        ttsResourceId.setText(config.ttsResourceId)
        ttsSpeaker.setText(config.ttsSpeaker)
        ttsUrl.setText(config.ttsUrl)
        maxToolRounds.setText(config.maxToolRounds.toString())
        maxContextTokens.setText(config.maxContextTokens.toString())

        // 保存按钮
        findViewById<Button>(R.id.btn_save).setOnClickListener {
            config.llmBaseUrl = normalizeUrl(llmBaseUrl.text.toString().trim())
            config.llmApiKey = llmApiKey.text.toString().trim()
            config.llmModel = llmModel.text.toString().trim()
            config.llmTemperature = llmTemperature.text.toString().toFloatOrNull() ?: 0.7f
            config.llmMaxTokens = llmMaxTokens.text.toString().toIntOrNull() ?: 4096
            config.ttsApiKey = ttsApiKey.text.toString().trim()
            config.ttsResourceId = ttsResourceId.text.toString().trim()
            config.ttsSpeaker = ttsSpeaker.text.toString().trim()
            config.ttsUrl = normalizeUrl(ttsUrl.text.toString().trim())
            config.maxToolRounds = maxToolRounds.text.toString().toIntOrNull() ?: 10
            config.maxContextTokens = maxContextTokens.text.toString().toIntOrNull() ?: 32000

            Toast.makeText(this, getString(R.string.toast_config_saved), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /** 自动补全 URL scheme，避免 OkHttp "no scheme" 错误 */
    private fun normalizeUrl(url: String): String {
        if (url.isBlank()) return url
        return if (url.startsWith("http://") || url.startsWith("https://")) url
        else "https://$url"
    }
}
