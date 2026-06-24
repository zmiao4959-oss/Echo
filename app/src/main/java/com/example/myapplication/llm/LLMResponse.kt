package com.example.myapplication.llm

/**
 * 统一的 LLM 响应。
 */
data class LLMResponse(
    val content: String = "",
    val toolCalls: List<LLMMessage.ToolCall> = emptyList(),
    val finishReason: String = "stop",
    val usage: Map<String, Int> = emptyMap()
)
