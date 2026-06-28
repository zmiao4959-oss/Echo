package com.example.myapplication.tools

import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.data.repository.DiaryRepository
import com.example.myapplication.data.repository.LifeRecordRepository
import com.example.myapplication.data.repository.MemoryRepository
import com.example.myapplication.memory.FileStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

        // ── remember_user_fact ──
        ToolRegistry.register(ToolDefinition(
            name = "remember_user_fact",
            description = "记住关于用户的重要个人信息（姓名、年龄、城市、职业、喜好、习惯、重要关系等）。当用户告诉你关于自己的事、背景、偏好、或任何你以后对话中应该记住的信息时调用此工具。不要用 create_life_record 或 create_memory_card 来记个人信息——那些是记录生活事件和高光时刻的。",
            schema = mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "remember_user_fact",
                    "description" to "将关于用户的重要个人信息持久化到长期记忆 MEMORY.md",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "fact" to mapOf("type" to "string", "description" to "要记住的事实，简洁一句话。如'用户叫小明，在北京工作'、'用户喜欢喝咖啡，不喜欢奶茶'"),
                            "category" to mapOf("type" to "string", "description" to "分类，如 basic_info / preferences / habits / relationships / work / other")
                        ),
                        "required" to listOf("fact")
                    )
                )
            ),
            requireApproval = false,
            riskLevel = "low",
            tags = listOf("echo", "memory"),
            executor = { args ->
                val fact = args["fact"] as? String
                    ?: return@ToolDefinition jsonError("缺少 fact")
                val category = args["category"] as? String ?: "other"

                val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

                val entry = "- [$now] [$category] $fact\n"

                try {
                    val current = FileStore.readWorkspaceFile("MEMORY.md")
                    val updated = com.example.myapplication.memory.MemoryMdParser.appendFact(current, entry)
                    FileStore.writeWorkspaceFile("MEMORY.md", updated)
                    jsonOk("已记住: $fact", null)
                } catch (e: Exception) {
                    jsonError("保存失败: ${e.message}")
                }
            }
        ))

        // ── create_memory_card ──
        ToolRegistry.register(ToolDefinition(
            name = "create_memory_card",
            description = "从对话中提取一段值得回顾的瞬间，存为记忆卡片。用户说的任何有趣、有意义、有情绪的内容都可以存——比如一个想法、一个决定、一段感受、一次小成就。每次对话中主动创建 1-3 张卡片。不要用这个工具记用户的基本信息（用 remember_user_fact），但生活中有温度的瞬间都适合。",
            schema = mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "create_memory_card",
                    "description" to "从对话中提取值得回顾的瞬间，存为记忆卡片",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "quote" to mapOf("type" to "string", "description" to "值得回顾的一句话，保留用户原话"),
                            "note" to mapOf("type" to "string", "description" to "一句话说明为什么值得记"),
                            "tags" to mapOf(
                                "type" to "array",
                                "items" to mapOf("type" to "string"),
                                "description" to "标签，如 工作、生活、感悟、决定"
                            ),
                            "mood" to mapOf("type" to "string", "description" to "情绪（可选）")
                        ),
                        "required" to listOf("quote", "note")
                    )
                )
            ),
            requireApproval = false,
            riskLevel = "low",
            tags = listOf("echo", "memory"),
            executor = { args ->
                val quote = args["quote"] as? String
                    ?: return@ToolDefinition jsonError("缺少 quote")
                val note = args["note"] as? String ?: ""
                val tags = parseStringList(args["tags"])
                val mood = args["mood"] as? String
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

                val card = MemoryCard(
                    id = UUID.randomUUID().toString(),
                    createdAt = System.currentTimeMillis(),
                    memoryDate = today,
                    quote = quote,
                    note = note,
                    tags = tags,
                    mood = mood,
                    sourceType = "chat",
                    sourceId = ""
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
