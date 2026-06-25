package com.example.myapplication.tools

import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.repository.DiaryRepository
import com.example.myapplication.data.repository.LifeRecordRepository
import java.util.UUID

/**
 * Echo 日记工具 —— AI 调用此工具直接创建 DailyDiary。
 */
object EchoDiaryTools {

    private val diaryRepo = DiaryRepository()
    private val recordRepo = LifeRecordRepository()

    fun registerAll() {
        ToolRegistry.register(ToolDefinition(
            name = "generate_daily_diary",
            description = "根据某一天的生活片段生成日记。由 AI 整理 LifeRecord 后调用，直接输出结构化的日记内容。",
            schema = mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "generate_daily_diary",
                    "description" to "根据某一天的生活片段生成日记",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "date" to mapOf("type" to "string", "description" to "日期 yyyy-MM-dd"),
                            "title" to mapOf("type" to "string", "description" to "日记标题"),
                            "summary" to mapOf("type" to "string", "description" to "一句话摘要"),
                            "diaryText" to mapOf("type" to "string", "description" to "完整日记正文"),
                            "mood" to mapOf("type" to "string", "description" to "情绪短语"),
                            "tags" to mapOf(
                                "type" to "array",
                                "items" to mapOf("type" to "string"),
                                "description" to "标签列表 3-6个"
                            ),
                            "sourceRecordIds" to mapOf(
                                "type" to "array",
                                "items" to mapOf("type" to "string"),
                                "description" to "来源 LifeRecord ID 列表"
                            )
                        ),
                        "required" to listOf("date", "title", "summary", "diaryText", "mood", "tags", "sourceRecordIds")
                    )
                )
            ),
            requireApproval = false,
            riskLevel = "low",
            tags = listOf("echo", "diary"),
            executor = { args ->
                val date = args["date"] as? String ?: return@ToolDefinition jsonError("缺少 date")
                val title = args["title"] as? String ?: return@ToolDefinition jsonError("缺少 title")
                val summary = args["summary"] as? String ?: ""
                val diaryText = args["diaryText"] as? String ?: ""
                val mood = args["mood"] as? String ?: ""
                val tags = parseStringList(args["tags"])
                val sourceIds = parseStringList(args["sourceRecordIds"])

                val now = System.currentTimeMillis()
                val diary = DailyDiary(
                    id = UUID.randomUUID().toString(),
                    date = date,
                    title = title,
                    summary = summary,
                    diaryText = diaryText,
                    mood = mood,
                    tags = tags,
                    sourceRecordIds = sourceIds,
                    createdAt = now,
                    updatedAt = now
                )
                diaryRepo.add(diary)
                jsonOk("已生成日记", diary.id)
            }
        ))
    }

    private fun parseStringList(value: Any?): List<String> {
        if (value == null) return emptyList()
        return when (value) {
            is List<*> -> value.mapNotNull { it?.toString() }
            else -> emptyList()
        }
    }
}
