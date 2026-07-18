package com.example.myapplication.policy

import java.util.Calendar
import java.util.TimeZone

/**
 * 轻提醒的纯本地规则：只在今天还没有生活片段时提醒，并计算下一次触发时间。
 */
object GentleRecordReminderPolicy {

    private const val MIN_ADAPTIVE_DAYS = 3
    private const val MAX_ADAPTIVE_DAYS = 21
    private const val EARLIEST_MINUTE = 7 * 60
    private const val LATEST_MINUTE = 22 * 60 + 30

    data class TimeOfDay(val hour: Int, val minute: Int)

    private val messages = listOf(
        "如果愿意，给今天留一句话就好。",
        "不必完整，记下此刻的一点点就够了。",
        "今天还没有留下片段。写一句，也算把今天接住了。"
    )

    fun shouldNotify(enabled: Boolean, recordCountToday: Int): Boolean =
        enabled && recordCountToday == 0

    fun messageFor(date: String): String =
        messages[Math.floorMod(date.hashCode(), messages.size)]

    /**
     * Uses the first record of each recent day so a busy day with many fragments does not
     * outweigh quieter days. Three distinct days are required before learning takes over.
     */
    fun preferredReminderTime(
        recordedAtMillis: List<Long>,
        fallbackHour: Int,
        fallbackMinute: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): TimeOfDay {
        val fallback = TimeOfDay(
            hour = fallbackHour.coerceIn(0, 23),
            minute = fallbackMinute.coerceIn(0, 59)
        )
        val firstMinuteByDay = recordedAtMillis
            .asSequence()
            .filter { it > 0L }
            .map { timestamp ->
                val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = timestamp }
                val dayKey = calendar.get(Calendar.YEAR) * 400 + calendar.get(Calendar.DAY_OF_YEAR)
                val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 +
                    calendar.get(Calendar.MINUTE)
                dayKey to minuteOfDay
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, minutes) -> minutes.minOrNull() ?: 0 }
            .entries
            .sortedByDescending { it.key }
            .take(MAX_ADAPTIVE_DAYS)
            .map { it.value }

        if (firstMinuteByDay.size < MIN_ADAPTIVE_DAYS) return fallback

        val sorted = firstMinuteByDay.sorted()
        val middle = sorted.size / 2
        val median = if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2
        }
        val rounded = ((median + 2) / 5) * 5
        val safeMinute = rounded.coerceIn(EARLIEST_MINUTE, LATEST_MINUTE)
        return TimeOfDay(hour = safeMinute / 60, minute = safeMinute % 60)
    }

    fun nextTriggerAtMillis(
        nowMillis: Long,
        hour: Int,
        minute: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val candidate = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, safeHour)
            set(Calendar.MINUTE, safeMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (candidate.timeInMillis <= nowMillis) {
            candidate.add(Calendar.DAY_OF_MONTH, 1)
        }
        return candidate.timeInMillis
    }
}
