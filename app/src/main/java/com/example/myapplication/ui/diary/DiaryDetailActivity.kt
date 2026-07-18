package com.example.myapplication.ui.diary

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.ui.ThemedActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.tts.AudioPlayer
import com.example.myapplication.tts.TTSClient
import com.example.myapplication.tts.TTSConfig
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiaryDetailActivity : ThemedActivity() {

    private lateinit var viewModel: DiaryViewModel
    private lateinit var audioPlayer: AudioPlayer

    private var diaryId: String? = null
    private var sourceRecords: List<com.example.myapplication.data.model.LifeRecord> = emptyList()
    private var showingRawRecords = false
    private var renderedRawMode: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary_detail)

        diaryId = intent.getStringExtra("diary_id")
        if (diaryId == null) {
            finish()
            return
        }

        val app = application as MyApplication
        audioPlayer = AudioPlayer(app)

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory(app)
        )[DiaryViewModel::class.java]

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_delete).setOnClickListener { showDeleteConfirmation() }
        findViewById<View>(R.id.btn_regenerate).setOnClickListener { regenerateDiary() }
        findViewById<View>(R.id.btn_read_aloud).setOnClickListener { readAloud() }
        findViewById<View>(R.id.btn_view_diary).setOnClickListener {
            showingRawRecords = false
            renderBodyMode()
        }
        findViewById<View>(R.id.btn_view_raw).setOnClickListener {
            showingRawRecords = true
            renderBodyMode()
        }

        observeViewModel()
        viewModel.loadDiaries()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.diaries.collectLatest { _ ->
                updateDetailView()
            }
        }

        lifecycleScope.launch {
            viewModel.detailSourceRecords.collectLatest { records ->
                sourceRecords = records
                renderBodyMode()
            }
        }

        lifecycleScope.launch {
            viewModel.isGenerating.collectLatest { generating ->
                findViewById<View>(R.id.btn_regenerate).isEnabled = !generating
            }
        }

        lifecycleScope.launch {
            viewModel.statusMessage.collectLatest { msg ->
                if (msg != null) {
                    Toast.makeText(this@DiaryDetailActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateDetailView() {
        val diary = viewModel.diaries.value.find { it.id == diaryId } ?: return

        findViewById<TextView>(R.id.tv_detail_date).text = formatFullDate(diary.date)
        findViewById<TextView>(R.id.tv_detail_title).text = diary.title
        findViewById<TextView>(R.id.tv_detail_mood).text = diary.mood
        findViewById<TextView>(R.id.tv_detail_body).text = diary.diaryText
        findViewById<TextView>(R.id.tv_source_count).text = "基于 ${diary.sourceRecordIds.size} 条生活记录生成"
        viewModel.loadSourceRecords(diary.id)
        renderBodyMode()

        // 仅今日日记可重新生成
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        findViewById<View>(R.id.btn_regenerate).visibility =
            if (diary.date == today) View.VISIBLE else View.GONE

        // 标签
        val chipGroup = findViewById<com.google.android.material.chip.ChipGroup>(R.id.chip_detail_tags)
        chipGroup.removeAllViews()
        for (tag in diary.tags) {
            val chip = Chip(this)
            chip.text = tag
            chip.chipStrokeWidth = 1f
            chip.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            chip.isCheckable = false
            chip.isClickable = false
            chipGroup.addView(chip)
        }
    }

    private fun renderBodyMode() {
        val diaryBody = findViewById<TextView>(R.id.tv_detail_body)
        val rawBody = findViewById<TextView>(R.id.tv_detail_raw_body)
        diaryBody.visibility = if (showingRawRecords) View.GONE else View.VISIBLE
        rawBody.visibility = if (showingRawRecords) View.VISIBLE else View.GONE
        val visibleBody = if (showingRawRecords) rawBody else diaryBody
        if (renderedRawMode != showingRawRecords) {
            visibleBody.animate().cancel()
            visibleBody.alpha = 0f
            visibleBody.translationY = 8f * resources.displayMetrics.density
            visibleBody.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(240L)
                .start()
            renderedRawMode = showingRawRecords
        }
        if (!showingRawRecords) return

        rawBody.text = if (sourceRecords.isEmpty()) {
            "这些来源片段已被删除，整理后的日记仍然保留。"
        } else {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            sourceRecords.joinToString("\n\n") { record ->
                val source = when (record.source) {
                    "voice" -> "语音"
                    "chat" -> "对话"
                    "checkin" -> "问候"
                    else -> "文字"
                }
                "${timeFormat.format(Date(record.createdAt))} · $source\n${record.content}"
            }
        }
    }

    private fun readAloud() {
        val diary = viewModel.diaries.value.find { it.id == diaryId } ?: return
        val config = (application as MyApplication).appConfig
        if (!config.isTTSConfigured) {
            Toast.makeText(this, "TTS 未配置", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                Toast.makeText(this@DiaryDetailActivity, "正在朗读…", Toast.LENGTH_SHORT).show()
                val ttsClient = TTSClient(TTSConfig(
                    apiKey = config.ttsApiKey,
                    resourceId = config.ttsResourceId,
                    speaker = config.ttsSpeaker,
                    url = config.ttsUrl
                ))
                val audio = withContext(Dispatchers.IO) {
                    ttsClient.synthesize("<mood>gentle</mood>${diary.diaryText}")
                }
                audioPlayer.play(audio)
            } catch (e: Exception) {
                Toast.makeText(this@DiaryDetailActivity, "朗读失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun regenerateDiary() {
        if (diaryId == null) return
        viewModel.regenerateDiary(diaryId!!)
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("删除日记")
            .setMessage("确定要删除这篇日记吗？")
            .setPositiveButton("删除") { _, _ ->
                diaryId?.let { id ->
                    viewModel.deleteDiary(id)
                    Toast.makeText(this, "日记已删除", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun formatFullDate(isoDate: String): String {
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(isoDate)
            val sdf = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE)
            parsed?.let { sdf.format(it) } ?: isoDate
        } catch (_: Exception) {
            isoDate
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioPlayer.stop()
    }
}
