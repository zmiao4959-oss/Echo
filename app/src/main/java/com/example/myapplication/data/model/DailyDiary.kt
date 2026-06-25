package com.example.myapplication.data.model

/**
 * 第二层记忆：每日日记。
 * AI 根据一天的 LifeRecord 整理出的每日文本。
 */
data class DailyDiary(
    val id: String,
    val date: String,              // yyyy-MM-dd
    val title: String,
    val summary: String,
    val diaryText: String,
    val mood: String,
    val tags: List<String>,
    val sourceRecordIds: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val generatedBy: String = "ai",
    val version: Int = 1
)
