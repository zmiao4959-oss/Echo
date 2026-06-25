package com.example.myapplication.tools

import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.data.repository.DiaryRepository
import com.example.myapplication.data.repository.LifeRecordRepository
import com.example.myapplication.data.repository.MemoryRepository
import java.util.UUID

/**
 * Echo 记忆工具 —— 搜索记忆 / 创建回忆卡片。
 */
object EchoMemoryTools {

    private val memoryRepo = MemoryRepository()
    private val recordRepo = LifeRecordRepository()
    private val diaryRepo = DiaryRepository()

    fun registerAll() {
        // ── search_memory ──
        ToolRegistry.register(ToolDefinition(
            name = "search_memory",
            description = "搜索用户过去的记录、日记、回忆卡片和长期画像。当用户问「之前」「过去」「我记得」等问题时调用。",
            schema = mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "search_memory",
                    "description" to "搜索用户过去的记录、日记和回忆",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "query" to mapOf("type" to "string", "description" to "搜索关键词"),
                            "dateFrom" to mapOf("type" to "string", "description" to "开始日期 yyyy-MM-dd（可选）"),
                            "dateTo" to mapOf("type" to "string", "description" to "结束日期 yyyy-MM-dd（可选）"),
                            "tags" to mapOf(
                                "type" to "array",
                                "items" to mapOf("type" to "string"),
                                "description" to "按标签过滤（可选）"
                            ),
                            "limit" to mapOf("type" to "integer", "description" to "返回条数上限（可选，默认5）")
                        ),
                        "required" to listOf("query")
                    )
                )
            ),
            requireApproval = false,
            riskLevel = "low",
            tags = listOf("echo", "memory"),
            executor = { args ->
                val query = args["query"] as? String
                    ?: return@ToolDefinition jsonError("缺少 query")
                val limit = (args["limit"] as? Double)?.toInt() ?: 5

                val result = memoryRepo.searchAll(query, recordRepo, diaryRepo)

                // 构建自然语言摘要
                val sb = StringBuilder()
                val total = result.lifeRecords.size + result.diaries.size +
                        result.memoryCards.size + result.profileMemories.size

                if (total == 0) {
                    return@ToolDefinition jsonOk("没有找到与「$query」相关的记忆。", null)
                }

                sb.appendLine("找到 $total 条与「$query」相关的内容：")

                if (result.lifeRecords.isNotEmpty()) {
                    sb.appendLine("\n【生活片段】")
                    result.lifeRecords.take(limit).forEach { r ->
                        sb.appendLine("- [${r.date}] ${r.content.take(100)}")
                    }
                }
                if (result.diaries.isNotEmpty()) {
                    sb.appendLine("\n【日记】")
                    result.diaries.take(limit).forEach { d ->
                        sb.appendLine("- [${d.date}] ${d.title}: ${d.summary.take(100)}")
                    }
                }
                if (result.memoryCards.isNotEmpty()) {
                    sb.appendLine("\n【回忆卡片】")
                    result.memoryCards.take(limit).forEach { c ->
                        sb.appendLine("- [${c.memoryDate}] ${c.quote.take(100)}")
                    }
                }
                if (result.profileMemories.isNotEmpty()) {
                    sb.appendLine("\n【长期记忆】")
                    result.profileMemories.take(limit).forEach { p ->
                        sb.appendLine("- ${p.value}")
                    }
                }

                sb.appendLine("\n你可以用这些结果来自然回应用户，不要逐条复述。")
                sb.toString()
            }
        ))

        // ── create_memory_card ──
        ToolRegistry.register(ToolDefinition(
            name = "create_memory_card",
            description = "从生活记录、日记或对话中提取一张值得以后回看的记忆卡片。当用户表达重要转折、感悟、或说「保存这句话」「记住这个」时调用。每次最多创建 1 张，不要批量创建。",
            schema = mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "create_memory_card",
                    "description" to "从生活记录、日记或对话中提取一张值得以后回看的记忆卡片",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "memoryDate" to mapOf("type" to "string", "description" to "记忆日期 yyyy-MM-dd"),
                            "quote" to mapOf("type" to "string", "description" to "值得记住的一句话"),
                            "note" to mapOf("type" to "string", "description" to "为什么值得记住（可选）"),
                            "tags" to mapOf(
                                "type" to "array",
                                "items" to mapOf("type" to "string"),
                                "description" to "标签"
                            ),
                            "mood" to mapOf("type" to "string", "description" to "情绪（可选）"),
                            "sourceType" to mapOf(
                                "type" to "string",
                                "description" to "来源类型: life_record / diary / chat"
                            ),
                            "sourceId" to mapOf("type" to "string", "description" to "来源 ID")
                        ),
                        "required" to listOf("memoryDate", "quote", "note", "sourceType", "sourceId")
                    )
                )
            ),
            requireApproval = false,
            riskLevel = "low",
            tags = listOf("echo", "memory"),
            executor = { args ->
                val memoryDate = args["memoryDate"] as? String
                    ?: return@ToolDefinition jsonError("缺少 memoryDate")
                val quote = args["quote"] as? String
                    ?: return@ToolDefinition jsonError("缺少 quote")
                val note = args["note"] as? String ?: ""
                val tags = parseStringList(args["tags"])
                val mood = args["mood"] as? String
                val sourceType = args["sourceType"] as? String ?: "chat"
                val sourceId = args["sourceId"] as? String ?: ""

                val card = MemoryCard(
                    id = UUID.randomUUID().toString(),
                    createdAt = System.currentTimeMillis(),
                    memoryDate = memoryDate,
                    quote = quote,
                    note = note,
                    tags = tags,
                    mood = mood,
                    sourceType = sourceType,
                    sourceId = sourceId
                )
                memoryRepo.addCard(card)
                jsonOk("已保存记忆卡片", card.id)
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
