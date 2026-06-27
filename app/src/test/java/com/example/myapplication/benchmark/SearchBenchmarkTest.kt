package com.example.myapplication.benchmark

import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.policy.MemoryRetrievalPolicy
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchBenchmarkTest {

    private val now = System.currentTimeMillis()

    @Test
    fun `score 1000 records with keyword query`() {
        val records = BenchmarkRunner.generate(1000) { i ->
            LifeRecord("r$i", now, "2026-06-01", "这是第${i}条记录 关于咖啡和工作的日常", "text")
        }

        BenchmarkRunner.measure("search_1000_records", BenchmarkRunner.THRESHOLD_SEARCH_1000) {
            val query = "咖啡 工作"
            val scored = records.map { r ->
                MemoryRetrievalPolicy.keywordScore(query, r.content) to r
            }.filter { it.first > 0 }.sortedByDescending { it.first }
            assertTrue(scored.isNotEmpty())
        }
    }

    @Test
    fun `filter 500 memory cards by status`() {
        val cards = BenchmarkRunner.generate(500) { i ->
            val status = when {
                i % 5 == 0 -> "pending"
                i % 7 == 0 -> "disabled"
                else -> "confirmed"
            }
            MemoryCard("c$i", now, "2026-06-01", "quote $i", "note $i",
                emptyList(), null, "chat", "s$i", i % 3 == 0, 0.8f, status)
        }

        BenchmarkRunner.measure("card_filter_500", BenchmarkRunner.THRESHOLD_CARD_FILTER_500) {
            val confirmed = cards.filter { it.status == "confirmed" }
            val pinned = cards.filter { it.pinned }
            assertTrue(confirmed.isNotEmpty())
        }
    }
}
