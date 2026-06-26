package com.example.myapplication.policy

import org.junit.Assert.*
import org.junit.Test

class MemoryRetrievalPolicyTest {

    private val policy = MemoryRetrievalPolicy

    // ── Scoring ──

    @Test
    fun `keywordScore returns positive for matching Chinese bigram`() {
        val score = policy.keywordScore("咖啡馆", "用户喜欢去咖啡馆工作")
        assertTrue("Should score positive for matching term", score > 0)
    }

    @Test
    fun `keywordScore returns zero for no match`() {
        val score = policy.keywordScore("动物园", "用户喜欢咖啡")
        assertEquals(0.0, score, 0.01)
    }

    @Test
    fun `keywordScore higher for multiple matches`() {
        val score1 = policy.keywordScore("咖啡", "咖啡很好喝")
        val score2 = policy.keywordScore("咖啡", "咖啡 咖啡 咖啡很好喝")
        assertTrue("Multiple matches should score higher", score2 > score1)
    }

    @Test
    fun `score applies source weight`() {
        val hit = MemoryRetrievalPolicy.SourceHit("profile", "用户画像", "id1", "snippet", 10.0)
        val hit2 = MemoryRetrievalPolicy.SourceHit("diary", "日记", "id2", "snippet", 10.0)
        assertTrue("Profile should score higher than diary", policy.score(hit) > policy.score(hit2))
    }

    @Test
    fun `score applies pinned bonus`() {
        val normal = MemoryRetrievalPolicy.SourceHit("memory_card", "记忆卡片", "id1", "s", 10.0, pinned = false)
        val pinned = MemoryRetrievalPolicy.SourceHit("memory_card", "记忆卡片", "id2", "s", 10.0, pinned = true)
        assertTrue("Pinned should score higher", policy.score(pinned) > policy.score(normal))
    }

    @Test
    fun `score applies time decay`() {
        val recent = MemoryRetrievalPolicy.SourceHit("life_record", "生活记录", "id1", "s", 10.0, ageDays = 1.0)
        val old = MemoryRetrievalPolicy.SourceHit("life_record", "生活记录", "id2", "s", 10.0, ageDays = 30.0)
        assertTrue("Recent should score higher than old", policy.score(recent) > policy.score(old))
    }

    // ── Sort ──

    @Test
    fun `sortByScore orders hits descending`() {
        val hits = listOf(
            MemoryRetrievalPolicy.SourceHit("diary", "日记", "1", "s", 1.0),
            MemoryRetrievalPolicy.SourceHit("profile", "用户画像", "2", "s", 10.0),
            MemoryRetrievalPolicy.SourceHit("memory_md", "长期记忆", "3", "s", 5.0)
        )
        val sorted = policy.sortByScore(hits)
        assertEquals("profile", sorted[0].sourceType)
        assertEquals("memory_md", sorted[1].sourceType)
        assertEquals("diary", sorted[2].sourceType)
    }

    // ── Filtering ──

    @Test
    fun `filterConfirmedEnabled excludes disabled profiles`() {
        val hits = listOf(
            MemoryRetrievalPolicy.SourceHit("profile", "用户画像", "id1", "小明", 10.0),
            MemoryRetrievalPolicy.SourceHit("profile", "用户画像", "id2", "小红", 8.0)
        )
        val statuses = mapOf(
            "id1" to MemoryRetrievalPolicy.StatusInfo(enabled = true, status = "confirmed"),
            "id2" to MemoryRetrievalPolicy.StatusInfo(enabled = false, status = "confirmed")
        )
        val filtered = policy.filterConfirmedEnabled(hits, statuses)
        assertEquals(1, filtered.size)
        assertEquals("id1", filtered[0].sourceId)
    }

    @Test
    fun `filterConfirmedEnabled excludes pending status`() {
        val hits = listOf(
            MemoryRetrievalPolicy.SourceHit("memory_card", "记忆卡片", "id1", "内容", 10.0)
        )
        val statuses = mapOf(
            "id1" to MemoryRetrievalPolicy.StatusInfo(enabled = true, status = "pending")
        )
        val filtered = policy.filterConfirmedEnabled(hits, statuses)
        assertTrue("Pending should be filtered out", filtered.isEmpty())
    }

    @Test
    fun `filterConfirmedEnabled includes confirmed and enabled`() {
        val hits = listOf(
            MemoryRetrievalPolicy.SourceHit("profile", "用户画像", "id1", "内容", 10.0)
        )
        val statuses = mapOf(
            "id1" to MemoryRetrievalPolicy.StatusInfo(enabled = true, status = "confirmed")
        )
        val filtered = policy.filterConfirmedEnabled(hits, statuses)
        assertEquals(1, filtered.size)
    }

    @Test
    fun `filterConfirmedEnabled default to confirmed when no status info`() {
        val hits = listOf(
            MemoryRetrievalPolicy.SourceHit("life_record", "生活记录", "id_missing", "内容", 10.0)
        )
        // LifeRecords don't have enabled/status — defaults to confirmed
        val filtered = policy.filterConfirmedEnabled(hits, emptyMap())
        assertEquals(1, filtered.size)
    }

    // ── Length capping ──

    @Test
    fun `capProfileSummary respects max length`() {
        val longText = "line ".repeat(100)
        val capped = policy.capProfileSummary(longText)
        assertTrue("Should cap to max length", capped.length <= MemoryRetrievalPolicy.MAX_PROFILE_SUMMARY + 5)
    }

    @Test
    fun `capProfileSummary returns empty for blank input`() {
        assertEquals("", policy.capProfileSummary(""))
        assertEquals("", policy.capProfileSummary("  \n  "))
    }

    @Test
    fun `capRelevantMemory includes source labels`() {
        val hits = listOf(
            MemoryRetrievalPolicy.SourceHit("profile", "用户画像", "id1", "用户叫小明", 10.0),
            MemoryRetrievalPolicy.SourceHit("life_record", "生活记录", "id2", "今天去了咖啡馆", 8.0)
        )
        val result = policy.capRelevantMemory(hits)
        assertTrue(result.contains("[用户画像]"))
        assertTrue(result.contains("[生活记录]"))
    }

    @Test
    fun `capMemoryPrefix empty for no hits`() {
        assertEquals("", policy.capMemoryPrefix(emptyList()))
    }

    @Test
    fun `capMemoryPrefix includes source labels`() {
        val hits = listOf(
            MemoryRetrievalPolicy.SourceHit("memory_md", "长期记忆", "id1", "snippet text", 10.0)
        )
        val result = policy.capMemoryPrefix(hits)
        assertTrue(result.contains("[Memory Search Results]"))
        assertTrue(result.contains("长期记忆"))
    }

    @Test
    fun `sortByScore respects source weights across types`() {
        // Same raw score — profile should beat diary due to source weight
        val hits = listOf(
            MemoryRetrievalPolicy.SourceHit("diary", "日记", "1", "s", 10.0),
            MemoryRetrievalPolicy.SourceHit("profile", "用户画像", "2", "s", 10.0)
        )
        val sorted = policy.sortByScore(hits)
        assertEquals("profile", sorted[0].sourceType)
    }

    // ── F4: Explain ──

    @Test
    fun `explainHit includes matched keywords`() {
        val hit = MemoryRetrievalPolicy.SourceHit("profile", "用户画像", "id1", "用户叫小明", 10.0)
        val explain = policy.explainHit(hit, "小明")
        assertTrue(explain.contains("小明"))
        assertTrue(explain.contains("命中"))
    }

    @Test
    fun `explainHit mentions pinned when applicable`() {
        val hit = MemoryRetrievalPolicy.SourceHit("memory_card", "记忆卡片", "id1", "coffee", 10.0, pinned = true)
        val explain = policy.explainHit(hit, "coffee")
        assertTrue(explain.contains("置顶"))
    }

    @Test
    fun `explainHit mentions recent when ageDays under 2`() {
        val hit = MemoryRetrievalPolicy.SourceHit("life_record", "生活记录", "id1", "today", 10.0, ageDays = 0.5)
        val explain = policy.explainHit(hit, "today")
        assertTrue(explain.contains("最近"))
    }

    @Test
    fun `explainHit mentions source label for high-weight types`() {
        val hit = MemoryRetrievalPolicy.SourceHit("profile", "用户画像", "id1", "test", 10.0)
        val explain = policy.explainHit(hit, "test")
        assertTrue(explain.contains("用户画像匹配"))
    }

    @Test
    fun `explainHit does not include raw score`() {
        val hit = MemoryRetrievalPolicy.SourceHit("memory_md", "长期记忆", "id1", "content", 99.5)
        val explain = policy.explainHit(hit, "content")
        assertFalse(explain.contains("99.5"))
        assertFalse(explain.contains("rawScore"))
    }

    @Test
    fun `findMatchedTerms returns matching query terms`() {
        val terms = policy.findMatchedTerms("咖啡 图书馆", "用户喜欢去咖啡馆")
        assertTrue(terms.contains("咖啡") || terms.contains("咖啡馆"))
    }

    @Test
    fun `findMatchedTerms returns empty for no match`() {
        val terms = policy.findMatchedTerms("动物园", "用户喜欢咖啡")
        assertTrue(terms.isEmpty())
    }
}
