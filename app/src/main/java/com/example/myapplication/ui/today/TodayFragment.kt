package com.example.myapplication.ui.today

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.MyApplication
import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.R
import com.example.myapplication.ui.CardTextureManager
import com.example.myapplication.ui.PageTextureManager
import com.example.myapplication.ui.ChatActivity
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TodayFragment : Fragment() {

    private lateinit var viewModel: TodayViewModel

    // ── UI ──
    private lateinit var tvDate: TextView
    private lateinit var tvWeather: TextView
    private lateinit var etQuickInput: EditText
    private lateinit var btnVoice: ImageButton
    private lateinit var tvRecordCount: TextView
    private lateinit var recyclerRecords: RecyclerView
    private lateinit var tvEmptyRecords: TextView
    private lateinit var cardChatEntry: MaterialCardView
    private lateinit var tvLastReply: TextView
    private lateinit var btnEnterChat: View
    private lateinit var tvDiaryPreview: TextView
    private lateinit var cardInput: MaterialCardView
    private lateinit var cardAiPreview: MaterialCardView
    private var recordAdapter: TodayRecordAdapter? = null
    private var isRecordsExpanded = false
    private var allRecords: List<LifeRecord> = emptyList()

    // 天气
    private val weatherClient = com.example.myapplication.net.HttpClient.instance.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // 语音录音（MediaRecorder，不依赖任何第三方语音服务）
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentAudioFile: java.io.File? = null
    private var pendingAudioPath: String? = null  // 录音完成但尚未提交的音频路径

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_today, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory(requireActivity().application as MyApplication)
        )[TodayViewModel::class.java]

        bindViews(view)
        setupRecycler()
        setupInput()
        setupChatCard()
        observeViewModel()
        applyCardTextures()
        applyPageTexture()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadToday()
        fetchWeather()
        applyCardTextures()
        applyPageTexture()
    }

    private fun applyPageTexture() {
        val config = (requireActivity().application as MyApplication).appConfig
        val key = config.getPageTextureKey(PageTextureManager.TODAY_PAGE)
        PageTextureManager.apply(requireView(), key)
    }

    private fun applyCardTextures() {
        val config = (requireActivity().application as MyApplication).appConfig

        // 对话互动
        CardTextureManager.apply(cardInput, config.getCardTextureKey(CardTextureManager.CHAT), R.attr.echoSurface)
        CardTextureManager.apply(cardChatEntry, config.getCardTextureKey(CardTextureManager.CHAT), R.attr.echoSurface)
        CardTextureManager.apply(cardAiPreview, config.getCardTextureKey(CardTextureManager.CHAT), R.attr.echoSurfaceVariant)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    // ── View Binding ──

    private fun bindViews(view: View) {
        tvDate = view.findViewById(R.id.tv_today_date)
        tvWeather = view.findViewById(R.id.tv_weather)
        etQuickInput = view.findViewById(R.id.et_quick_input)
        btnVoice = view.findViewById(R.id.btn_voice)
        tvRecordCount = view.findViewById(R.id.tv_record_count)
        recyclerRecords = view.findViewById(R.id.recycler_records)
        tvEmptyRecords = view.findViewById(R.id.tv_empty_records)
        cardInput = view.findViewById(R.id.card_input)
        cardChatEntry = view.findViewById(R.id.card_chat_entry)
        tvLastReply = view.findViewById(R.id.tv_last_reply)
        btnEnterChat = view.findViewById(R.id.btn_enter_chat)
        tvDiaryPreview = view.findViewById(R.id.tv_diary_preview)
        cardAiPreview = view.findViewById(R.id.card_ai_preview)
    }

    // ── RecyclerView ──

    private fun setupRecycler() {
        recordAdapter = TodayRecordAdapter(
            onClick = { record -> showRecordDetail(record) },
            onDelete = { id -> showDeleteConfirmation(id) }
        )
        recyclerRecords.layoutManager = LinearLayoutManager(requireContext())
        recyclerRecords.adapter = recordAdapter
    }

    // ── 输入区 ──

    private fun setupInput() {
        requireView().findViewById<View>(R.id.btn_record).setOnClickListener {
            val text = etQuickInput.text.toString().trim()
            val audio = pendingAudioPath

            if (text.isEmpty() && audio == null) return@setOnClickListener

            if (audio != null) {
                // 有录音：优先保存为语音便签，文字作为备注
                val content = if (text.isNotEmpty()) "[语音] $text" else "[语音]"
                viewModel.addRecord(content, "voice", audio)
                pendingAudioPath = null
                etQuickInput.text.clear()
                etQuickInput.hint = "记录今天的生活片段…"
                btnVoice.clearColorFilter()
                Toast.makeText(requireContext(), "语音便签已保存", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.addRecord(text, "text")
                etQuickInput.text.clear()
                Toast.makeText(requireContext(), "已记录", Toast.LENGTH_SHORT).show()
            }
        }

        btnVoice.setOnClickListener { toggleVoiceRecord() }
    }

    // ── 对话卡片 ──

    private fun setupChatCard() {
        cardChatEntry.setOnClickListener { enterChat() }
        btnEnterChat.setOnClickListener { enterChat() }
    }

    private fun enterChat(newConversation: Boolean = false) {
        val prefs = requireContext().getSharedPreferences("clawspeaker_config", android.content.Context.MODE_PRIVATE)
        val lastId = prefs.getString("last_chat_id", null)
        val chatId = if (newConversation || lastId == null) {
            "android:${System.currentTimeMillis()}"
        } else {
            lastId
        }
        // 记住本次 chatId，下次进来继续
        prefs.edit().putString("last_chat_id", chatId).apply()
        val intent = Intent(requireContext(), ChatActivity::class.java)
        intent.putExtra("chat_id", chatId)
        startActivity(intent)
    }

    // ── ViewModel 观察 ──

    private fun observeViewModel() {
        viewModel.loadToday()

        lifecycleScope.launch {
            viewModel.todayDate.collectLatest { date ->
                val display = formatDisplayDate(date)
                tvDate.text = display
            }
        }

        lifecycleScope.launch {
            viewModel.records.collectLatest { records ->
                allRecords = records
                applyRecordFilter()
            }
        }

        tvRecordCount.setOnClickListener {
            isRecordsExpanded = !isRecordsExpanded
            applyRecordFilter()
        }

        lifecycleScope.launch {
            viewModel.latestSession.collectLatest { session ->
                if (session != null) {
                    val lastAssistant = session.messages.findLast { it.role == "assistant" }
                    if (lastAssistant != null && lastAssistant.content.isNotBlank()) {
                        tvLastReply.text = "最近: ${lastAssistant.content.take(60)}…"
                        tvLastReply.visibility = View.VISIBLE
                    } else {
                        tvLastReply.visibility = View.GONE
                    }
                } else {
                    tvLastReply.visibility = View.GONE
                }
            }
        }
    }

    private fun applyRecordFilter() {
        val count = allRecords.size
        val display = if (!isRecordsExpanded && count > 3) allRecords.take(3) else allRecords
        recordAdapter?.submitList(display)

        tvRecordCount.text = when {
            count > 3 && !isRecordsExpanded -> "查看全部 >"
            count > 3 && isRecordsExpanded -> "收起 <"
            else -> ""
        }

        tvEmptyRecords.visibility = if (count == 0) View.VISIBLE else View.GONE
        recyclerRecords.visibility = if (count == 0) View.GONE else View.VISIBLE

        if (count > 0) {
            tvDiaryPreview.text = getString(R.string.today_diary_ready, count)
        } else {
            tvDiaryPreview.text = getString(R.string.today_diary_empty)
        }
    }

    // ── 查看记录详情 ──

    private fun showRecordDetail(record: LifeRecord) {
        val sdf = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINESE)
        val timeStr = sdf.format(Date(record.createdAt))

        val sourceLabel = when (record.source) {
            "voice" -> "🎙️ 语音"
            "chat" -> "💬 对话"
            "checkin" -> "👋 问候"
            else -> "✏️ 手动记录"
        }

        val sb = StringBuilder()
        sb.appendLine(record.content)
        sb.appendLine()
        sb.appendLine("$sourceLabel  ·  $timeStr")
        record.mood?.let { sb.appendLine("心情: $it") }
        record.tags.takeIf { it.isNotEmpty() }?.let {
            sb.appendLine("标签: ${it.joinToString(", ")}")
        }

        AlertDialog.Builder(requireContext())
            .setMessage(sb.toString().trim())
            .setPositiveButton("关闭", null)
            .show()
    }

    // ── 删除确认 ──

    private fun showDeleteConfirmation(recordId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除记录")
            .setMessage("确定要删除这条生活记录吗？")
            .setPositiveButton(getString(R.string.delete_confirm)) { _, _ ->
                viewModel.deleteRecord(recordId)
                Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ── 日期格式化 ──

    private fun formatDisplayDate(isoDate: String): String {
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(isoDate)
            val sdf = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE)
            parsed?.let { sdf.format(it) } ?: isoDate
        } catch (_: Exception) {
            isoDate
        }
    }

    // ── 天气 ──

    private fun fetchWeather() {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("clawspeaker_config", android.content.Context.MODE_PRIVATE)
        val cacheTime = prefs.getLong("weather_cache_time", 0L)
        val cacheAge = System.currentTimeMillis() - cacheTime
        val app = requireActivity().application as MyApplication
        val currentCity = app.appConfig.weatherCity
        val cachedCity = prefs.getString("weather_cache_city", null)
        val cacheValid = cacheAge in 0..30 * 60 * 1000L
                && cachedCity == currentCity

        if (cacheValid) {
            val desc = prefs.getString("weather_desc_cn", null)
            val info = prefs.getString("weather_info_v2", null)
            if (desc != null && info != null) {
                tvWeather.text = "$desc  $info"
                tvWeather.visibility = View.VISIBLE
                return
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val manualCity = currentCity
                val url = if (manualCity.isNotBlank()) {
                    "https://wttr.in/${java.net.URLEncoder.encode(manualCity, "UTF-8")}?format=j1"
                } else {
                    "https://wttr.in/?format=j1"
                }

                val request = Request.Builder().url(url).build()
                val response = weatherClient.newCall(request).execute()
                val body = response.body?.string()?.trim() ?: return@launch

                val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                val current = json.getAsJsonArray("current_condition")
                    ?.get(0)?.asJsonObject ?: return@launch

                val tempC = current.get("temp_C")?.asString ?: ""
                val descEn = current.getAsJsonArray("weatherDesc")
                    ?.get(0)?.asJsonObject?.get("value")?.asString ?: ""
                val desc = weatherToChinese(descEn)

                val city = if (manualCity.isNotBlank()) {
                    manualCity
                } else {
                    json.getAsJsonArray("nearest_area")
                        ?.get(0)?.asJsonObject
                        ?.getAsJsonArray("areaName")
                        ?.get(0)?.asJsonObject
                        ?.get("value")?.asString ?: ""
                }

                val infoText = buildString {
                    append("${tempC}℃")
                    if (city.isNotEmpty()) append("  $city")
                }

                withContext(Dispatchers.Main) {
                    prefs.edit()
                        .putLong("weather_cache_time", System.currentTimeMillis())
                        .putString("weather_cache_city", currentCity)
                        .putString("weather_desc_cn", desc)
                        .putString("weather_info_v2", infoText)
                        .apply()

                    tvWeather.text = "$desc  $infoText"
                    tvWeather.visibility = View.VISIBLE
                }
            } catch (_: Exception) {
                // 天气获取失败，静默处理
            }
        }
    }

    private fun weatherToChinese(desc: String): String {
        val d = desc.lowercase().trim()
        return when {
            "sunny" in d || "clear" in d -> "晴"
            "partly cloudy" in d -> "多云"
            "cloudy" in d || "overcast" in d -> "阴"
            "rain" in d && "light" in d -> "小雨"
            "rain" in d && "heavy" in d -> "大雨"
            "rain" in d -> "雨"
            "drizzle" in d -> "毛毛雨"
            "thunder" in d -> "雷阵雨"
            "snow" in d && "light" in d -> "小雪"
            "snow" in d && "heavy" in d -> "大雪"
            "snow" in d -> "雪"
            "fog" in d || "mist" in d -> "雾"
            "haze" in d -> "霾"
            else -> desc
        }
    }

    // ── 语音便签（MediaRecorder 录音，无需任何第三方服务）──

    private fun toggleVoiceRecord() {
        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            return
        }

        try {
            val audioDir = java.io.File(requireContext().filesDir, "echo/records/audio")
            audioDir.mkdirs()
            currentAudioFile = java.io.File(audioDir, "voice_${System.currentTimeMillis()}.m4a")

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(currentAudioFile!!.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            btnVoice.setColorFilter(0xFFFF4444.toInt())
            Toast.makeText(requireContext(), "🎙 正在录音…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "录音启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            val audioFile = currentAudioFile
            if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
                // 录音完成，暂存路径，等待用户点击"记录"提交
                pendingAudioPath = audioFile.absolutePath
                btnVoice.setColorFilter(0xFF4CAF50.toInt())  // 绿色 = 录音待提交
                etQuickInput.hint = "语音已录制 · 点「记录」提交（可补充文字）"
                Toast.makeText(requireContext(), "语音已录制 (${formatFileSize(audioFile.length())})，点记录提交", Toast.LENGTH_SHORT).show()
            } else {
                // 录音为空，丢弃
                Toast.makeText(requireContext(), "录音为空，未保存", Toast.LENGTH_SHORT).show()
                btnVoice.clearColorFilter()
                pendingAudioPath = null
            }
            currentAudioFile = null
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "录音保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))}MB"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                Toast.makeText(requireContext(), "需要录音权限才能使用语音输入", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO = 3001
    }
}
