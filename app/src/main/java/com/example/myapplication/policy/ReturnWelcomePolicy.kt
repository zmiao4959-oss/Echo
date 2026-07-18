package com.example.myapplication.policy

import com.example.myapplication.data.model.LifeRecord
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Offers a guilt-free re-entry after a recording gap. */
object ReturnWelcomePolicy {

    sealed class Welcome {
        object Hidden : Welcome()
        data class WelcomeBack(
            val lastRecordDate: String,
            val daysSinceLastRecord: Int,
            val message: String
        ) : Welcome()
    }

    fun build(records: List<LifeRecord>, today: String): Welcome {
        val candidates = records.mapNotNull { record ->
            if (record.content.isBlank()) return@mapNotNull null
            val daysAgo = daysBetween(record.date, today) ?: return@mapNotNull null
            if (daysAgo < 0) null else record.date to daysAgo
        }
        val latest = candidates.minByOrNull { it.second } ?: return Welcome.Hidden
        val daysSinceLastRecord = latest.second
        if (daysSinceLastRecord <= 1) return Welcome.Hidden

        val message = when (daysSinceLastRecord) {
            2 -> "隔了一天也没关系，回来就已经接上了。今天留一句就好。"
            in 3..6 -> "欢迎回来。前几天不必补齐，从今天的一句重新接上就好。"
            in 7..29 -> "好久不见。过去的记录都还在，今天可以从一个小片段重新开始。"
            else -> "欢迎回来。这里没有欠下的打卡，想写的时候从今天开始就好。"
        }
        return Welcome.WelcomeBack(
            lastRecordDate = latest.first,
            daysSinceLastRecord = daysSinceLastRecord,
            message = message
        )
    }

    private fun daysBetween(from: String, to: String): Int? {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
            timeZone = UTC
        }
        return try {
            val fromMillis = formatter.parse(from)?.time ?: return null
            val toMillis = formatter.parse(to)?.time ?: return null
            TimeUnit.MILLISECONDS.toDays(toMillis - fromMillis).toInt()
        } catch (_: Exception) {
            null
        }
    }

    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")
}
