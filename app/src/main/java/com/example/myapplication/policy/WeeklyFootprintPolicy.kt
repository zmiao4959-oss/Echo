package com.example.myapplication.policy

import com.example.myapplication.data.model.LifeRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * A non-punitive view of the user's recording rhythm.
 *
 * It deliberately avoids streaks and negative comparisons: missed days never
 * reset progress, and a quieter week is described only by what was retained.
 */
object WeeklyFootprintPolicy {

    data class DayFootprint(
        val date: String,
        val weekdayLabel: String,
        val recordCount: Int
    ) {
        val hasRecord: Boolean get() = recordCount > 0
    }

    data class WeeklyFootprint(
        val days: List<DayFootprint>,
        val activeDays: Int,
        val totalRecords: Int,
        val previousActiveDays: Int,
        val positiveChangeDays: Int,
        val message: String
    )

    fun build(records: List<LifeRecord>, today: String): WeeklyFootprint {
        val currentDates = (6 downTo 0).mapNotNull { offsetDate(today, -it) }
        val previousDates = (13 downTo 7).mapNotNull { offsetDate(today, -it) }
        val relevantDates = (currentDates + previousDates).toSet()
        val countsByDate = records.asSequence()
            .filter { it.content.isNotBlank() && it.date in relevantDates }
            .groupingBy { it.date }
            .eachCount()

        val days = currentDates.map { date ->
            DayFootprint(
                date = date,
                weekdayLabel = if (date == today) "今" else weekdayLabel(date),
                recordCount = countsByDate[date] ?: 0
            )
        }
        val activeDays = days.count { it.hasRecord }
        val totalRecords = days.sumOf { it.recordCount }
        val previousActiveDays = previousDates.count { (countsByDate[it] ?: 0) > 0 }
        val positiveChangeDays = (activeDays - previousActiveDays).coerceAtLeast(0)

        return WeeklyFootprint(
            days = days,
            activeDays = activeDays,
            totalRecords = totalRecords,
            previousActiveDays = previousActiveDays,
            positiveChangeDays = positiveChangeDays,
            message = buildMessage(activeDays, previousActiveDays)
        )
    }

    /** Earliest date the caller needs to load for a current-vs-previous comparison. */
    fun earliestRequiredDate(today: String): String = offsetDate(today, -13) ?: today

    private fun buildMessage(activeDays: Int, previousActiveDays: Int): String {
        val positiveChange = activeDays - previousActiveDays
        return when {
            activeDays == 0 -> "这一周还没有留下片段，今天写一句也算开始。"
            activeDays == 7 -> "最近 7 天都留下了痕迹，这是一段完整的生活轨迹。"
            activeDays == 1 -> "这一周已经有一个落点，不需要每天都完整。"
            positiveChange > 0 && previousActiveDays > 0 ->
                "比前 7 天多留下 $positiveChange 天，你的记录节奏正在变得更稳。"
            activeDays >= 4 -> "这一周有 $activeDays 天留下痕迹，你已经建立了自己的节奏。"
            else -> "这一周已有 $activeDays 天被记住，间隔几天也不影响积累。"
        }
    }

    private fun weekdayLabel(date: String): String {
        val parsed = formatter().parse(date) ?: return ""
        return SimpleDateFormat("E", Locale.CHINESE).apply {
            timeZone = UTC
        }.format(parsed).removePrefix("周").removePrefix("星期")
    }

    private fun offsetDate(date: String, days: Int): String? {
        val parsed = try {
            formatter().parse(date)
        } catch (_: Exception) {
            null
        } ?: return null
        val calendar = Calendar.getInstance(UTC, Locale.US).apply {
            time = parsed
            add(Calendar.DAY_OF_YEAR, days)
        }
        return formatter().format(calendar.time)
    }

    private fun formatter() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
        timeZone = UTC
    }

    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")
}
