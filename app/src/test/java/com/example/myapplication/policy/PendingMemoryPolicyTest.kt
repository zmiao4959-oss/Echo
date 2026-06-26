package com.example.myapplication.policy

import org.junit.Assert.*
import org.junit.Test

class PendingMemoryPolicyTest {

    private val policy = PendingMemoryPolicy

    // ── Similarity / Dedup ──

    @Test
    fun `isSimilar true when new value contains discarded value`() {
        val discarded = listOf(
            PendingMemoryPolicy.DiscardedEntry("用户叫小明", "identity", 1000L)
        )
        assertTrue(policy.isSimilar("用户叫小明，在北京工作", "identity", discarded))
    }

    @Test
    fun `isSimilar true when discarded value contains new value`() {
        val discarded = listOf(
            PendingMemoryPolicy.DiscardedEntry("用户叫小明，在北京工作", "identity", 1000L)
        )
        assertTrue(policy.isSimilar("用户叫小明", "identity", discarded))
    }

    @Test
    fun `isSimilar false when different category`() {
        val discarded = listOf(
            PendingMemoryPolicy.DiscardedEntry("用户叫小明", "identity", 1000L)
        )
        assertFalse(policy.isSimilar("用户叫小明", "preference", discarded))
    }

    @Test
    fun `isSimilar false when completely different content`() {
        val discarded = listOf(
            PendingMemoryPolicy.DiscardedEntry("用户喜欢咖啡", "preference", 1000L)
        )
        assertFalse(policy.isSimilar("用户喜欢奶茶", "preference", discarded))
    }

    @Test
    fun `isSimilar false for empty discarded list`() {
        assertFalse(policy.isSimilar("anything", "identity", emptyList()))
    }

    // ── Dedup key ──

    @Test
    fun `dedupKey is lowercase and trimmed`() {
        val key = policy.dedupKey("  User Name  ", "IDENTITY")
        assertEquals("IDENTITY::user name", key)
    }

    @Test
    fun `hasDuplicateDedupKey true for exact match`() {
        val keys = setOf("identity::喜欢咖啡")
        assertTrue(policy.hasDuplicateDedupKey("喜欢咖啡", "identity", keys))
    }

    @Test
    fun `hasDuplicateDedupKey false for different key`() {
        val keys = setOf("identity::喜欢咖啡")
        assertFalse(policy.hasDuplicateDedupKey("喜欢奶茶", "identity", keys))
    }

    // ── Validation ──

    @Test
    fun `validatePendingInfo returns empty for valid entry`() {
        val issues = policy.validatePendingInfo("chat", "置信度较低", 0.6f)
        assertTrue("Should have no issues", issues.isEmpty())
    }

    @Test
    fun `validatePendingInfo reports missing source`() {
        val issues = policy.validatePendingInfo("", "some reason", 0.8f)
        assertTrue(issues.any { it.contains("来源") })
    }

    @Test
    fun `validatePendingInfo reports missing reason`() {
        val issues = policy.validatePendingInfo("chat", "", 0.8f)
        assertTrue(issues.any { it.contains("原因") })
    }

    @Test
    fun `validatePendingInfo reports out of range confidence`() {
        val issues = policy.validatePendingInfo("chat", "reason", 1.5f)
        assertTrue(issues.any { it.contains("置信度") })
    }

    // ── Reason formatting ──

    @Test
    fun `formatReason returns reason when provided`() {
        val result = policy.formatReason("用户提到喜欢下雨天", 0.6f)
        assertEquals("用户提到喜欢下雨天", result)
    }

    @Test
    fun `formatReason auto-generates for low confidence`() {
        val result = policy.formatReason("", 0.55f)
        assertTrue(result.contains("55%"))
        assertTrue(result.contains("较低"))
    }

    @Test
    fun `formatReason auto-generates generic for blank reason`() {
        val result = policy.formatReason("", 0.85f)
        assertEquals("需要用户确认", result)
    }

    // ── Sort by urgency ──

    @Test
    fun `sortPendingByUrgency lowest confidence first`() {
        val entries = listOf(
            PendingMemoryPolicy.PendingEntry("1", "a", "identity", 0.8f, "chat", "r"),
            PendingMemoryPolicy.PendingEntry("2", "b", "preference", 0.3f, "chat", "r"),
            PendingMemoryPolicy.PendingEntry("3", "c", "habit", 0.6f, "diary", "r")
        )
        val sorted = policy.sortPendingByUrgency(entries)
        assertEquals(0.3f, sorted[0].confidence)
        assertEquals(0.6f, sorted[1].confidence)
        assertEquals(0.8f, sorted[2].confidence)
    }

    // ── Source labels ──

    @Test
    fun `sourceLabel returns Chinese label for known source`() {
        assertEquals("对话记录", policy.sourceLabel("chat"))
        assertEquals("生活记录", policy.sourceLabel("life_record"))
        assertEquals("日记", policy.sourceLabel("diary"))
        assertEquals("手动添加", policy.sourceLabel("manual"))
    }

    @Test
    fun `sourceLabel returns unknown for empty source`() {
        assertEquals("未知来源", policy.sourceLabel(""))
    }

    @Test
    fun `sourceLabel returns raw value for unknown source`() {
        assertEquals("custom_source", policy.sourceLabel("custom_source"))
    }
}
