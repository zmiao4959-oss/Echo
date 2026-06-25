package com.example.myapplication.data.model

/**
 * 规划项（替代原有 ScheduledTask，扩展为四种类型）。
 *
 * type 取值：
 * - task_reminder      任务提醒
 * - companion_checkin  主动问候
 * - memory_trigger     回忆触发
 * - auto_diary         自动日记整理
 */
data class EchoPlan(
    val id: String,
    val type: String,              // task_reminder, companion_checkin, memory_trigger, auto_diary
    val title: String,
    val message: String,
    val triggerAt: Long,
    val repeatRule: String? = null,
    val enabled: Boolean = true,
    val autoSpeak: Boolean = false,
    val importance: Int = 1,
    val tags: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val lastTriggeredAt: Long? = null
)
