package com.example.myapplication.data.model

/**
 * 第一层记忆：原始生活片段。
 * 用户每天随手留下的记录，来源包括手动输入、语音、对话、问候回答。
 */
data class LifeRecord(
    val id: String,
    val createdAt: Long,
    val date: String,              // yyyy-MM-dd
    val content: String,
    val source: String,            // text, voice, chat, checkin
    val mood: String? = null,
    val tags: List<String> = emptyList(),
    val importance: Int = 1,       // 1-5
    val linkedPlanId: String? = null,
    val rawConversationId: String? = null,
    val audioPath: String? = null  // 语音便签的音频文件路径
)
