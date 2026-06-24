package com.example.myapplication.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.agent.Agent
import com.example.myapplication.agent.AgentContext
import com.example.myapplication.agent.AgentStreamEvent
import com.example.myapplication.memory.Session
import com.example.myapplication.memory.SessionManager
import com.example.myapplication.tts.AudioPlayer
import com.example.myapplication.tts.TTSClient
import com.example.myapplication.tts.TTSConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 聊天消息的 UI 表示。
 */
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String,               // user | assistant | system | tool
    val content: String,
    val isStreaming: Boolean = false,
    val toolCalls: List<String> = emptyList(),
    val toolResults: List<Pair<String, String>> = emptyList()
)

/**
 * 聊天 ViewModel — 管理对话状态。
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MyApplication

    private val sessionsDir = File(app.filesDir, "sessions")
    private val sessionManager = SessionManager(
        saveDir = sessionsDir,
        maxContextTokens = { app.appConfig.maxContextTokens },
        compactionKeepMessages = { app.appConfig.compactionKeepMessages }
    )

    private val appStr = application.resources
    val agent = Agent(
        sessionManager = sessionManager,
        statusCompacting = appStr.getString(R.string.status_compacting),
        statusExecutingTools = appStr.getString(R.string.status_executing_tools),
        statusMaxRounds = appStr.getString(R.string.status_max_rounds),
        statusErrorPrefix = appStr.getString(R.string.status_error_prefix)
    )
    val audioPlayer = AudioPlayer(application)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var currentSession: Session? = null
    private var currentChatId: String? = null

    /** 加载或创建会话 */
    fun loadSession(chatId: String) {
        currentChatId = chatId
        viewModelScope.launch(Dispatchers.IO) {
            val session = sessionManager.resolveSession(chatId)
                ?: sessionManager.getOrCreate(chatId)
            currentSession = session

            val msgs = session.messages
                .filter { it.role == "user" || it.role == "assistant" }
                .map { msg ->
                    ChatMessage(role = msg.role, content = msg.content)
                }
            _messages.value = msgs
        }
    }

    /** 发送消息 */
    fun sendMessage(text: String) {
        val chatId = currentChatId ?: return
        if (text.isBlank()) return

        // 确保 session 存在
        if (currentSession == null) {
            viewModelScope.launch(Dispatchers.IO) {
                currentSession = sessionManager.getOrCreate(chatId)
            }
        }

        val userMsg = ChatMessage(role = "user", content = text)
        _messages.value = _messages.value + userMsg

        val aiMsg = ChatMessage(role = "assistant", content = "", isStreaming = true)
        _messages.value = _messages.value + aiMsg

        _isLoading.value = true
        _statusMessage.value = null

        viewModelScope.launch {
            val context = AgentContext(
                chatId = chatId,
                userMessage = text,
                enableTTS = app.appConfig.ttsEnabled
            )

            var currentContent = ""
            val toolCalls = mutableListOf<String>()
            val toolResults = mutableListOf<Pair<String, String>>()
            var ttsText: String? = null
            var hasError = false

            agent.processMessageStream(context).collect { event ->
                when (event) {
                    is AgentStreamEvent.TextDelta -> {
                        currentContent += event.text
                        updateLastAiMessage(
                            ChatMessage(role = "assistant", content = currentContent,
                                isStreaming = true, toolCalls = toolCalls.toList(),
                                toolResults = toolResults.toList())
                        )
                    }
                    is AgentStreamEvent.Status -> {
                        _statusMessage.value = event.message
                    }
                    is AgentStreamEvent.ToolCallStart -> {
                        toolCalls.add(event.toolName)
                        updateLastAiMessage(
                            ChatMessage(role = "assistant", content = currentContent,
                                isStreaming = true, toolCalls = toolCalls.toList(),
                                toolResults = toolResults.toList())
                        )
                    }
                    is AgentStreamEvent.ToolCallResult -> {
                        toolResults.add(event.toolName to event.result.take(200))
                        updateLastAiMessage(
                            ChatMessage(role = "assistant", content = currentContent,
                                isStreaming = true, toolCalls = toolCalls.toList(),
                                toolResults = toolResults.toList())
                        )
                    }
                    is AgentStreamEvent.TTSHint -> {
                        ttsText = event.text
                        Log.d("TTS", "TTSHint received: text_len=${event.text.length}")
                    }
                    is AgentStreamEvent.Error -> {
                        hasError = true
                        _statusMessage.value = event.message
                        updateLastAiMessage(
                            ChatMessage(role = "assistant",
                                content = currentContent.ifEmpty { event.message },
                                isStreaming = false)
                        )
                    }
                    is AgentStreamEvent.Done -> {
                        // 出错且无有效内容时保留错误消息，避免被空白覆盖
                        if (!hasError || currentContent.isNotEmpty()) {
                            updateLastAiMessage(
                                ChatMessage(role = "assistant", content = currentContent,
                                    isStreaming = false, toolCalls = toolCalls.toList(),
                                    toolResults = toolResults.toList())
                            )
                        }
                        Log.d("TTS", "Done: ttsText=${ttsText != null} ttsEnabled=${app.appConfig.ttsEnabled} ttsConfigured=${app.appConfig.isTTSConfigured}")
                        if (ttsText != null && app.appConfig.ttsEnabled && app.appConfig.isTTSConfigured) {
                            Log.d("TTS", "Triggering TTS synthesis...")
                            synthesizeAndPlay(ttsText!!)
                        }
                    }
                }
            }

            _isLoading.value = false
            _statusMessage.value = null
        }
    }

    private fun updateLastAiMessage(msg: ChatMessage) {
        val msgs = _messages.value.toMutableList()
        val lastIdx = msgs.indexOfLast { it.role == "assistant" }
        if (lastIdx >= 0) {
            msgs[lastIdx] = msg
            _messages.value = msgs
        }
    }

    private suspend fun synthesizeAndPlay(text: String) {
        try {
            Log.d("TTS", "synthesizeAndPlay: text_len=${text.length}")
            val config = app.appConfig
            val ttsClient = TTSClient(TTSConfig(
                apiKey = config.ttsApiKey,
                resourceId = config.ttsResourceId,
                speaker = config.ttsSpeaker,
                url = config.ttsUrl
            ))
            val audio = withContext(Dispatchers.IO) { ttsClient.synthesize(text) }
            Log.d("TTS", "Synthesis done: audio_bytes=${audio.size}, playing...")
            audioPlayer.play(audio)
        } catch (e: Exception) {
            Log.e("TTS", "TTS failed: ${e.message}", e)
            _statusMessage.value = appStr.getString(R.string.status_tts_failed_prefix) + e.message
        }
    }

    suspend fun getAllSessions(): List<Session> = sessionManager.getAllSessions()

    suspend fun deleteSession(sessionId: String) {
        sessionManager.delete(sessionId)
    }

    fun newChatId(): String = "android:${System.currentTimeMillis()}"

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
    }
}
