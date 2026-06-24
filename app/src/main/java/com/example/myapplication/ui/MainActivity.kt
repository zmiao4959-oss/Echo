package com.example.myapplication.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.memory.SessionManager
import com.example.myapplication.schedule.ScheduleEngine
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

        adapter = ConversationAdapter(
            onClick = { chatId ->
                val intent = Intent(this, ChatActivity::class.java)
                intent.putExtra("chat_id", chatId)
                startActivity(intent)
            },
            onLongClick = { sessionId ->
                showDeleteConfirmation(sessionId)
            }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        fabNewChat.setOnClickListener {
            val chatId = "android:${System.currentTimeMillis()}"
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("chat_id", chatId)
            startActivity(intent)
        }

        loadConversations()

        // 恢复定时闹钟
        lifecycleScope.launch {
            ScheduleEngine.rescheduleAll(this@MainActivity)
        }

        // 请求通知权限 (Android 13+)
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        loadConversations()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_NOTIFICATIONS
                )
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_NOTIFICATIONS = 1001
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

    fun openWorkspaceFiles(view: View) {
        startActivity(Intent(this, WorkspaceFilesActivity::class.java))
    }

    fun openSchedules(view: View) {
        startActivity(Intent(this, ScheduleListActivity::class.java))
    }

    private fun showDeleteConfirmation(sessionId: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_conversation_title))
            .setMessage(getString(R.string.delete_conversation_message))
            .setPositiveButton(getString(R.string.delete_confirm)) { _, _ ->
                val app = application as MyApplication
                val sessionsDir = File(app.filesDir, "sessions")
                val sessionManager = SessionManager(
                    saveDir = sessionsDir,
                    maxContextTokens = { app.appConfig.maxContextTokens },
                    compactionKeepMessages = { app.appConfig.compactionKeepMessages }
                )
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { sessionManager.delete(sessionId) }
                    Toast.makeText(this@MainActivity, getString(R.string.conversation_deleted), Toast.LENGTH_SHORT).show()
                    loadConversations()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
