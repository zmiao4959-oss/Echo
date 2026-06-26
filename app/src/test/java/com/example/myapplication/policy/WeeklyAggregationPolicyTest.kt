package com.example.myapplication.policy

import org.junit.Assert.*
import org.junit.Test

class WeeklyAggregationPolicyTest {

    private val policy = WeeklyAggregationPolicy

    // ── No records → no fabrication ──

    @Test
    fun `buildRuleReview with empty data returns empty summary`() {
        val input = WeeklyAggregationPolicy.ReviewInput(
            dateRange = "6/20 - 6/26",
            lifeRecordCount = 0, diaryCount = 0, memoryCardCount = 0,
            completedPlanCount = 0, totalPlanCount = 0,
            topKeywords = emptyList(), dominantMood = null,
            allTextForKeywords = emptyList()
        )
        val output = policy.buildRuleReview(input)
        assertEquals("本周暂无记录。", output.ruleSummary)
        assertTrue(output.isEmpty)
        assertTrue(output.topKeywords.isEmpty())
    }

    @Test
    fun `buildRuleReview with no records does not fabricate keywords`() {
        val input = WeeklyAggregationPolicy.ReviewInput(
            dateRange = "6/20 - 6/26",
            lifeRecordCount = 0, diaryCount = 0, memoryCardCount = 0,
            completedPlanCount = 0, totalPlanCount = 0,
            topKeywords = emptyList(), dominantMood = null,
            allTextForKeywords = emptyList()
        )
        val output = policy.buildRuleReview(input)
        assertTrue("Should not fabricate keywords", output.topKeywords.isEmpty())
        assertTrue("Summary should indicate no records", output.ruleSummary.contains("暂无记录"))
    }

    // ── With data → rule-based aggregation ──

    @Test
    fun `buildRuleReview with records generates meaningful summary`() {
        val input = WeeklyAggregationPolicy.ReviewInput(
            dateRange = "6/20 - 6/26",
            lifeRecordCount = 5, diaryCount = 2, memoryCardCount = 1,
            completedPlanCount = 3, totalPlanCount = 5,
            topKeywords = emptyList(), dominantMood = "开心",
            allTextForKeywords = listOf("今天去了咖啡馆", "喜欢下雨天", "咖啡馆工作")
        )
        val output = policy.buildRuleReview(input)
        assertTrue(output.ruleSummary.contains("5 个生活片段"))
        assertTrue(output.ruleSummary.contains("2 篇日记"))
        assertTrue(output.ruleSummary.contains("开心"))
        assertFalse(output.ruleSummary.contains("本周暂无记录"))
    }

    @Test
    fun `buildRuleReview with MemoryCards mentions card count`() {
        val input = WeeklyAggregationPolicy.ReviewInput(
            dateRange = "6/20 - 6/26",
            lifeRecordCount = 0, diaryCount = 0, memoryCardCount = 3,
            completedPlanCount = 0, totalPlanCount = 0,
            topKeywords = emptyList(), dominantMood = null,
            allTextForKeywords = emptyList()
        )
        val output = policy.buildRuleReview(input)
        assertTrue(output.ruleSummary.contains("3 条回忆"))
    }

    @Test
    fun `buildRuleReview with plans mentions completion ratio`() {
        val input = WeeklyAggregationPolicy.ReviewInput(
            dateRange = "6/20 - 6/26",
            lifeRecordCount = 1, diaryCount = 0, memoryCardCount = 0,
            completedPlanCount = 4, totalPlanCount = 6,
            topKeywords = emptyList(), dominantMood = null,
            allTextForKeywords = listOf("test")
        )
        val output = policy.buildRuleReview(input)
        assertTrue(output.ruleSummary.contains("4/6 个计划已完成"))
    }

    // ── Keyword extraction ──

    @Test
    fun `extractTopKeywords returns meaningful bigrams`() {
        val texts = listOf("用户喜欢去咖啡馆工作", "周末经常去图书馆", "咖啡馆环境很好")
        val keywords = policy.extractTopKeywords(texts, 3)
        assertTrue("Should extract '咖啡馆' related keyword", keywords.isNotEmpty())
        // "咖啡馆" → bigrams: "咖啡", "啡馆" → both should appear
        assertTrue(keywords.any { it.contains("咖啡") || it.contains("啡馆") })
    }

    @Test
    fun `extractTopKeywords filters stop words`() {
        val texts = listOf("今天我去了一个很好的地方")
        val keywords = policy.extractTopKeywords(texts, 10)
        assertFalse("Stop word '今天' should be filtered", keywords.any { it == "今天" })
        assertFalse("Stop word '一个' should be filtered", keywords.any { it == "一个" })
    }

    @Test
    fun `extractTopKeywords returns empty for empty input`() {
        val keywords = policy.extractTopKeywords(emptyList(), 5)
        assertTrue(keywords.isEmpty())
    }

    // ── Dominant mood ──

    @Test
    fun `dominantMood returns mode`() {
        val moods = listOf("开心", "平静", "开心", "开心", "疲惫")
        assertEquals("开心", policy.dominantMood(moods))
    }

    @Test
    fun `dominantMood returns null for empty list`() {
        assertNull(policy.dominantMood(emptyList()))
    }

    // ── Summary validation ──

    @Test
    fun `containsOnlyProvidedContent validates record count in summary`() {
        val input = WeeklyAggregationPolicy.ReviewInput(
            dateRange = "6/20 - 6/26",
            lifeRecordCount = 5, diaryCount = 0, memoryCardCount = 0,
            completedPlanCount = 0, totalPlanCount = 0,
            topKeywords = emptyList(), dominantMood = null,
            allTextForKeywords = emptyList()
        )
        val output = policy.buildRuleReview(input)
        assertTrue("Summary should contain actual record count",
            policy.containsOnlyProvidedContent(output.ruleSummary, input))
    }

    @Test
    fun `capForNotification respects max length`() {
        val longSummary = "a".repeat(500)
        val capped = policy.capForNotification(longSummary, 200)
        assertEquals(200, capped.length)
    }

    @Test
    fun `capForNotification preserves short summary`() {
        val short = "本周暂无记录。"
        assertEquals(short, policy.capForNotification(short, 200))
    }

    // ── Rule summary property checks ──

    @Test
    fun `buildRuleReview output not empty when has data`() {
        val input = WeeklyAggregationPolicy.ReviewInput(
            dateRange = "6/20 - 6/26",
            lifeRecordCount = 1, diaryCount = 0, memoryCardCount = 0,
            completedPlanCount = 0, totalPlanCount = 0,
            topKeywords = emptyList(), dominantMood = null,
            allTextForKeywords = listOf("something")
        )
        val output = policy.buildRuleReview(input)
        assertFalse("Should not be empty", output.isEmpty)
    }

    @Test
    fun `buildRuleReview dateRange preserved`() {
        val input = WeeklyAggregationPolicy.ReviewInput(
            dateRange = "6/20 - 6/26",
            lifeRecordCount = 0, diaryCount = 0, memoryCardCount = 0,
            completedPlanCount = 0, totalPlanCount = 0,
            topKeywords = emptyList(), dominantMood = null,
            allTextForKeywords = emptyList()
        )
        val output = policy.buildRuleReview(input)
        assertEquals("6/20 - 6/26", output.dateRange)
    }
}
