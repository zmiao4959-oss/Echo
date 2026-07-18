package com.example.myapplication.policy

import com.example.myapplication.data.model.EchoForeshadow
import kotlin.math.max

/** Pure Kotlin detection, deduplication and timing rules for Echo foreshadows. */
object ForeshadowPolicy {

    const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    private val intentMarkers = listOf(
        "不知道能不能", "不知道会不会", "最近一直想", "重新开始", "有机会想",
        "想要", "打算", "准备", "考虑", "希望", "决定", "尝试", "试着", "开始",
        "期待", "等待", "等到", "以后想", "想"
    )

    private val negativeMarkers = listOf(
        "不想", "没想", "没有想", "不打算", "没打算", "不准备", "不考虑", "不希望"
    )

    private val completedMarkers = listOf(
        "已经完成", "终于完成", "做完了", "搞定了", "结束了", "成功了", "实现了"
    )

    private val exactTaskPattern = Regex(
        "(今天|明天|后天|今晚|本周|下周|周[一二三四五六日天]|星期[一二三四五六日天]|\\d{1,2}[点时]|\\d{1,2}月\\d{1,2}日).{0,16}(提醒|提交|预约|取|拿|买|交|开会|打电话)"
    )

    data class Candidate(
        val subject: String,
        val title: String,
        val followUpQuestion: String,
        val confidence: Float,
        val nextCheckAt: Long
    )

    fun detect(text: String, sourceCreatedAt: Long): Candidate? {
        val compact = text.trim().replace(Regex("\\s+"), " ")
        if (compact.length < 5 || negativeMarkers.any(compact::contains)) return null
        if (completedMarkers.any(compact::contains) && !compact.contains("已经决定")) return null
        if (exactTaskPattern.containsMatchIn(compact)) return null

        val marker = intentMarkers
            .mapNotNull { marker -> compact.indexOf(marker).takeIf { it >= 0 }?.let { marker to it } }
            .minByOrNull { (_, index) -> index }
            ?.first
            ?: return null
        val subject = extractSubject(compact, marker) ?: return null
        val title = subject
            .trim('，', '。', '！', '？', ',', '.', '!', '?')
            .take(24)
        if (normalizeSubject(title).length < 2) return null

        val confidence = when {
            compact.contains("不知道能不能") || compact.contains("不知道会不会") -> 0.88f
            compact.contains("打算") || compact.contains("准备") || compact.contains("决定") -> 0.84f
            compact.contains("最近一直想") || compact.contains("重新开始") -> 0.82f
            else -> 0.74f
        }
        return Candidate(
            subject = subject,
            title = title,
            followUpQuestion = "之前你提到「$title」，这件事后来怎么样了？",
            confidence = confidence,
            nextCheckAt = sourceCreatedAt + horizonDays(compact) * DAY_MILLIS
        )
    }

    fun isDuplicate(subject: String, existing: List<EchoForeshadow>): Boolean {
        val normalized = normalizeSubject(subject)
        if (normalized.isBlank()) return true
        return existing.any { thread ->
            val other = normalizeSubject(thread.subject)
            val shorter = minOf(normalized.length, other.length)
            (shorter >= 3 && (normalized.contains(other) || other.contains(normalized))) ||
                bigramSimilarity(normalized, other) >= 0.58
        }
    }

    /** Returns the most relevant existing thread when a new record looks like progress. */
    fun findRelated(text: String, existing: List<EchoForeshadow>): EchoForeshadow? {
        val normalizedText = normalizeSubject(text)
        if (normalizedText.length < 2) return null
        return existing
            .map { it to relatedness(normalizeSubject(it.subject), normalizedText) }
            .filter { (_, score) -> score >= 0.42 }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    fun normalizeSubject(value: String): String {
        var result = value.lowercase()
        val removable = intentMarkers + listOf(
            "最近", "一直", "以后", "有机会", "这件事", "一下", "试试", "看看",
            "我", "自己", "真的", "可能", "也许", "能够", "可以", "要"
        )
        removable.sortedByDescending(String::length).forEach { result = result.replace(it, "") }
        return result.replace(Regex("[^\\p{L}\\p{N}]"), "")
    }

    private fun extractSubject(text: String, marker: String): String? {
        val markerIndex = text.indexOf(marker)
        if (markerIndex < 0) return null
        var subject = text.substring(markerIndex + marker.length)
            .substringBefore('。')
            .substringBefore('！')
            .substringBefore('？')
            .substringBefore(';')
            .substringBefore('；')
            .trim()
        subject = subject.replace(Regex("^(要|去|能不能|会不会|着|先|再)+"), "").trim()
        subject = subject.replace(Regex("(吧|呢|啊|呀|看看|试试)[。！？，,!?]*$"), "").trim()
        if (subject.length < 2) return null
        return subject.take(48)
    }

    private fun horizonDays(text: String): Long = when {
        text.contains("几天") || text.contains("过两天") -> 3L
        text.contains("一周") || text.contains("下周") -> 7L
        text.contains("一个月") || text.contains("以后") || text.contains("有机会") -> 30L
        text.contains("几个月") || text.contains("明年") -> 60L
        text.contains("最近") || text.contains("开始") || text.contains("尝试") -> 10L
        else -> 14L
    }

    private fun relatedness(subject: String, text: String): Double {
        if (subject.length >= 2 && text.contains(subject)) return 1.0
        return max(bigramSimilarity(subject, text), characterCoverage(subject, text))
    }

    private fun characterCoverage(subject: String, text: String): Double {
        val meaningful = subject.toSet()
        if (meaningful.size < 2) return 0.0
        return meaningful.count(text::contains).toDouble() / meaningful.size
    }

    private fun bigramSimilarity(left: String, right: String): Double {
        val leftBigrams = bigrams(left)
        val rightBigrams = bigrams(right)
        if (leftBigrams.isEmpty() || rightBigrams.isEmpty()) return 0.0
        val intersection = leftBigrams.intersect(rightBigrams).size
        val union = leftBigrams.union(rightBigrams).size
        return if (union == 0) 0.0 else intersection.toDouble() / union
    }

    private fun bigrams(value: String): Set<String> = when {
        value.length < 2 -> emptySet()
        else -> (0 until value.length - 1).mapTo(linkedSetOf()) { value.substring(it, it + 2) }
    }
}
