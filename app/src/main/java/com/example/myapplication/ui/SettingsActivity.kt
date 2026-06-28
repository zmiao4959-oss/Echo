package com.example.myapplication.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.Manifest
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
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

        // 主动陪伴设置
        val switchCompanion = findViewById<Switch>(R.id.switch_companion)
        switchCompanion.isChecked = config.companionEnabled
        switchCompanion.setOnCheckedChangeListener { _, isChecked -> config.companionEnabled = isChecked }

        val switchCompanionVoice = findViewById<Switch>(R.id.switch_companion_voice)
        switchCompanionVoice.isChecked = config.companionAllowVoice
        switchCompanionVoice.setOnCheckedChangeListener { _, isChecked -> config.companionAllowVoice = isChecked }

        val quietStart = findViewById<EditText>(R.id.companion_quiet_start)
        quietStart.setText(config.companionQuietStart.toString())
        val quietEnd = findViewById<EditText>(R.id.companion_quiet_end)
        quietEnd.setText(config.companionQuietEnd.toString())

        // 检索模式配置
        val spinnerRetrievalMode = findViewById<Spinner>(R.id.spinner_retrieval_mode)
        val retrievalWarning = findViewById<TextView>(R.id.retrieval_warning)
        val retrievalSemanticStatus = findViewById<TextView>(R.id.retrieval_semantic_status)
        val retrievalModes = listOf("rule_only", "hybrid", "semantic_experiment")
        val retrievalLabels = listOf(
            getString(R.string.retrieval_mode_rule_only),
            getString(R.string.retrieval_mode_hybrid),
            getString(R.string.retrieval_mode_semantic_experiment)
        )
        val retrievalAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, retrievalLabels)
        retrievalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRetrievalMode.adapter = retrievalAdapter

        // 设置当前选中的检索模式
        val currentModeIndex = retrievalModes.indexOf(config.retrievalMode).let { if (it < 0) 0 else it }
        spinnerRetrievalMode.setSelection(currentModeIndex)

        // 非 rule_only 模式显示实验警告 + 语义服务状态
        fun updateRetrievalWarning() {
            val selectedMode = retrievalModes[spinnerRetrievalMode.selectedItemPosition]
            val notRuleOnly = selectedMode != "rule_only"
            retrievalWarning.visibility = if (notRuleOnly) android.view.View.VISIBLE else android.view.View.GONE

            // 语义服务状态
            retrievalSemanticStatus.visibility = if (notRuleOnly) android.view.View.VISIBLE else android.view.View.GONE
            if (notRuleOnly) {
                val hasProvider = MyApplication.instance.semanticEngine.embeddingProvider != null
                retrievalSemanticStatus.text = if (hasProvider) {
                    "语义服务: 已配置 (${config.embeddingModel})"
                } else {
                    getString(R.string.retrieval_semantic_no_provider)
                }
            }
        }
        updateRetrievalWarning()
        spinnerRetrievalMode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                updateRetrievalWarning()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Embedding API 配置
        val embeddingApiKey = findViewById<EditText>(R.id.embedding_api_key)
        val embeddingBaseUrl = findViewById<EditText>(R.id.embedding_base_url)
        val embeddingModel = findViewById<EditText>(R.id.embedding_model)
        embeddingApiKey.setText(config.embeddingApiKey)
        embeddingBaseUrl.setText(config.embeddingBaseUrl)
        embeddingModel.setText(config.embeddingModel)

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
            config.companionQuietStart = quietStart.text.toString().toIntOrNull() ?: 23
            config.companionQuietEnd = quietEnd.text.toString().toIntOrNull() ?: 7

            // 保存检索模式
            val selectedMode = retrievalModes[spinnerRetrievalMode.selectedItemPosition]
            config.retrievalMode = selectedMode

            // 保存 Embedding API 配置
            config.embeddingApiKey = embeddingApiKey.text.toString().trim()
            config.embeddingBaseUrl = normalizeUrl(embeddingBaseUrl.text.toString().trim())
            config.embeddingModel = embeddingModel.text.toString().trim()

            // 刷新检索引擎（Embedding Provider + 检索模式即时生效）
            MyApplication.instance.refreshEmbeddingProvider()

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

        // 诊断面板
        refreshDiagnostics()
    }

    private fun refreshDiagnostics() {
        val app = application as MyApplication
        val config = app.appConfig

        findViewById<TextView>(R.id.diag_llm_status).text =
            if (config.isLLMConfigured) "LLM: 已配置 (${config.llmModel} @ ${config.llmBaseUrl.ifBlank { "未设置" }})"
            else "LLM: 未配置 (请填写 API 地址和 Key)"

        findViewById<TextView>(R.id.diag_tts_status).text =
            if (config.isTTSConfigured) "TTS: 已配置 (${config.ttsResourceId}) — 对话语音${if (config.ttsEnabled) "开启" else "关闭"}"
            else "TTS: 未配置"

        findViewById<TextView>(R.id.diag_weather_status).text =
            if (config.weatherCity.isNotBlank()) "天气: 城市=${config.weatherCity}  ${com.example.myapplication.diagnostics.ServiceHealth.summary("Weather")}"
            else "天气: 城市未设置，自动定位中  ${com.example.myapplication.diagnostics.ServiceHealth.summary("Weather")}"

        val notifPerm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) "已授权" else "未授权"
        } else "无需授权"
        val alarmPerm = com.example.myapplication.schedule.ScheduleEngine.canScheduleExact(this).let { if (it) "已授权" else "未授权" }
        findViewById<TextView>(R.id.diag_permissions).text = "通知权限: $notifPerm  |  精确闹钟: $alarmPerm"

        val errorLines = mutableListOf<String>()
        for (svc in com.example.myapplication.diagnostics.ServiceHealth.allServices()) {
            errorLines.add(com.example.myapplication.diagnostics.ServiceHealth.summary(svc))
        }
        val healthSummary = com.example.myapplication.diagnostics.DataHealthChecker.quickSummary()
        findViewById<TextView>(R.id.diag_recent_errors).text =
            (if (errorLines.isEmpty()) "最近错误: 无" else "最近错误:\n${errorLines.joinToString("\n")}") +
            "\n\n数据健康: $healthSummary"

        // Embedding 诊断详情
        val embeddingDiag = findViewById<TextView>(R.id.diag_embedding_status)
        val modeLabel = when (config.retrievalMode) {
            "rule_only" -> "规则检索"
            "hybrid" -> "混合检索"
            "semantic_experiment" -> "语义实验"
            else -> config.retrievalMode
        }
        val providerOk = MyApplication.instance.semanticEngine.embeddingProvider != null
        val embeddingSummary = com.example.myapplication.diagnostics.ServiceHealth.summary("Embedding")
        val lastErr = com.example.myapplication.diagnostics.ServiceHealth.lastErrorMessage("Embedding")
        embeddingDiag.text = buildString {
            append("检索模式: $modeLabel")
            append("\n语义服务: ${if (providerOk) "已配置 (${config.embeddingModel})" else "未配置（无 Embedding Provider）"}")
            append("\nEmbedding API: $embeddingSummary")
            if (lastErr != null) {
                append("\n最近错误: $lastErr")
            }
            if (config.retrievalMode != "rule_only" && !providerOk) {
                append("\n→ 当前自动回退规则检索")
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
