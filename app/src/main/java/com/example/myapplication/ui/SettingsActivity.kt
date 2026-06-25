package com.example.myapplication.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.config.AppConfig
import com.example.myapplication.data.store.DataExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ThemedActivity() {

    private lateinit var config: AppConfig

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = BackgroundManager.saveCustomImage(this, uri)
            if (path != null) {
                config.backgroundKey = "custom:$path"
                refreshBackgroundSelection()
                applyCurrentBackground()
            } else {
                Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

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

        // TTS 对话语音开关（即时生效，不影响闹钟）
        val switchTts = findViewById<Switch>(R.id.switch_tts)
        switchTts.isChecked = config.ttsEnabled
        switchTts.setOnCheckedChangeListener { _, isChecked ->
            config.ttsEnabled = isChecked
        }

        maxToolRounds.setText(config.maxToolRounds.toString())
        maxContextTokens.setText(config.maxContextTokens.toString())

        // 天气城市
        val weatherCity = findViewById<EditText>(R.id.weather_city)
        weatherCity.setText(config.weatherCity)

        // 背景选择按钮
        findViewById<Button>(R.id.bg_select_1).setOnClickListener {
            config.backgroundKey = "bg_default_1"
            refreshBackgroundSelection()
            applyCurrentBackground()
        }
        findViewById<Button>(R.id.bg_select_2).setOnClickListener {
            config.backgroundKey = "bg_default_2"
            refreshBackgroundSelection()
            applyCurrentBackground()
        }
        findViewById<Button>(R.id.bg_select_3).setOnClickListener {
            config.backgroundKey = "bg_default_3"
            refreshBackgroundSelection()
            applyCurrentBackground()
        }
        findViewById<Button>(R.id.bg_select_custom).setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        refreshBackgroundSelection()
        applyCurrentBackground()

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
            config.weatherCity = weatherCity.text.toString().trim()
            config.ttsUrl = normalizeUrl(ttsUrl.text.toString().trim())
            config.maxToolRounds = maxToolRounds.text.toString().toIntOrNull() ?: 10
            config.maxContextTokens = maxContextTokens.text.toString().toIntOrNull() ?: 32000

            Toast.makeText(this, getString(R.string.toast_config_saved), Toast.LENGTH_SHORT).show()
            finish()
        }

        // 导出按钮
        findViewById<Button>(R.id.btn_export).setOnClickListener {
            Toast.makeText(this, "正在导出…", Toast.LENGTH_SHORT).show()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val zipFile = DataExporter.exportAll(this@SettingsActivity)
                    withContext(Dispatchers.Main) {
                        DataExporter.shareZip(this@SettingsActivity, zipFile)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun refreshBackgroundSelection() {
        val currentKey = config.backgroundKey
        val label = findViewById<TextView>(R.id.bg_current_label)

        // 更新默认按钮的选中态文本
        val btn1 = findViewById<Button>(R.id.bg_select_1)
        val btn2 = findViewById<Button>(R.id.bg_select_2)
        val btn3 = findViewById<Button>(R.id.bg_select_3)

        btn1.text = "默认 1${if (currentKey == "bg_default_1") " ✓" else ""}"
        btn2.text = "默认 2${if (currentKey == "bg_default_2") " ✓" else ""}"
        btn3.text = "默认 3${if (currentKey == "bg_default_3") " ✓" else ""}"

        label.text = when {
            currentKey.startsWith("custom:") -> "当前: 自定义图片"
            currentKey in BackgroundManager.DEFAULT_BG_IDS -> "当前: $currentKey"
            else -> ""
        }
    }

    private fun applyCurrentBackground() {
        BackgroundManager.apply(this, config.backgroundKey)
    }

    /** 自动补全 URL scheme，避免 OkHttp "no scheme" 错误 */
    private fun normalizeUrl(url: String): String {
        if (url.isBlank()) return url
        return if (url.startsWith("http://") || url.startsWith("https://")) url
        else "https://$url"
    }
}
