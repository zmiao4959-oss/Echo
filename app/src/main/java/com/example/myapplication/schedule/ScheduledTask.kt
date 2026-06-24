package com.example.myapplication.schedule

import com.google.gson.Gson
import java.util.UUID

/**
 * 定时提醒任务。
 *
 * @param id 唯一标识
 * @param hour 小时 (0-23)
 * @param minute 分钟 (0-59)
 * @param daysOfWeek 星期集合，1=周日, 2=周一, ..., 7=周六
 * @param prompt 提醒提示词
 * @param enabled 是否启用
 * @param createdAt 创建时间戳
 */
data class ScheduledTask(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int,
    val minute: Int,
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7), // 默认每天
    val prompt: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** 格式化的时间字符串 (HH:mm) */
    val timeFormatted: String get() = "%02d:%02d".format(hour, minute)

    /** 星期中文标签 */
    val daysLabel: String get() {
        val names = listOf("", "周日", "周一", "周二", "周三", "周四", "周五", "周六")
        return daysOfWeek.sorted().joinToString(" ") { names.getOrElse(it) { "" } }
    }

    fun toJson(): String = gson.toJson(toSerializable())

    private fun toSerializable(): SerializableSchedule = SerializableSchedule(
        id = id,
        hour = hour,
        minute = minute,
        daysOfWeek = daysOfWeek.toList(),
        prompt = prompt,
        enabled = enabled,
        createdAt = createdAt
    )

    companion object {
        private val gson = Gson()

        fun fromJson(json: String): ScheduledTask? = try {
            val s = gson.fromJson(json, SerializableSchedule::class.java)
            ScheduledTask(
                id = s.id,
                hour = s.hour,
                minute = s.minute,
                daysOfWeek = s.daysOfWeek.toSet(),
                prompt = s.prompt,
                enabled = s.enabled,
                createdAt = s.createdAt
            )
        } catch (e: Exception) {
            null
        }
    }
}

/** JSON 序列化镜像 */
data class SerializableSchedule(
    val id: String,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: List<Int>,
    val prompt: String,
    val enabled: Boolean,
    val createdAt: Long
)
