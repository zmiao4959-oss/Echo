package com.example.myapplication.tools

import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.data.repository.LifeRecordRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Echo 生活记录工具 —— 创建 / 搜索 LifeRecord。
 */
object EchoLifeTools {

    private val recordRepo = LifeRecordRepository()

    fun registerAll() {
        ToolRegistry.register(ToolDefinition(
            name = "create_life_record",
            description = "把用户当前表达的一段生活片段保存为生活记录。不要把所有闲聊都保存，只在用户表达明显的生活片段、情绪、项目进展、想法时调用。",
            schema = mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "create_life_record",
                    "description" to "把用户当前表达的一段生活片段保存为生活记录",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "content" to mapOf("type" to "string", "description" to "记录的内容"),
                            "source" to mapOf(
                                "type" to "string",
                                "enum" to listOf("text", "voice", "chat", "checkin"),
                                "description" to "来源：text=文字, voice=语音, chat=对话, checkin=问候"
                            ),
                            "mood" to mapOf("type" to "string", "description" to "情绪描述（可选）"),
                            "tags" to mapOf(
                                "type" to "array",
                                "items" to mapOf("type" to "string"),
                                "description" to "标签列表"
                            ),
                            "importance" to mapOf(
                                "type" to "integer",
                                "description" to "重要程度 1-5（默认1）"
                            )
                        ),
                        "required" to listOf("content", "source")
                    )
                )
            ),
            requireApproval = false,
            riskLevel = "low",
            tags = listOf("echo", "life_records"),
            executor = { args ->
                val content = args["content"] as? String ?: return@ToolDefinition jsonError("缺少 content")
                val source = args["source"] as? String ?: "chat"
                val mood = args["mood"] as? String
                val tags = parseStringList(args["tags"])
                val importance = (args["importance"] as? Double)?.toInt() ?: 1

                val record = LifeRecord(
                    id = UUID.randomUUID().toString(),
                    createdAt = System.currentTimeMillis(),
                    date = today(),
                    content = content,
                    source = source,
                    mood = mood,
                    tags = tags,
                    importance = importance.coerceIn(1, 5)
                )
                recordRepo.add(record)
                jsonOk("已记录", record.id)
            }
        ))
    }

    private fun today(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    private fun parseStringList(value: Any?): List<String> {
        if (value == null) return emptyList()
        return when (value) {
            is List<*> -> value.mapNotNull { it?.toString() }
            else -> emptyList()
        }
    }
}
