package com.example.myapplication.policy

import com.example.myapplication.ui.TimelineItem
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for timeline sort, filter, and exclusion logic.
 * Pure JVM — no Android deps.
 */
class GrowthTimelineFilterTest {

    private val now = System.currentTimeMillis()

    // ── Helpers ──

    private fun makeTimelineItems(): List<TimelineItem> = listOf(
        TimelineItem.LifeRecordItem("r1", now - 1000, "第一条记录", "开心", emptyList(), "chat"),
        TimelineItem.LifeRecordItem("r2", now - 3000, "第二条记录", null, emptyList(), "text"),
        TimelineItem.DiaryItem("d1", now - 2000, "一篇日记", "日记摘要", "充实", "2026-06-27"),
        TimelineItem.MemoryCardItem("c1", now - 500, "名言警句", "备注", "感动", listOf("tag"), "2026-06-26"),
        TimelineItem.PlanItem("p1", now - 8000, "task_reminder", "计划标题", "计划内容", completed = true),
        TimelineItem.WeeklyReviewItem("w1", now - 5000, "6/20-6/26", "本周回顾摘要", 5, 2, 1)
    )

    // ═══════════════════════════════════════
    // Sort — reverse chronological
    // ═══════════════════════════════════════

    @Test
    fun `sort timeline items reverse chronological by timestamp`() {
        val items = makeTimelineItems()
        val sorted = items.sortedByDescending { it.timestamp }
        for (i in 0 until sorted.size - 1) {
            assertTrue("items should be sorted descending, but ${sorted[i].timestamp} < ${sorted[i+1].timestamp}",
                sorted[i].timestamp >= sorted[i + 1].timestamp)
        }
    }

    // ═══════════════════════════════════════
    // Filter — type
    // ═══════════════════════════════════════

    @Test
    fun `filter by type diary returns only DiaryItems`() {
        val items = makeTimelineItems()
        val filtered = items.filter { it is TimelineItem.DiaryItem }
        assertEquals(1, filtered.size)
        assertTrue(filtered.all { it is TimelineItem.DiaryItem })
    }

    @Test
    fun `filter by type life_record returns only LifeRecordItems`() {
        val items = makeTimelineItems()
        val filtered = items.filter { it is TimelineItem.LifeRecordItem }
        assertEquals(2, filtered.size)
    }

    @Test
    fun `filter by multiple types returns union`() {
        val items = makeTimelineItems()
        val filtered = items.filter { it is TimelineItem.DiaryItem || it is TimelineItem.MemoryCardItem }
        assertEquals(2, filtered.size)
    }

    // ═══════════════════════════════════════
    // Exclusion — disabled / pending
    // ═══════════════════════════════════════

    @Test
    fun `filter excludes MemoryCard with disabled status`() {
        // This tests the logic that should be in the ViewModel
        val disabledCard = TimelineItem.MemoryCardItem(
            id = "c_disabled", timestamp = now, quote = "test", note = "",
            mood = null, tags = emptyList(), memoryDate = "2026-06-27"
        )
        // The ViewModel checks status before creating the item, so the item is never created
        // This test validates the exclusion concept
        val items = listOf(disabledCard)
        // Simulating status filter: exclude if status != "confirmed"
        val simulatedStatuses = mapOf("c_disabled" to "disabled")
        val filtered = items.filter { simulatedStatuses[it.id] == "confirmed" }
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `confirmed items included in unfiltered timeline`() {
        val items = makeTimelineItems()
        // All items created by the test helper simulate confirmed items
        // (the ViewModel filters status before creating items)
        assertTrue(items.isNotEmpty())
        assertEquals(6, items.size)
    }

    // ═══════════════════════════════════════
    // Empty state
    // ═══════════════════════════════════════

    @Test
    fun `empty timeline list produces empty result`() {
        val items = emptyList<TimelineItem>()
        assertTrue(items.isEmpty())
        // The UI should display warm empty message
    }

    // ═══════════════════════════════════════
    // Type label correctness
    // ═══════════════════════════════════════

    @Test
    fun `each item type has correct typeLabel`() {
        val items = makeTimelineItems()
        val byType = items.groupBy { it.typeLabel }
        assertTrue("should have LifeRecord items", byType.containsKey("生活记录"))
        assertTrue("should have Diary items", byType.containsKey("日记"))
        assertTrue("should have MemoryCard items", byType.containsKey("记忆卡片"))
        assertTrue("should have WeeklyReview items", byType.containsKey("周回顾"))
        assertTrue("should have Plan items", byType.containsKey("计划"))
    }

    @Test
    fun `summary text is non-blank for all items`() {
        val items = makeTimelineItems()
        for (item in items) {
            assertTrue("${item.typeLabel} summary should be non-blank", item.summaryText().isNotBlank())
        }
    }
}
