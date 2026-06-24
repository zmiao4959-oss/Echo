package com.example.myapplication.llm

/**
 * 流式输出的一个 chunk。
 */
data class LLMStreamChunk(
    val deltaContent: String = "",
    val deltaToolCalls: List<LLMMessage.ToolCall> = emptyList(),
    val finishReason: String? = null,
    val usage: Map<String, Int> = emptyMap()
)
