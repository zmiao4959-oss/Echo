package com.example.myapplication.ui

import android.Manifest
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
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatActivity : ThemedActivity() {

    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: ChatAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var inputMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnVoice: ImageButton
    private lateinit var statusText: TextView
    private lateinit var progressLoading: ProgressBar

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val app = application as com.example.myapplication.MyApplication
        BackgroundManager.apply(this, app.appConfig.backgroundKey)

        viewModel = (application as com.example.myapplication.MyApplication)
            .let { app ->
                androidx.lifecycle.ViewModelProvider(
                    this,
                    androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(app)
                )[ChatViewModel::class.java]
            }

        val chatId = intent.getStringExtra("chat_id") ?: viewModel.newChatId()

        recycler = findViewById(R.id.recycler_messages)
        inputMessage = findViewById(R.id.input_message)
        btnSend = findViewById(R.id.btn_send)
        btnVoice = findViewById(R.id.btn_voice)
        statusText = findViewById(R.id.status_text)
        progressLoading = findViewById(R.id.progress_loading)

        adapter = ChatAdapter()
        recycler.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        recycler.adapter = adapter

        // 加载会话
        viewModel.loadSession(chatId)

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

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        viewModel.audioPlayer.stop()
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO = 2001
    }
}
