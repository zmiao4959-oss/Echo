package com.example.myapplication.ui.plan

import java.util.Calendar

data class PlanDraft(
    val title: String,
    val message: String,
    val triggerAt: Long,
    val repeatRule: String? = null
)

/** Lightweight, offline parser for the plan phrases used most often in Echo. */
object NaturalLanguagePlanParser {

    private val dateWordRegex = Regex("今天|明天|后天")
    private val colonTimeRegex = Regex("(?:凌晨|早上|上午|中午|下午|傍晚|晚上)?\\s*([01]?\\d|2[0-3]):([0-5]\\d)")
    private val spokenTimeRegex = Regex(
        "(?:(凌晨|早上|上午|中午|下午|傍晚|晚上)\\s*)?" +
            "([0-2]?\\d|[零〇一二两三四五六七八九十]{1,3})\\s*[点时]" +
            "(?:\\s*([0-5]?\\d|半)\\s*分?)?"
    )

    fun parse(input: String, nowMillis: Long = System.currentTimeMillis()): PlanDraft? {
        val message = input.trim()
        if (message.isEmpty()) return null

        val dayOffset = when {
            "后天" in message -> 2
            "明天" in message -> 1
            else -> 0
        }

        val spokenMatch = spokenTimeRegex.find(message)
        val colonMatch = colonTimeRegex.find(message)
        val hasExplicitTime = spokenMatch != null || colonMatch != null

        var hour = 9
        var minute = 0
        if (spokenMatch != null) {
            val period = spokenMatch.groupValues[1]
            hour = parseNumber(spokenMatch.groupValues[2])?.coerceIn(0, 23) ?: 9
            minute = when (val token = spokenMatch.groupValues[3]) {
                "半" -> 30
                "" -> 0
                else -> token.toIntOrNull()?.coerceIn(0, 59) ?: 0
            }
            if (period in setOf("中午", "下午", "傍晚", "晚上") && hour in 1..11) hour += 12
            if (period == "凌晨" && hour == 12) hour = 0
        } else if (colonMatch != null) {
            hour = colonMatch.groupValues[1].toInt()
            minute = colonMatch.groupValues[2].toInt()
            val prefix = colonMatch.value
            if (prefix.contains(Regex("中午|下午|傍晚|晚上")) && hour in 1..11) hour += 12
        }

        val target = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (dayOffset == 0 && hasExplicitTime && target.timeInMillis <= nowMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        val repeatRule = when {
            "每天" in message || "每日" in message -> "每天"
            "每周" in message || "每星期" in message -> "每周"
            else -> null
        }

        val title = message
            .replace(dateWordRegex, "")
            .replace(spokenTimeRegex, "")
            .replace(colonTimeRegex, "")
            .replace(Regex("每天|每日|每周|每星期"), "")
            .replace(Regex("^(请|麻烦)?\\s*(提醒我|记得提醒我|帮我记得)\\s*"), "")
            .trim(' ', '，', ',', '。', '.', '：', ':')
            .ifEmpty { message }
            .take(40)

        return PlanDraft(title, message, target.timeInMillis, repeatRule)
    }

    private fun parseNumber(token: String): Int? {
        token.toIntOrNull()?.let { return it }
        val digits = mapOf(
            '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3,
            '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
        )
        if (token == "十") return 10
        val tenIndex = token.indexOf('十')
        if (tenIndex >= 0) {
            val tens = if (tenIndex == 0) 1 else digits[token.first()] ?: return null
            val ones = if (tenIndex == token.lastIndex) 0 else digits[token.last()] ?: return null
            return tens * 10 + ones
        }
        return if (token.length == 1) digits[token.first()] else null
    }
}
