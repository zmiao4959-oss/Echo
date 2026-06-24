package com.example.myapplication.llm

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

/**
 * 一条对话消息，对应 OpenAI message 格式。
 */
data class LLMMessage(
    val role: String,       // system | user | assistant | tool
    val content: String,
    @SerializedName("tool_call_id") val toolCallId: String? = null,
    @SerializedName("tool_calls") val toolCalls: List<ToolCall>? = null,
    val name: String? = null       // 工具名（tool 消息）
) {
    data class ToolCall(
        val id: String,
        val type: String = "function",
        val function: FunctionCall
    )

    data class FunctionCall(
        val name: String,
        val arguments: String   // JSON string
    )

    companion object {
        private val gson = Gson()

        /** 将 List<LLMMessage> 序列化为 JSON 字符串（用于 Room 存储） */
        fun listToJson(messages: List<LLMMessage>): String {
            return gson.toJson(messages)
        }

        /** 从 JSON 字符串反序列化 List<LLMMessage> */
        fun listFromJson(json: String): List<LLMMessage> {
            if (json.isBlank()) return emptyList()
            return try {
                val type = object : TypeToken<List<LLMMessage>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
