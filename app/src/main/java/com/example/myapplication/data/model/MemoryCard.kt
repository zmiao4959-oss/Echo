package com.example.myapplication.data.model

/**
 * 第四层记忆：回忆卡片。
 * 值得以后被重新提起的一句话、一段经历、一次转折。
 */
data class MemoryCard(
    val id: String,
    val createdAt: Long,
    val memoryDate: String,
    val quote: String,
    val note: String,
    val tags: List<String>,
    val mood: String? = null,
    val sourceType: String,        // life_record, diary, chat
    val sourceId: String,
    val pinned: Boolean = false,
    val confidence: Float = 1.0f,  // 0.0 - 1.0
    val status: String = "confirmed"  // "confirmed" | "pending"
)
