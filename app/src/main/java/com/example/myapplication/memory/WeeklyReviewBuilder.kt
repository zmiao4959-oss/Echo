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
import com.example.myapplication.policy.WeeklyAggregationPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Weekly review builder — aggregates last 7 days of data.
 *
 * Android layer handles repo access. Pure aggregation/delegation is in WeeklyAggregationPolicy.
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
        val summary: String,
        val lifeRecords: List<LifeRecord> = emptyList(),
        val diaries: List<DailyDiary> = emptyList(),
        val memoryCards: List<MemoryCard> = emptyList(),
        val completedPlans: List<com.example.myapplication.data.model.EchoPlan> = emptyList(),
        val pendingPlans: List<com.example.myapplication.data.model.EchoPlan> = emptyList()
    ) {
        val isEmpty: Boolean get() = lifeRecordCount == 0 && diaryCount == 0 && memoryCardCount == 0
    }

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

        val records = try { recordRepo.getByDateRange(startDate, endDate) } catch (_: Exception) { emptyList<LifeRecord>() }
        val diaries = try { diaryRepo.getByDateRange(startDate, endDate) } catch (_: Exception) { emptyList<DailyDiary>() }
        val cards = try {
            val all = memoryRepo.getAllCards().filter { it.status == "confirmed" }
            val weekAgo = now - 7 * 86_400_000L
            all.filter { it.createdAt >= weekAgo }
        } catch (_: Exception) { emptyList<MemoryCard>() }
        val allPlans = try { planRepo.getAll() } catch (_: Exception) { emptyList() }
        val weekAgo = now - 7 * 86_400_000L
        val completedPlans = allPlans.filter { (it.lastTriggeredAt ?: 0) >= weekAgo }

        val allText = records.map { it.content } + diaries.flatMap { listOf(it.title, it.summary, it.diaryText) }
        val moods = records.mapNotNull { it.mood } + cards.mapNotNull { it.mood }

        // Delegate to policy
        val input = WeeklyAggregationPolicy.ReviewInput(
            dateRange = "$startShort - $endShort",
            lifeRecordCount = records.size,
            diaryCount = diaries.size,
            memoryCardCount = cards.size,
            completedPlanCount = completedPlans.size,
            totalPlanCount = allPlans.size,
            topKeywords = emptyList(),
            dominantMood = WeeklyAggregationPolicy.dominantMood(moods),
            allTextForKeywords = allText
        )
        val output = WeeklyAggregationPolicy.buildRuleReview(input)

        val pendingPlansList = allPlans.filter { it.enabled && (it.lastTriggeredAt ?: 0) < weekAgo }

        WeeklyReview(
            dateRange = output.dateRange,
            lifeRecordCount = output.lifeRecordCount,
            diaryCount = output.diaryCount,
            memoryCardCount = output.memoryCardCount,
            completedPlanCount = output.completedPlanCount,
            totalPlanCount = output.totalPlanCount,
            topKeywords = output.topKeywords,
            dominantMood = output.dominantMood,
            summary = output.ruleSummary,
            lifeRecords = records,
            diaries = diaries,
            memoryCards = cards,
            completedPlans = completedPlans,
            pendingPlans = pendingPlansList
        )
    }

    suspend fun buildWithLLM(): WeeklyReview = withContext(Dispatchers.IO) {
        val review = build()
        if (review.isEmpty || review.summary == "本周暂无记录。") return@withContext review

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
            review
        }
    }
}
