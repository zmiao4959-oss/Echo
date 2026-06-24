package com.example.myapplication.llm

import kotlinx.coroutines.flow.Flow

/**
 * LLM Provider 接口。
 */
interface LLMProvider {
    /** 非流式对话 */
    suspend fun chat(
        messages: List<LLMMessage>,
        tools: List<Map<String, Any>>? = null,
        temperature: Float = 0.7f,
        maxTokens: Int = 4096
    ): LLMResponse

    /** 流式对话，返回 Flow<LLMStreamChunk> */
    fun chatStream(
        messages: List<LLMMessage>,
        tools: List<Map<String, Any>>? = null,
        temperature: Float = 0.7f,
        maxTokens: Int = 4096
    ): Flow<LLMStreamChunk>

    /** 是否支持 function calling */
    fun supportsTools(): Boolean = true
}
