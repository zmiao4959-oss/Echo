package com.example.myapplication.memory

import com.example.myapplication.MyApplication
import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.data.repository.DiaryRepository
import com.example.myapplication.data.repository.LifeRecordRepository
import com.example.myapplication.data.repository.MemoryRepository
import com.example.myapplication.data.repository.PlanRepository
import com.example.myapplication.llm.LLMMessage
import com.example.myapplication.llm.ProviderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Weekly review builder — aggregates last 7 days of data and generates a summary.
 *
 * Rule-based aggregation is always available. LLM summary is optional and falls back
 * to rule-based on failure.
 *
 * Principle: never fabricate — only use real records.
 */
object WeeklyReviewBuilder {

    data class WeeklyReview(
        val dateRange: String,
        val lifeRecordCount: Int,
        val diaryCount: Int,
        val memoryCardCount: Int,
        val completedPlanCount: Int,
        val totalPlanCount: Int,
        val topKeywords: List<String>,
        val dominantMood: String?,
        val summary: String
    )

    // Stop words for keyword extraction
    private val STOP_WORDS = setOf(
        "的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一",
        "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着",
        "没有", "看", "好", "自己", "这", "他", "她", "它", "们", "那", "些",
        "今天", "昨天", "明天", "这个", "那个", "什么", "怎么", "可以", "还是",
        "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did", "will", "would", "could",
        "should", "may", "might", "can", "shall", "to", "of", "in", "for",
        "on", "with", "at", "by", "from", "and", "or", "but", "not", "this",
        "that", "it", "its"
    )

    /** Rule-based weekly review. Always works, never fabricates. */
    suspend fun build(): WeeklyReview = withContext(Dispatchers.IO) {
        val recordRepo = LifeRecordRepository()
        val diaryRepo = DiaryRepository()
        val memoryRepo = MemoryRepository()
        val planRepo = PlanRepository()

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val endFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val endDate = endFmt.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val startDate = endFmt.format(cal.time)
        val startShort = SimpleDateFormat("M/d", Locale.US).format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, 6)
        val endShort = SimpleDateFormat("M/d", Locale.US).format(cal.time)

        // 1. LifeRecords (last 7 days)
        val records = try {
            recordRepo.getByDateRange(startDate, endDate)
        } catch (_: Exception) { emptyList<LifeRecord>() }

        // 2. Diaries (last 7 days)
        val diaries = try {
            diaryRepo.getByDateRange(startDate, endDate)
        } catch (_: Exception) { emptyList<DailyDiary>() }

        // 3. MemoryCards (last 7 days)
        val cards = try {
            val all = memoryRepo.getAllCards().filter { it.status == "confirmed" }
            val weekAgo = now - 7 * 86_400_000L
            all.filter { it.createdAt >= weekAgo }
        } catch (_: Exception) { emptyList<MemoryCard>() }

        // 4. Plans
        val allPlans = try { planRepo.getAll() } catch (_: Exception) { emptyList() }
        val weekAgo = now - 7 * 86_400_000L
        val completedPlans = allPlans.filter { (it.lastTriggeredAt ?: 0) >= weekAgo }

        // 5. Keywords
        val allText = records.map { it.content } + diaries.flatMap { listOf(it.title, it.summary, it.diaryText) }
        val keywords = extractTopKeywords(allText, 5)

        // 6. Dominant mood
        val moods = records.mapNotNull { it.mood } + cards.mapNotNull { it.mood }
        val dominantMood = if (moods.isNotEmpty()) {
            moods.groupBy { it }.maxByOrNull { it.value.size }?.key
        } else null

        // 7. Rule-based summary
        val summary = buildRuleSummary(
            lifeRecordCount = records.size,
            diaryCount = diaries.size,
            memoryCardCount = cards.size,
            completedPlanCount = completedPlans.size,
            totalPlanCount = allPlans.size,
            topKeywords = keywords,
            dominantMood = dominantMood
        )

        WeeklyReview(
            dateRange = "$startShort - $endShort",
            lifeRecordCount = records.size,
            diaryCount = diaries.size,
            memoryCardCount = cards.size,
            completedPlanCount = completedPlans.size,
            totalPlanCount = allPlans.size,
            topKeywords = keywords,
            dominantMood = dominantMood,
            summary = summary
        )
    }

    /** Build rule-based summary with optional LLM enhancement. Falls back to rule-based. */
    suspend fun buildWithLLM(): WeeklyReview = withContext(Dispatchers.IO) {
        val review = build()

        if (review.lifeRecordCount == 0 && review.diaryCount == 0 && review.memoryCardCount == 0) {
            return@withContext review
        }

        try {
            val config = MyApplication.instance.appConfig
            if (!config.isLLMConfigured) return@withContext review

            val provider = ProviderFactory.createLLMProvider()
            val systemPrompt = """
You are summarizing a user's weekly activity. Only use the provided data. DO NOT fabricate, guess, or add any information not present. Write in Chinese, under 200 characters. Be natural and light, avoid clichés.
""".trimIndent()

            val userPrompt = """
本周数据（${review.dateRange}）：
- 生活片段：${review.lifeRecordCount} 条
- 日记：${review.diaryCount} 篇
- 记忆卡片：${review.memoryCardCount} 张
- 计划完成：${review.completedPlanCount}/${review.totalPlanCount}
${if (review.topKeywords.isNotEmpty()) "- 关键词：${review.topKeywords.joinToString("、")}" else ""}
${if (review.dominantMood != null) "- 主要情绪：${review.dominantMood}" else ""}

请用自然的中文总结这一周（不超过200字）：
""".trimIndent()

            val messages = listOf(
                LLMMessage(role = "system", content = systemPrompt),
                LLMMessage(role = "user", content = userPrompt)
            )
            val resp = provider.chat(messages, tools = null, temperature = 0.5f, maxTokens = 300)
            if (resp.content.isNotBlank()) {
                review.copy(summary = resp.content.trim().take(200))
            } else review
        } catch (_: Exception) {
            review // Fall back to rule-based
        }
    }

    // -- private --

    private fun buildRuleSummary(
        lifeRecordCount: Int,
        diaryCount: Int,
        memoryCardCount: Int,
        completedPlanCount: Int,
        totalPlanCount: Int,
        topKeywords: List<String>,
        dominantMood: String?
    ): String {
        if (lifeRecordCount == 0 && diaryCount == 0 && memoryCardCount == 0) {
            return "本周暂无记录。"
        }

        val sb = StringBuilder()
        sb.append("本周共记录 ${lifeRecordCount} 个生活片段")
        if (diaryCount > 0) sb.append("、${diaryCount} 篇日记")
        sb.append("。")

        if (dominantMood != null) {
            sb.append("主要情绪：${dominantMood}。")
        }

        if (topKeywords.isNotEmpty()) {
            sb.append("关键词：${topKeywords.joinToString("、")}。")
        }

        if (memoryCardCount > 0) {
            sb.append("收藏了 ${memoryCardCount} 条回忆。")
        }

        if (totalPlanCount > 0) {
            sb.append("${completedPlanCount}/${totalPlanCount} 个计划已完成。")
        }

        return sb.toString()
    }

    private fun isCJK(c: Char): Boolean = c in '一'..'鿿' || c in '㐀'..'䶿'

    private fun extractTopKeywords(texts: List<String>, topN: Int): List<String> {
        val wordCounts = mutableMapOf<String, Int>()
        for (text in texts) {
            val segments = text.split("\\s+".toRegex())
            for (seg in segments) {
                if (seg.isEmpty()) continue
                val hasCJK = seg.any { isCJK(it) }
                if (hasCJK) {
                    // Extract bigrams
                    if (seg.length >= 2) {
                        for (i in 0..seg.length - 2) {
                            val bg = seg.substring(i, i + 2)
                            val punctChars = setOf(',', '.', '!', '?', ';', ':', '"', '\'', '(', ')', '[', ']', '“', '”', '、', '。', '，', '．')
                            if (bg.length >= 2 && bg.none { it.isWhitespace() || it in punctChars }) {
                                val lower = bg.lowercase()
                                if (lower !in STOP_WORDS) {
                                    wordCounts[lower] = (wordCounts[lower] ?: 0) + 1
                                }
                            }
                        }
                    }
                } else {
                    // Whole word
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
}
