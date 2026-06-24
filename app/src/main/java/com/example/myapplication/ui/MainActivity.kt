package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.memory.SessionManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ConversationAdapter
    private lateinit var fabNewChat: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val app = application as MyApplication

        if (!app.appConfig.isLLMConfigured) {
            Toast.makeText(this, getString(R.string.toast_configure_llm_first), Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        recycler = findViewById(R.id.recycler_conversations)
        fabNewChat = findViewById(R.id.fab_new_chat)

        adapter = ConversationAdapter { chatId ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("chat_id", chatId)
            startActivity(intent)
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        fabNewChat.setOnClickListener {
            val chatId = "android:${System.currentTimeMillis()}"
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("chat_id", chatId)
            startActivity(intent)
        }

        loadConversations()
    }

    override fun onResume() {
        super.onResume()
        loadConversations()
    }

    private fun loadConversations() {
        val app = application as MyApplication
        val sessionsDir = File(app.filesDir, "sessions")
        val sessionManager = SessionManager(
            saveDir = sessionsDir,
            maxContextTokens = { app.appConfig.maxContextTokens },
            compactionKeepMessages = { app.appConfig.compactionKeepMessages }
        )

        lifecycleScope.launch {
            val sessions = withContext(Dispatchers.IO) { sessionManager.getAllSessions() }
            adapter.submitList(sessions)
        }
    }

    fun openSettings(view: View) {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
}
