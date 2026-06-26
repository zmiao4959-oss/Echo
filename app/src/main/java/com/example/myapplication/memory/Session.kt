package com.example.myapplication.memory

import com.example.myapplication.llm.LLMMessage
import com.google.gson.Gson
import java.io.File

/**
 * 内存中的会话表示（JSON 文件持久化，和 clawspeaker 原版一致）。
 */
data class Session(
    val sessionId: String,
    val chatId: String,
    val messages: MutableList<LLMMessage> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var lastActive: Long = System.currentTimeMillis(),
    val metadata: MutableMap<String, String> = mutableMapOf()
) {
    fun addMessage(msg: LLMMessage) {
        messages.add(msg)
        lastActive = System.currentTimeMillis()
    }

    /** 对话标题（存储在 metadata["title"]） */
    var title: String
        get() = metadata["title"] ?: "对话"
        set(value) { metadata["title"] = value }

    /** 第一条用户消息前 30 字作为标题 */
    fun autoTitle(): String {
        val firstUser = messages.firstOrNull { it.role == "user" }
        return firstUser?.content?.take(30)?.replace("\n", " ") ?: "新对话"
    }

    /** 简单估算 token 数（4 字符 ≈ 1 token） */
    fun estimateTokens(): Int {
        return messages.sumOf { it.content.length / 4 }
    }

    /** 是否需要压缩上下文 */
    fun shouldCompact(maxContextTokens: Int): Boolean {
        return estimateTokens() > maxContextTokens * 0.8
    }

    /** 序列化为 JSON 字符串 */
    fun toJson(): String {
        val gson = Gson()
        return gson.toJson(SerializableSession(
            sessionId = sessionId,
            chatId = chatId,
            messages = messages.map { msg ->
                mapOf(
                    "role" to msg.role,
                    "content" to msg.content,
                    "tool_call_id" to (msg.toolCallId ?: ""),
                    "tool_calls" to (msg.toolCalls?.map { tc ->
                        mapOf(
                            "id" to tc.id,
                            "type" to tc.type,
                            "function" to mapOf(
                                "name" to tc.function.name,
                                "arguments" to tc.function.arguments
                            )
                        )
                    } ?: emptyList<Map<String, Any>>()),
                    "name" to (msg.name ?: "")
                )
            },
            createdAt = createdAt,
            lastActive = lastActive,
            metadata = metadata
        ))
    }

    companion object {
        private fun deserializeToolCalls(raw: Any?): List<LLMMessage.ToolCall>? {
            if (raw == null) return null
            val list = raw as? List<*> ?: return null
            if (list.isEmpty()) return null
            return list.mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                val func = m["function"] as? Map<*, *> ?: return@mapNotNull null
                LLMMessage.ToolCall(
                    id = m["id"] as? String ?: "",
                    type = m["type"] as? String ?: "function",
                    function = LLMMessage.FunctionCall(
                        name = func["name"] as? String ?: "",
                        arguments = func["arguments"] as? String ?: ""
                    )
                )
            }.ifEmpty { null }
        }

        /** 从 JSON 文件恢复 Session */
        fun fromJsonFile(file: File): Session? {
            return try {
                val gson = Gson()
                val json = file.readText(Charsets.UTF_8)
                val data: SerializableSession = gson.fromJson(json, SerializableSession::class.java)
                Session(
                    sessionId = data.sessionId,
                    chatId = data.chatId,
                    messages = data.messages.map { md ->
                        val tcList = deserializeToolCalls(md["tool_calls"])
                        LLMMessage(
                            role = md["role"] as? String ?: "",
                            content = md["content"] as? String ?: "",
                            toolCallId = (md["tool_call_id"] as? String)?.ifEmpty { null },
                            toolCalls = tcList,
                            name = (md["name"] as? String)?.ifEmpty { null }
                        )
                    }.toMutableList(),
                    createdAt = data.createdAt,
                    lastActive = data.lastActive,
                    metadata = (data.metadata ?: emptyMap()).toMutableMap()
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** 用于 JSON 序列化的中间类 */
data class SerializableSession(
    val sessionId: String,
    val chatId: String,
    val messages: List<Map<String, Any>>,
    val createdAt: Long,
    val lastActive: Long,
    val metadata: Map<String, String>?
)
