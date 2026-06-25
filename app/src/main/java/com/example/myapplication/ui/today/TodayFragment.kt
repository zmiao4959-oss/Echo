package com.example.myapplication.ui.today

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import com.example.myapplication.ui.ThemeColors
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
    private lateinit var cardTodayStatus: MaterialCardView
    private lateinit var tvStatusSummary: TextView

    private var recordAdapter: TodayRecordAdapter? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

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
        initSpeechRecognizer()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadToday()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }

    // ── View Binding ──

    private fun bindViews(view: View) {
        tvDate = view.findViewById(R.id.tv_today_date)
        etQuickInput = view.findViewById(R.id.et_quick_input)
        btnVoice = view.findViewById(R.id.btn_voice)
        tvRecordCount = view.findViewById(R.id.tv_record_count)
        recyclerRecords = view.findViewById(R.id.recycler_records)
        tvEmptyRecords = view.findViewById(R.id.tv_empty_records)
        cardChatEntry = view.findViewById(R.id.card_chat_entry)
        tvLastReply = view.findViewById(R.id.tv_last_reply)
        btnEnterChat = view.findViewById(R.id.btn_enter_chat)
        tvDiaryPreview = view.findViewById(R.id.tv_diary_preview)
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
            if (text.isNotEmpty()) {
                viewModel.addRecord(text, "text")
                etQuickInput.text.clear()
                Toast.makeText(requireContext(), "已记录", Toast.LENGTH_SHORT).show()
            }
        }

        btnVoice.setOnClickListener { toggleVoiceInput() }
    }

    // ── 对话卡片 ──

    private fun setupChatCard() {
        cardChatEntry.setOnClickListener { enterChat() }
        btnEnterChat.setOnClickListener { enterChat() }
    }

    private fun enterChat() {
        val chatId = "android:${System.currentTimeMillis()}"
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

    // ── 语音输入 ──

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            btnVoice.visibility = View.GONE
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                btnVoice.setColorFilter(ThemeColors.destructive(requireContext()))
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
                btnVoice.clearColorFilter()
            }

            override fun onError(error: Int) {
                isListening = false
                btnVoice.clearColorFilter()
                if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NETWORK -> "网络不可用"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未检测到语音"
                        else -> "语音识别失败 ($error)"
                    }
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                btnVoice.clearColorFilter()
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val current = etQuickInput.text.toString()
                    etQuickInput.setText(if (current.isNotEmpty()) "$current${matches[0]}" else matches[0])
                    etQuickInput.setSelection(etQuickInput.text.length)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun toggleVoiceInput() {
        if (isListening) {
            speechRecognizer?.stopListening()
            return
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            return
        }
        startVoiceInput()
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceInput()
            } else {
                Toast.makeText(requireContext(), "需要录音权限才能使用语音输入", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO = 3001
    }
}
