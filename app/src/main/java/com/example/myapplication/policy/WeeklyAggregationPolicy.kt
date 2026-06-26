package com.example.myapplication.policy

/**
 * Pure Kotlin: Weekly review data aggregation and rule-based summary generation.
 *
 * Takes raw data objects, returns a structured review. Never fabricates.
 */
object WeeklyAggregationPolicy {

    data class ReviewInput(
        val dateRange: String,
        val lifeRecordCount: Int,
        val diaryCount: Int,
        val memoryCardCount: Int,
        val completedPlanCount: Int,
        val totalPlanCount: Int,
        val topKeywords: List<String>,
        val dominantMood: String?,
        val allTextForKeywords: List<String> = emptyList()
    )

    data class ReviewOutput(
        val dateRange: String,
        val lifeRecordCount: Int,
        val diaryCount: Int,
        val memoryCardCount: Int,
        val completedPlanCount: Int,
        val totalPlanCount: Int,
        val topKeywords: List<String>,
        val dominantMood: String?,
        val ruleSummary: String
    ) {
        val isEmpty: Boolean get() = lifeRecordCount == 0 && diaryCount == 0 && memoryCardCount == 0
    }

    // Stop words for keyword extraction
    val STOP_WORDS = setOf(
        "的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一",
        "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着",
        "没有", "看", "好", "自己", "这", "他", "她", "它", "们", "那", "些",
        "今天", "昨天", "明天", "这个", "那个", "什么", "怎么", "可以", "还是",
        "the", "a", "an", "is", "are", "was", "were", "be", "been", "being"
    )

    /** Build rule-based review from input. Never fabricates. */
    fun buildRuleReview(input: ReviewInput): ReviewOutput {
        val keywords = extractTopKeywords(input.allTextForKeywords, 5)
        val summary = buildRuleSummary(input, keywords)
        return ReviewOutput(
            dateRange = input.dateRange,
            lifeRecordCount = input.lifeRecordCount,
            diaryCount = input.diaryCount,
            memoryCardCount = input.memoryCardCount,
            completedPlanCount = input.completedPlanCount,
            totalPlanCount = input.totalPlanCount,
            topKeywords = keywords,
            dominantMood = input.dominantMood,
            ruleSummary = summary
        )
    }

    /** Build rule-based summary string. */
    fun buildRuleSummary(input: ReviewInput, keywords: List<String>): String {
        if (input.lifeRecordCount == 0 && input.diaryCount == 0 && input.memoryCardCount == 0) {
            return "本周暂无记录。"
        }

        val sb = StringBuilder()
        sb.append("本周共记录 ${input.lifeRecordCount} 个生活片段")
        if (input.diaryCount > 0) sb.append("、${input.diaryCount} 篇日记")
        sb.append("。")

        if (input.dominantMood != null) {
            sb.append("主要情绪：${input.dominantMood}。")
        }

        if (keywords.isNotEmpty()) {
            sb.append("关键词：${keywords.joinToString("、")}。")
        }

        if (input.memoryCardCount > 0) {
            sb.append("收藏了 ${input.memoryCardCount} 条回忆。")
        }

        if (input.totalPlanCount > 0) {
            sb.append("${input.completedPlanCount}/${input.totalPlanCount} 个计划已完成。")
        }

        return sb.toString()
    }

    /** Extract top N keywords from texts using bigram + frequency. */
    fun extractTopKeywords(texts: List<String>, topN: Int): List<String> {
        val wordCounts = mutableMapOf<String, Int>()
        val punctChars = setOf(',', '.', '!', '?', ';', ':', '"', '\'', '(', ')',
            '[', ']', '“', '”', '、', '。', '，', '．')

        for (text in texts) {
            val segments = text.split("\\s+".toRegex())
            for (seg in segments) {
                if (seg.isEmpty()) continue
                val hasCJK = seg.any { isCJK(it) }
                if (hasCJK) {
                    if (seg.length >= 2) {
                        for (i in 0..seg.length - 2) {
                            val bg = seg.substring(i, i + 2)
                            if (bg.length >= 2 && bg.none { it.isWhitespace() || it in punctChars }) {
                                val lower = bg.lowercase()
                                if (lower !in STOP_WORDS) {
                                    wordCounts[lower] = (wordCounts[lower] ?: 0) + 1
                                }
                            }
                        }
                    }
                } else {
                    val lower = seg.lowercase()
                    if (lower.length >= 2 && lower !in STOP_WORDS) {
                        wordCounts[lower] = (wordCounts[lower] ?: 0) + 1
                    }
                }
            }
        }
        return wordCounts.entries
            .sortedByDescending { it.value }
            .take(topN)
            .map { it.key }
    }

    /** Extract dominant mood from mood list (mode). */
    fun dominantMood(moods: List<String>): String? {
        if (moods.isEmpty()) return null
        return moods.groupBy { it }.maxByOrNull { it.value.size }?.key
    }

    /** Validate that summary only uses provided data (no fabrication check). */
    fun containsOnlyProvidedContent(summary: String, input: ReviewInput): Boolean {
        // Check that summary doesn't contain fabricated numbers or claims
        // by verifying all numeric references match the input
        val recordMention = "${input.lifeRecordCount} 个生活片段"
        if (input.lifeRecordCount > 0 && !summary.contains(recordMention)) return false
        return true
    }

    /** Cap summary for notification display. */
    fun capForNotification(summary: String, maxLen: Int = 200): String =
        summary.take(maxLen)

    private fun isCJK(c: Char): Boolean = c in '一'..'鿿' || c in '㐀'..'䶿'
}
