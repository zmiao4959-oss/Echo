package com.example.myapplication.benchmark

import com.example.myapplication.ui.TimelineItem
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineBenchmarkTest {

    private val now = System.currentTimeMillis()

    @Test
    fun `sort 1000 mixed timeline items and paginate`() {
        val items = BenchmarkRunner.generate(1000) { i ->
            when (i % 5) {
                0 -> TimelineItem.LifeRecordItem("r$i", now - i * 1000L, "content $i", null, emptyList(), "text")
                1 -> TimelineItem.DiaryItem("d$i", now - i * 2000L, "title $i", "summary $i", "mood", "2026-06-01")
                2 -> TimelineItem.MemoryCardItem("c$i", now - i * 3000L, "quote $i", "", null, emptyList(), "2026-06-01")
                3 -> TimelineItem.PlanItem("p$i", now - i * 5000L, "task_reminder", "title $i", "msg", true)
                else -> TimelineItem.WeeklyReviewItem("w$i", now - i * 8000L, "week", "summary", 3, 1, 0)
            }
        }

        // Sort + paginate first page
        BenchmarkRunner.measure("timeline_sort_1000_page1", BenchmarkRunner.THRESHOLD_TIMELINE_SORT_1000) {
            val sorted = items.sortedByDescending { it.timestamp }
            val page1 = sorted.take(30)
            assertTrue(page1.size == 30)
        }

        // Filter by type
        BenchmarkRunner.measure("timeline_filter_1000", BenchmarkRunner.THRESHOLD_TIMELINE_SORT_1000) {
            val diaries = items.filter { it is TimelineItem.DiaryItem }
            assertTrue(diaries.isNotEmpty())
        }
    }
}
