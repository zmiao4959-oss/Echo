package com.example.myapplication.policy

import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.model.LifeRecord
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Selects one stable, evidence-backed memory to bring back today. */
object PastEchoPolicy {

    data class PastEcho(
        val date: String,
        val contextLabel: String,
        val title: String,
        val snippet: String,
        val sourceType: String,
        val sourceId: String,
        val mood: String?,
        val microEcho: String?,
        val daysAgo: Int
    )

    private data class Candidate(
        val date: String,
        val title: String,
        val snippet: String,
        val sourceType: String,
        val sourceId: String,
        val mood: String?,
        val microEcho: String?,
        val daysAgo: Int
    )

    private val milestoneDays = setOf(7, 14, 30, 60, 90, 180, 365)
    private val difficultContentWords = setOf(
        "去世", "葬礼", "自杀", "自残", "流产", "创伤", "绝望", "崩溃", "痛苦", "分手", "离婚"
    )
    private val difficultMoodWords = setOf("绝望", "崩溃", "痛苦", "创伤", "重度低落")

    fun select(
        diaries: List<DailyDiary>,
        records: List<LifeRecord>,
        today: String
    ): PastEcho? {
        val diaryDates = diaries
            .filter { diary ->
                val snippet = diary.summary.ifBlank { diary.diaryText }
                snippet.isNotBlank() &&
                    (daysBetween(diary.date, today) ?: -1) >= 3 &&
                    !shouldAvoidAutomaticResurfacing(snippet, diary.mood)
            }
            .map { it.date }
            .toSet()
        val bestRecordByDate = records
            .filter { it.content.isNotBlank() && it.date !in diaryDates }
            .groupBy { it.date }
            .mapValues { (_, items) ->
                items.maxWithOrNull(
                    compareBy<LifeRecord> { it.importance }
                        .thenBy { it.content.length }
                        .thenBy { it.createdAt }
                )
            }

        val candidates = buildList {
            diaries.forEach { diary ->
                val snippet = diary.summary.ifBlank { diary.diaryText }.trim()
                val daysAgo = daysBetween(diary.date, today)
                if (
                    snippet.isNotBlank() && daysAgo != null && daysAgo >= 3 &&
                    !shouldAvoidAutomaticResurfacing(snippet, diary.mood)
                ) {
                    add(
                        Candidate(
                            date = diary.date,
                            title = diary.title.ifBlank { "那天的日记" },
                            snippet = snippet.take(160),
                            sourceType = "diary",
                            sourceId = diary.id,
                            mood = diary.mood.takeIf { it.isNotBlank() },
                            microEcho = null,
                            daysAgo = daysAgo
                        )
                    )
                }
            }
            bestRecordByDate.values.filterNotNull().forEach { record ->
                val daysAgo = daysBetween(record.date, today)
                if (
                    daysAgo != null && daysAgo >= 3 &&
                    !shouldAvoidAutomaticResurfacing(record.content, record.mood)
                ) {
                    add(
                        Candidate(
                            date = record.date,
                            title = "那天，你留下了一个片段",
                            snippet = record.content.trim().take(160),
                            sourceType = "life_record",
                            sourceId = record.id,
                            mood = record.mood?.takeIf { it.isNotBlank() },
                            microEcho = record.microEcho?.takeIf { it.isNotBlank() },
                            daysAgo = daysAgo
                        )
                    )
                }
            }
        }
        if (candidates.isEmpty()) return null

        val anniversary = candidates
            .filter { sameMonthDay(it.date, today) }
            .minByOrNull { it.daysAgo }

        val milestone = candidates
            .filter { it.daysAgo in milestoneDays }
            .maxByOrNull { it.daysAgo }

        val selected = anniversary ?: milestone ?: stableFallback(candidates, today)
        return selected.toPastEcho(today)
    }

    private fun stableFallback(candidates: List<Candidate>, today: String): Candidate {
        val sorted = candidates.sortedWith(
            compareByDescending<Candidate> { it.date }
                .thenBy { it.sourceType }
                .thenBy { it.sourceId }
        )
        val seed = today.filter(Char::isDigit).toLongOrNull() ?: 0L
        return sorted[(seed % sorted.size).toInt()]
    }

    private fun Candidate.toPastEcho(today: String): PastEcho = PastEcho(
        date = date,
        contextLabel = contextLabel(date, today, daysAgo),
        title = title,
        snippet = snippet,
        sourceType = sourceType,
        sourceId = sourceId,
        mood = mood,
        microEcho = microEcho,
        daysAgo = daysAgo
    )

    private fun contextLabel(date: String, today: String, daysAgo: Int): String {
        if (sameMonthDay(date, today)) {
            val yearGap = today.take(4).toIntOrNull()?.minus(date.take(4).toIntOrNull() ?: 0) ?: 0
            return if (yearGap == 1) "一年前的今天" else "${yearGap} 年前的今天"
        }
        return when (daysAgo) {
            7 -> "一周前的今天"
            14 -> "两周前的今天"
            else -> "$daysAgo 天前"
        }
    }

    private fun sameMonthDay(first: String, second: String): Boolean =
        first.length == 10 && second.length == 10 && first.substring(5) == second.substring(5)

    private fun shouldAvoidAutomaticResurfacing(content: String, mood: String?): Boolean =
        difficultContentWords.any { word -> content.contains(word) } ||
            difficultMoodWords.any { word -> mood?.contains(word) == true }

    private fun daysBetween(from: String, to: String): Int? {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return try {
            val fromMillis = formatter.parse(from)?.time ?: return null
            val toMillis = formatter.parse(to)?.time ?: return null
            TimeUnit.MILLISECONDS.toDays(toMillis - fromMillis).toInt().takeIf { it >= 0 }
        } catch (_: Exception) {
            null
        }
    }
}
