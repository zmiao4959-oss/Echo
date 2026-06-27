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
import com.example.myapplication.R
import com.example.myapplication.ui.CardTextureManager
import com.example.myapplication.ui.PageTextureManager
import com.example.myapplication.ui.ChatActivity
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TodayFragment : Fragment() {

    private lateinit var viewModel: TodayViewModel

    // ── UI ──
    private lateinit var tvDate: TextView
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
    private lateinit var cardTodayStatus: MaterialCardView
    private lateinit var tvStatusSummary: TextView

    private var recordAdapter: TodayRecordAdapter? = null

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

        // 生活记录
        CardTextureManager.apply(cardTodayStatus, config.getCardTextureKey(CardTextureManager.LIFE_RECORD), R.attr.echoSurfaceVariant)

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
        cardTodayStatus = view.findViewById(R.id.card_today_status)
        tvStatusSummary = view.findViewById(R.id.tv_status_summary)
    }

    // ── RecyclerView ──

    private fun setupRecycler() {
        recordAdapter = TodayRecordAdapter(
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
                recordAdapter?.submitList(records)
                val count = records.size
                tvRecordCount.text = if (count > 0) "$count 条记录" else ""
                tvEmptyRecords.visibility = if (count == 0) View.VISIBLE else View.GONE
                recyclerRecords.visibility = if (count == 0) View.GONE else View.VISIBLE

                // AI preview
                if (count > 0) {
                    tvDiaryPreview.text = getString(R.string.today_diary_ready, count)
                } else {
                    tvDiaryPreview.text = getString(R.string.today_diary_empty)
                }

                // Status card
                val status = viewModel.getTodayStatusSummary()
                if (status != null) {
                    tvStatusSummary.text = status
                    cardTodayStatus.visibility = View.VISIBLE
                } else {
                    cardTodayStatus.visibility = View.GONE
                }
            }
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
