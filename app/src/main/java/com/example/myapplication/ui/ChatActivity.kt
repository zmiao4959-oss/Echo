package com.example.myapplication.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.data.repository.LifeRecordRepository
import com.example.myapplication.data.repository.MemoryRepository
import com.example.myapplication.tts.TTSParser
import com.example.myapplication.ui.plan.PlanEditActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ChatActivity : ThemedActivity() {

    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: ChatAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var inputMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnVoice: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var statusText: TextView
    private lateinit var progressLoading: ProgressBar

    private var currentChatId: String = ""
    private var sessionManager = (com.example.myapplication.MyApplication.instance as com.example.myapplication.MyApplication)
        .let { com.example.myapplication.memory.SessionManager(
            java.io.File(it.filesDir, "sessions"),
            { it.appConfig.maxContextTokens },
            { it.appConfig.compactionKeepMessages }
        ) }
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // Keep the existing product behavior (leave chat directly), while also
        // supporting the system back gesture through AndroidX's dispatcher.
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }

        val app = application as com.example.myapplication.MyApplication
        BackgroundManager.apply(this, app.appConfig.backgroundKey)

        viewModel = androidx.lifecycle.ViewModelProvider(
            this,
            androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(app)
        )[ChatViewModel::class.java]

        currentChatId = intent.getStringExtra("chat_id") ?: viewModel.newChatId()

        recycler = findViewById(R.id.recycler_messages)
        inputMessage = findViewById(R.id.input_message)
        btnSend = findViewById(R.id.btn_send)
        btnVoice = findViewById(R.id.btn_voice)
        statusText = findViewById(R.id.status_text)
        progressLoading = findViewById(R.id.progress_loading)

        adapter = ChatAdapter(::showMessageActions)
        recycler.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        recycler.adapter = adapter
        showMessageActionHintOnce()

        // 加载会话
        viewModel.loadSession(currentChatId)

        // 标题栏
        tvTitle = findViewById(R.id.tv_chat_title)
        tvTitle.setOnClickListener { showConversationList() }

        // 新对话按钮
        findViewById<View>(R.id.btn_new_chat).setOnClickListener { startNewChat() }

        // 加载标题
        updateTitle()

        // 观察消息列表
        lifecycleScope.launch {
            viewModel.messages.collectLatest { messages ->
                adapter.submitList(messages) {
                    recycler.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }

        // 观察加载状态
        lifecycleScope.launch {
            viewModel.isLoading.collectLatest { loading ->
                progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
                btnSend.isEnabled = !loading
            }
        }

        // 观察状态消息
        lifecycleScope.launch {
            viewModel.statusMessage.collectLatest { msg ->
                if (msg != null) {
                    statusText.text = msg
                    statusText.visibility = View.VISIBLE
                } else {
                    statusText.visibility = View.GONE
                }
            }
        }

        // 观察记忆引用提示
        val memoryHintView = findViewById<TextView>(R.id.memory_hint)
        lifecycleScope.launch {
            viewModel.memoryHint.collectLatest { hint ->
                if (hint != null) {
                    memoryHintView.text = hint + "  ▸"
                    memoryHintView.visibility = View.VISIBLE
                } else {
                    memoryHintView.visibility = View.GONE
                }
            }
        }
        memoryHintView.setOnClickListener {
            showMemoryRefDetail()
        }

        // 发送按钮
        btnSend.setOnClickListener { sendMessage() }

        // 语音输入按钮
        btnVoice.setOnClickListener { toggleVoiceInput() }

        // 键盘发送
        inputMessage.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                sendMessage()
                true
            } else false
        }

        initSpeechRecognizer()
    }

    private fun showMessageActionHintOnce() {
        val prefs = getSharedPreferences("clawspeaker_config", MODE_PRIVATE)
        if (!prefs.getBoolean("chat_message_action_hint_shown", false)) {
            Toast.makeText(this, "长按任意消息，可转为记录、计划或回忆", Toast.LENGTH_LONG).show()
            prefs.edit().putBoolean("chat_message_action_hint_shown", true).apply()
        }
    }

    private fun showMessageActions(message: ChatMessage) {
        val text = messageDisplayText(message)
        if (text.isBlank()) return
        AlertDialog.Builder(this)
            .setTitle("留下这段内容")
            .setItems(arrayOf("复制", "保存为今日片段", "转为计划", "保存为回忆")) { _, which ->
                when (which) {
                    0 -> copyMessage(text)
                    1 -> saveAsLifeRecord(text)
                    2 -> openAsPlan(text)
                    3 -> showSaveMemoryDialog(message, text)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun messageDisplayText(message: ChatMessage): String =
        if (message.role == "assistant") TTSParser.toDisplayText(message.content) else message.content.trim()

    private fun copyMessage(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Echo 对话", text))
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
    }

    private fun saveAsLifeRecord(text: String) {
        lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val record = LifeRecord(
                id = UUID.randomUUID().toString(),
                createdAt = now,
                date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now)),
                content = text,
                source = "chat",
                rawConversationId = currentChatId
            )
            withContext(Dispatchers.IO) { LifeRecordRepository().add(record) }
            Toast.makeText(this@ChatActivity, "已保存到今日片段", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAsPlan(text: String) {
        val title = text.lineSequence().firstOrNull { it.isNotBlank() }
            ?.trim()?.take(40).orEmpty().ifEmpty { "来自对话的计划" }
        startActivity(Intent(this, PlanEditActivity::class.java).apply {
            putExtra(PlanEditActivity.EXTRA_DRAFT_TITLE, title)
            putExtra(PlanEditActivity.EXTRA_DRAFT_MESSAGE, text)
        })
    }

    private fun showSaveMemoryDialog(message: ChatMessage, text: String) {
        val input = EditText(this).apply {
            setText(text)
            minLines = 3
            maxLines = 8
            setSelection(length())
        }
        AlertDialog.Builder(this)
            .setTitle("保存为回忆")
            .setMessage("可以先删减，只留下以后想再次遇见的部分。")
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val quote = input.text.toString().trim()
                if (quote.isNotEmpty()) saveMemory(message, quote)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun saveMemory(message: ChatMessage, quote: String) {
        lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val card = MemoryCard(
                id = UUID.randomUUID().toString(),
                createdAt = now,
                memoryDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now)),
                quote = quote,
                note = "",
                tags = listOf("对话"),
                sourceType = "chat",
                sourceId = "$currentChatId:${message.id}"
            )
            withContext(Dispatchers.IO) { MemoryRepository().addCard(card) }
            Toast.makeText(this@ChatActivity, "已保存到回忆", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newChatId = intent.getStringExtra("chat_id")
        if (newChatId != null && newChatId != currentChatId) {
            currentChatId = newChatId
            viewModel.loadSession(newChatId)
            updateTitle()
            inputMessage.text.clear()
        }
    }

    private fun updateTitle() {
        lifecycleScope.launch {
            val session = sessionManager.resolveSession(currentChatId)
            val title = session?.title?.ifEmpty { session.autoTitle() } ?: "Echo 对话"
            tvTitle.text = "$title  ▼"
        }
    }

    private fun showConversationList() {
        startActivity(android.content.Intent(this, ConversationListActivity::class.java))
    }

    private fun showManageDialog(session: com.example.myapplication.memory.Session) {
        val title = session.title.ifEmpty { session.autoTitle() }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(arrayOf("切换到该对话", "重命名", "删除")) { _, which ->
                when (which) {
                    0 -> switchToConversation(session.chatId)
                    1 -> showRenameDialog(session)
                    2 -> showDeleteDialog(session)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun switchToConversation(chatId: String) {
        currentChatId = chatId
        getSharedPreferences("clawspeaker_config", MODE_PRIVATE)
            .edit().putString("last_chat_id", chatId).apply()
        viewModel.loadSession(chatId)
        updateTitle()
        Toast.makeText(this, "已切换对话", Toast.LENGTH_SHORT).show()
    }

    private fun showRenameDialog(session: com.example.myapplication.memory.Session) {
        val input = EditText(this).apply {
            setText(session.title.ifEmpty { session.autoTitle() })
            setSingleLine(true)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("重命名对话")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val newName = input.text.toString().trim().ifEmpty { return@setPositiveButton }
                session.title = newName
                lifecycleScope.launch {
                    sessionManager.save(session)
                    updateTitle()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteDialog(session: com.example.myapplication.memory.Session) {
        val title = session.title.ifEmpty { session.autoTitle() }
        AlertDialog.Builder(this)
            .setTitle("删除「$title」")
            .setMessage("确定删除该对话吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    sessionManager.delete(session.sessionId)
                    if (session.chatId == currentChatId) {
                        // 删的是当前对话，创建新会话
                        startNewChat()
                    }
                    updateTitle()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun startNewChat() {
        currentChatId = "android:${System.currentTimeMillis()}"
        getSharedPreferences("clawspeaker_config", MODE_PRIVATE)
            .edit().putString("last_chat_id", currentChatId).apply()
        viewModel.loadSession(currentChatId)
        Toast.makeText(this, "已开启新对话", Toast.LENGTH_SHORT).show()
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            btnVoice.visibility = View.GONE
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                btnVoice.setColorFilter(ThemeColors.destructive(this@ChatActivity)) // 红色表示正在听
                statusText.text = getString(R.string.voice_listening)
                statusText.visibility = View.VISIBLE
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
                btnVoice.clearColorFilter()
                statusText.visibility = View.GONE
            }

            override fun onError(error: Int) {
                isListening = false
                btnVoice.clearColorFilter()
                statusText.visibility = View.GONE
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NETWORK -> "网络不可用"
                    SpeechRecognizer.ERROR_NO_MATCH -> "未识别到语音"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未检测到语音"
                    else -> "语音识别失败 ($error)"
                }
                if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                    Toast.makeText(this@ChatActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                btnVoice.clearColorFilter()
                statusText.visibility = View.GONE
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val current = inputMessage.text.toString()
                    inputMessage.setText(if (current.isNotEmpty()) "$current${matches[0]}" else matches[0])
                    inputMessage.setSelection(inputMessage.text.length)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!partial.isNullOrEmpty()) {
                    statusText.text = partial[0]
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun toggleVoiceInput() {
        if (isListening) {
            speechRecognizer?.stopListening()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
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
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceInput()
            } else {
                Toast.makeText(this, "需要录音权限才能使用语音输入", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendMessage() {
        val text = inputMessage.text.toString().trim()
        if (text.isEmpty()) return
        inputMessage.text.clear()
        viewModel.sendMessage(text)
    }

    override fun onResume() {
        super.onResume()
        updateTitle()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        viewModel.audioPlayer.stop()
    }

    private fun showMemoryRefDetail() {
        val sources = viewModel.memorySources.value
        if (sources.isEmpty()) return

        val scrollView = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
        }

        val repo = com.example.myapplication.data.repository.MemoryRepository()

        for ((index, s) in sources.withIndex()) {
            val icon = when (s.type) {
                "profile" -> "👤"; "memory_card" -> "💬"; "memory_md" -> "📄"
                "life_record" -> "📝"; "diary" -> "📔"; else -> "📌"
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 6.dpToPx(), 0, 6.dpToPx())
            }

            // Header: icon + label + explain
            val headerText = buildString {
                append("$icon ${s.label}")
                if (s.explain.isNotBlank()) append("  ·  ${s.explain}")
            }
            val header = TextView(this).apply {
                text = headerText; textSize = 13f
                setTextColor(0xFF333333.toInt())
            }
            row.addView(header)

            // Snippet
            val snippet = TextView(this).apply {
                text = s.snippet.take(50)
                textSize = 12f; setTextColor(0xFF888888.toInt())
                setPadding(0, 2.dpToPx(), 0, 4.dpToPx())
            }
            row.addView(snippet)

            // Action buttons
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val disableSupported = s.type in listOf("profile", "memory_card", "memory_md")
            val disableBtn = Button(this).apply {
                text = if (disableSupported) "不再使用" else "暂不支持禁用"
                textSize = 11f
                isEnabled = disableSupported
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, 36.dpToPx()
                ).apply { marginEnd = 8.dpToPx() }
            }
            disableBtn.setOnClickListener {
                val btn = disableBtn
                lifecycleScope.launch {
                    val success = disableSource(s, repo)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            header.setTextColor(0xFFAAAAAA.toInt())
                            btn.isEnabled = false
                            btn.text = "已禁用"
                            Toast.makeText(this@ChatActivity, "已禁用: ${s.label}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@ChatActivity, "暂不支持禁用此类型", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            actions.addView(disableBtn)
            row.addView(actions)

            // Divider
            if (index < sources.size - 1) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1.dpToPx()
                    ).apply { topMargin = 4.dpToPx() }
                    setBackgroundColor(0xFFE0E0E0.toInt())
                }
                row.addView(divider)
            }

            container.addView(row)
        }

        scrollView.addView(container)

        AlertDialog.Builder(this)
            .setTitle("本轮参考的记忆")
            .setView(scrollView)
            .setNegativeButton("关闭", null)
            .show()
    }

    /** Disable a memory source. Returns true if successful. */
    private suspend fun disableSource(
        source: com.example.myapplication.memory.MemoryContextBuilder.MemorySource,
        repo: com.example.myapplication.data.repository.MemoryRepository
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            when (source.type) {
                "profile" -> {
                    val profiles = repo.getAllProfiles()
                    val match = profiles.find { it.id == source.sourceId }
                    if (match != null) {
                        val result = com.example.myapplication.policy.MemoryGovernanceService.disableProfile(match)
                        repo.upsertProfile(result.updated!!)
                        true
                    } else false
                }
                "memory_card" -> {
                    val cards = repo.getAllCards()
                    val match = cards.find { it.id == source.sourceId }
                    if (match != null) {
                        val result = com.example.myapplication.policy.MemoryGovernanceService.disableCard(match)
                        repo.updateCard(result.updated!!)
                        true
                    } else false
                }
                "memory_md" -> {
                    val md = com.example.myapplication.memory.FileStore.readWorkspaceFile("MEMORY.md")
                    val snippet = source.snippet
                    // Find the line in MEMORY.md that matches this snippet
                    val lines = md.split("\n")
                    val targetLine = lines.find { it.trim().contains(snippet) }
                    if (targetLine != null) {
                        val updated = com.example.myapplication.memory.MemoryMdParser.moveFact(
                            md, targetLine,
                            com.example.myapplication.memory.MemoryMdParser.SECTION_CONFIRMED,
                            com.example.myapplication.memory.MemoryMdParser.SECTION_DISABLED
                        )
                        if (updated != md) {
                            com.example.myapplication.memory.FileStore.writeWorkspaceFile("MEMORY.md", updated)
                            true
                        } else false
                    } else false
                }
                "life_record", "diary" -> false
                else -> false
            }
        } catch (_: Exception) { false }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_RECORD_AUDIO = 2001
    }
}
