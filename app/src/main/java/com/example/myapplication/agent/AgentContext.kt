package com.example.myapplication.agent

/**
 * 单次 Agent 调用的上下文。
 */
data class AgentContext(
    val chatId: String,
    val channel: String = "android",
    val accountId: String = "local",
    val userMessage: String = "",
    val enableTTS: Boolean = true,
    val metadata: Map<String, String> = emptyMap()
)
