package com.example.myapplication.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.memory.Session
import com.example.myapplication.memory.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ConversationListActivity : ThemedActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var searchInput: EditText
    private lateinit var sessionManager: SessionManager

    private var allSessions: List<Session> = emptyList()

    private val adapter = ConversationAdapter(
        onClick = { chatId -> switchToChat(chatId) },
        onLongClick = { sessionId ->
            lifecycleScope.launch {
                val s = withContext(Dispatchers.IO) {
                    sessionManager.getAllSessions().find { it.sessionId == sessionId }
                }
                s?.let { showManageDialog(it) }
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation_list)

        val app = application as MyApplication
        sessionManager = SessionManager(
            saveDir = File(filesDir, "sessions"),
            maxContextTokens = { app.appConfig.maxContextTokens },
            compactionKeepMessages = { app.appConfig.compactionKeepMessages }
        )

        recycler = findViewById(R.id.recycler_conversations)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        tvEmpty = findViewById(R.id.tv_empty_conversations)
        searchInput = findViewById(R.id.search_conversations)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterSessions(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        findViewById<Button>(R.id.btn_new_chat).setOnClickListener { startNewChat() }

        loadSessions()
    }

    override fun onResume() {
        super.onResume()
        loadSessions()
    }

    private fun loadSessions() {
        lifecycleScope.launch {
            allSessions = withContext(Dispatchers.IO) { sessionManager.getAllSessions() }
            filterSessions(searchInput.text?.toString() ?: "")
        }
    }

    private fun filterSessions(query: String) {
        val filtered = if (query.isBlank()) allSessions
        else allSessions.filter { s ->
            val title = s.title.ifEmpty { s.autoTitle() }
            title.contains(query, ignoreCase = true) ||
            s.messages.any { it.content.contains(query, ignoreCase = true) }
        }
        adapter.submitList(filtered)
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun switchToChat(chatId: String) {
        val prefs = getSharedPreferences("clawspeaker_config", Context.MODE_PRIVATE)
        prefs.edit().putString("last_chat_id", chatId).apply()
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("chat_id", chatId)
        startActivity(intent)
        finish()
    }

    private fun startNewChat() {
        val chatId = "android:${System.currentTimeMillis()}"
        val prefs = getSharedPreferences("clawspeaker_config", Context.MODE_PRIVATE)
        prefs.edit().putString("last_chat_id", chatId).apply()
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("chat_id", chatId)
        startActivity(intent)
        finish()
    }

    private fun showManageDialog(session: Session) {
        AlertDialog.Builder(this)
            .setTitle(session.title.ifEmpty { session.autoTitle() })
            .setItems(arrayOf("重命名", "删除")) { _, which ->
                when (which) {
                    0 -> showRenameDialog(session)
                    1 -> showDeleteDialog(session)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRenameDialog(session: Session) {
        val input = EditText(this).apply {
            setText(session.title.ifEmpty { session.autoTitle() })
            setSingleLine(true)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("重命名")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                lifecycleScope.launch {
                    session.title = input.text.toString().trim()
                    withContext(Dispatchers.IO) { sessionManager.save(session) }
                    loadSessions()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteDialog(session: Session) {
        AlertDialog.Builder(this)
            .setTitle("删除「${session.title.ifEmpty { session.autoTitle() }}」")
            .setMessage("确定要删除这个对话吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { sessionManager.delete(session.sessionId) }
                    val prefs = getSharedPreferences("clawspeaker_config", Context.MODE_PRIVATE)
                    if (prefs.getString("last_chat_id", null) == session.chatId) {
                        prefs.edit().remove("last_chat_id").apply()
                    }
                    loadSessions()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
