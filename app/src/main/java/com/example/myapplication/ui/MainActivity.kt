package com.example.myapplication.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ConversationAdapter
    private lateinit var fabNewChat: FloatingActionButton
    private lateinit var weatherBar: View
    private lateinit var weatherIcon: TextView
    private lateinit var weatherInfo: TextView

    private val weatherClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val app = application as MyApplication

        BackgroundManager.apply(this, app.appConfig.backgroundKey)

        if (!app.appConfig.isLLMConfigured) {
            Toast.makeText(this, getString(R.string.toast_configure_llm_first), Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        recycler = findViewById(R.id.recycler_conversations)
        fabNewChat = findViewById(R.id.fab_new_chat)
        weatherBar = findViewById(R.id.weather_bar)
        weatherIcon = findViewById(R.id.weather_icon)
        weatherInfo = findViewById(R.id.weather_info)

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
        val app = application as MyApplication
        BackgroundManager.apply(this, app.appConfig.backgroundKey)
        loadConversations()
        fetchDailyWeather()
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

    /** 获取天气（缓存 30 分钟，超时自动刷新） */
    private fun fetchDailyWeather() {
        val prefs = getSharedPreferences("clawspeaker_config", MODE_PRIVATE)
        val cacheTime = prefs.getLong("weather_cache_time", 0L)
        val cacheAge = System.currentTimeMillis() - cacheTime
        val cacheValid = cacheAge in 0..30 * 60 * 1000L

        // 缓存未过期 → 直接用
        if (cacheValid) {
            val icon = prefs.getString("weather_icon", null) ?: return
            val info = prefs.getString("weather_info", null) ?: return
            weatherIcon.text = icon
            weatherInfo.text = info
            weatherBar.visibility = View.VISIBLE
            return
        }

        // 异步获取天气
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // wttr.in 简单格式: %c=emoji %t=温度 %l=城市
                val request = Request.Builder()
                    .url("https://wttr.in/?format=%c+%t+%l")
                    .build()
                val response = weatherClient.newCall(request).execute()
                val body = response.body?.string()?.trim() ?: return@launch

                // body 格式: "☀️ +25°C Beijing"
                val parts = body.split(" ", limit = 3)
                if (parts.size < 2) return@launch
                val emoji = parts[0]
                val temp = parts.getOrElse(1) { "" }
                val city = parts.getOrElse(2) { "" }

                val infoText = if (city.isNotEmpty()) "$temp  $city" else temp

                // 缓存
                withContext(Dispatchers.Main) {
                    prefs.edit()
                        .putLong("weather_cache_time", System.currentTimeMillis())
                        .putString("weather_icon", emoji)
                        .putString("weather_info", infoText)
                        .apply()

                    weatherIcon.text = emoji
                    weatherInfo.text = infoText
                    weatherBar.visibility = View.VISIBLE
                }
            } catch (_: Exception) {
                // 网络失败静默，不影响主流程
            }
        }
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
