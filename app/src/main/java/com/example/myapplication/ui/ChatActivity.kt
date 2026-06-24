package com.example.myapplication.ui

import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: ChatAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var inputMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var statusText: TextView
    private lateinit var progressLoading: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

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

        // 键盘发送
        inputMessage.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                sendMessage()
                true
            } else false
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
        viewModel.audioPlayer.stop()
    }
}
