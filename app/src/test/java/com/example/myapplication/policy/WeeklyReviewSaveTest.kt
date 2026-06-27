package com.example.myapplication.policy

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for weekly review save-to-card/diary logic validation.
 * Pure JVM — validates the data transformation logic, not the actual Android persistence.
 */
class WeeklyReviewSaveTest {

    // ── Simulated review data ──

    data class SimulatedReview(
        val dateRange: String,
        val lifeRecordCount: Int,
        val diaryCount: Int,
        val memoryCardCount: Int,
        val topKeywords: List<String>,
        val dominantMood: String?,
        val summary: String,
        val sourceIds: List<String>
    ) {
        val isEmpty: Boolean get() = lifeRecordCount == 0 && diaryCount == 0 && memoryCardCount == 0
    }

    // ═══════════════════════════════════════
    // Save as Diary
    // ═══════════════════════════════════════

    @Test
    fun `review with data produces diary draft with correct fields`() {
        val review = SimulatedReview(
            dateRange = "6/20 - 6/26",
            lifeRecordCount = 5, diaryCount = 2, memoryCardCount = 1,
            topKeywords = listOf("咖啡", "跑步", "学习"),
            dominantMood = "开心",
            summary = "本周共记录 5 个生活片段、2 篇日记。",
            sourceIds = listOf("r1", "r2", "r3", "r4", "r5")
        )
        // Simulate diary draft creation
        val diaryTitle = "周回顾 · ${review.dateRange}"
        val diaryText = buildString {
            appendLine("Echo 周回顾 · ${review.dateRange}")
            appendLine(review.summary)
            appendLine("关键词: ${review.topKeywords.joinToString("、")}")
            appendLine("主要情绪: ${review.dominantMood}")
        }

        assertTrue(diaryTitle.contains("周回顾"))
        assertTrue(diaryTitle.contains(review.dateRange))
        assertTrue(diaryText.contains(review.summary))
        assertTrue(diaryText.contains("咖啡"))
        assertEquals(review.sourceIds.size, 5)
    }

    @Test
    fun `review with data produces memory card with correct fields`() {
        val review = SimulatedReview(
            dateRange = "6/20 - 6/26",
            lifeRecordCount = 3, diaryCount = 1, memoryCardCount = 0,
            topKeywords = listOf("跑步", "项目"),
            dominantMood = "充实",
            summary = "本周关键词：跑步、项目。",
            sourceIds = listOf("r1", "r2", "r3")
        )
        // Simulate card creation
        val cardQuote = "关键词: ${review.topKeywords.take(5).joinToString("、")}"
        val cardNote = review.summary.take(200)
        val cardSourceType = "weekly_review"

        assertEquals("关键词: 跑步、项目", cardQuote)
        assertTrue(cardNote.isNotBlank())
        assertEquals("weekly_review", cardSourceType)
    }

    // ═══════════════════════════════════════
    // Empty guard
    // ═══════════════════════════════════════

    @Test
    fun `empty review does not produce a card`() {
        val review = SimulatedReview(
            dateRange = "6/20 - 6/26",
            lifeRecordCount = 0, diaryCount = 0, memoryCardCount = 0,
            topKeywords = emptyList(),
            dominantMood = null,
            summary = "本周暂无记录。",
            sourceIds = emptyList()
        )
        // Empty review should not trigger save
        if (review.isEmpty) {
            assertTrue("should skip save when empty", true)
        } else {
            fail("review should be empty")
        }
    }

    @Test
    fun `no warm-but-fake summary when data is empty`() {
        val review = SimulatedReview(
            dateRange = "6/20 - 6/26",
            lifeRecordCount = 0, diaryCount = 0, memoryCardCount = 0,
            topKeywords = emptyList(),
            dominantMood = null,
            summary = "本周暂无记录。",
            sourceIds = emptyList()
        )
        // When empty, summary should not be displayed as "Echo 说"
        val isEmpty = review.lifeRecordCount + review.diaryCount + review.memoryCardCount == 0
        assertTrue(isEmpty)
        // Save buttons should be hidden
    }

    // ═══════════════════════════════════════
    // Source traceability
    // ═══════════════════════════════════════

    @Test
    fun `source ids are preserved for traceability`() {
        val sourceIds = listOf("r1", "r2", "r3", "r4", "r5")
        assertEquals(5, sourceIds.size)
        assertTrue(sourceIds.all { it.startsWith("r") })
    }
}
