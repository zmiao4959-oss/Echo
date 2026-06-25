package com.example.myapplication.tools

import com.example.myapplication.MyApplication
import com.example.myapplication.data.model.EchoPlan
import com.example.myapplication.data.repository.PlanRepository
import com.example.myapplication.schedule.PlanScheduler
import java.util.UUID

/**
 * Echo 规划工具 —— AI 创建/更新/删除 EchoPlan。
 */
object EchoPlanTools {

    private val planRepo = PlanRepository()

    fun registerAll() {
        // ── create_plan ──
        ToolRegistry.register(ToolDefinition(
            name = "create_plan",
            description = "创建未来的任务提醒、主动问候、回忆触发或自动日记整理。type 取值：task_reminder=任务提醒, companion_checkin=主动问候, memory_trigger=回忆触发, auto_diary=自动日记。triggerAt 使用 Unix 毫秒时间戳。",
            schema = mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "create_plan",
                    "description" to "创建未来的任务提醒、主动问候、回忆触发或自动日记整理",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "type" to mapOf(
                                "type" to "string",
                                "enum" to listOf("task_reminder", "companion_checkin", "memory_trigger", "auto_diary"),
                                "description" to "规划类型"
                            ),
                            "title" to mapOf("type" to "string", "description" to "规划标题"),
                            "message" to mapOf("type" to "string", "description" to "提醒消息内容"),
                            "triggerAt" to mapOf("type" to "integer", "description" to "触发时间（Unix 毫秒时间戳）"),
                            "repeatRule" to mapOf("type" to "string", "description" to "重复规则，如 daily/weekly/不填"),
                            "autoSpeak" to mapOf("type" to "boolean", "description" to "是否自动语音播报"),
                            "importance" to mapOf("type" to "integer", "description" to "重要程度 1-5"),
                            "tags" to mapOf(
                                "type" to "array",
                                "items" to mapOf("type" to "string"),
                                "description" to "标签"
                            )
                        ),
                        "required" to listOf("type", "title", "message", "triggerAt")
                    )
                )
            ),
            requireApproval = false,
            riskLevel = "low",
            tags = listOf("echo", "plan"),
            executor = { args ->
                val type = args["type"] as? String ?: return@ToolDefinition jsonError("缺少 type")
                val title = args["title"] as? String ?: return@ToolDefinition jsonError("缺少 title")
                val message = args["message"] as? String ?: title
                val triggerAt = parseTimestamp(args["triggerAt"])
                val repeatRule = args["repeatRule"] as? String
                val autoSpeak = args["autoSpeak"] as? Boolean
                    ?: (type == "task_reminder")
                val importance = (args["importance"] as? Double)?.toInt() ?: 1
                val tags = parseStringList(args["tags"])

                val now = System.currentTimeMillis()
                val plan = EchoPlan(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    title = title,
                    message = message,
                    triggerAt = triggerAt,
                    repeatRule = repeatRule?.ifEmpty { null },
                    autoSpeak = autoSpeak,
                    importance = importance.coerceIn(1, 5),
                    tags = tags,
                    createdAt = now,
                    updatedAt = now
                )
                planRepo.add(plan)
                PlanScheduler.schedule(MyApplication.instance, plan)
                jsonOk("已创建规划: $title", plan.id)
            }
        ))

        // ── update_plan ──
        ToolRegistry.register(ToolDefinition(
            name = "update_plan",
            description = "修改已有规划项。只需提供 planId 和要修改的字段，未提供的字段保持不变。",
            schema = mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "update_plan",
                    "description" to "修改已有规划项",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "planId" to mapOf("type" to "string", "description" to "规划 ID"),
                            "title" to mapOf("type" to "string", "description" to "新标题（可选）"),
                            "message" to mapOf("type" to "string", "description" to "新消息（可选）"),
                            "triggerAt" to mapOf("type" to "integer", "description" to "新触发时间（可选）"),
                            "repeatRule" to mapOf("type" to "string", "description" to "新重复规则（可选）"),
                            "enabled" to mapOf("type" to "boolean", "description" to "是否启用（可选）"),
                            "autoSpeak" to mapOf("type" to "boolean", "description" to "是否自动播报（可选）")
                        ),
                        "required" to listOf("planId")
                    )
                )
            ),
            requireApproval = false,
            riskLevel = "low",
            tags = listOf("echo", "plan"),
            executor = { args ->
                val planId = args["planId"] as? String
                    ?: return@ToolDefinition jsonError("缺少 planId")
                val existing = planRepo.getById(planId)
                    ?: return@ToolDefinition jsonError("规划不存在: $planId")

                val updated = existing.copy(
                    title = (args["title"] as? String) ?: existing.title,
                    message = (args["message"] as? String) ?: existing.message,
                    triggerAt = parseTimestamp(args["triggerAt"], existing.triggerAt),
                    repeatRule = (args["repeatRule"] as? String)?.ifEmpty { null } ?: existing.repeatRule,
                    enabled = (args["enabled"] as? Boolean) ?: existing.enabled,
                    autoSpeak = (args["autoSpeak"] as? Boolean) ?: existing.autoSpeak,
                    updatedAt = System.currentTimeMillis()
                )
                planRepo.update(updated)
                PlanScheduler.cancel(MyApplication.instance, updated.id)
                PlanScheduler.schedule(MyApplication.instance, updated)
                jsonOk("已更新规划", updated.id)
            }
        ))

        // ── delete_plan ──
        ToolRegistry.register(ToolDefinition(
            name = "delete_plan",
            description = "删除已有规划项。",
            schema = mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "delete_plan",
                    "description" to "删除已有规划项",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "planId" to mapOf("type" to "string", "description" to "要删除的规划 ID")
                        ),
                        "required" to listOf("planId")
                    )
                )
            ),
            requireApproval = true,
            riskLevel = "medium",
            tags = listOf("echo", "plan"),
            executor = { args ->
                val planId = args["planId"] as? String
                    ?: return@ToolDefinition jsonError("缺少 planId")
                planRepo.delete(planId)
                PlanScheduler.cancel(MyApplication.instance, planId)
                jsonOk("已删除规划", planId)
            }
        ))
    }

    private fun parseTimestamp(value: Any?, fallback: Long = System.currentTimeMillis() + 3600_000): Long {
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: fallback
            else -> fallback
        }
    }

    private fun parseStringList(value: Any?): List<String> {
        if (value == null) return emptyList()
        return when (value) {
            is List<*> -> value.mapNotNull { it?.toString() }
            else -> emptyList()
        }
    }
}
