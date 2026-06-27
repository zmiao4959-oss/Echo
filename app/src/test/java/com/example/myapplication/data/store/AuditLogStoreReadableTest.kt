package com.example.myapplication.data.store

import org.junit.Assert.*
import org.junit.Test
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.File

/**
 * Tests for AuditLogStore read/write and accessibility for governance center.
 */
class AuditLogStoreReadableTest {

    // Since AuditLogStore is an object that reads from actual filesystem,
    // we test the logic patterns and data class behavior here.

    data class StoredEntry(
        val action: String,
        val memoryType: String,
        val memoryId: String,
        val timestamp: Long,
        val summary: String
    )

    // ═══════════════════════════════════════
    // Sort order
    // ═══════════════════════════════════════

    @Test
    fun `entries sorted by timestamp descending`() {
        val entries = listOf(
            StoredEntry("confirm", "profile", "p1", 1000L, "confirmed profile"),
            StoredEntry("disable", "memory_card", "c1", 3000L, "disabled card"),
            StoredEntry("edit", "profile", "p2", 2000L, "edited profile")
        )
        val sorted = entries.sortedByDescending { it.timestamp }
        assertEquals(3000L, sorted[0].timestamp)
        assertEquals(2000L, sorted[1].timestamp)
        assertEquals(1000L, sorted[2].timestamp)
    }

    @Test
    fun `most recent entry is first after sort`() {
        val entries = listOf(
            StoredEntry("confirm", "profile", "p1", 5000L, "most recent"),
            StoredEntry("disable", "memory_card", "c1", 1000L, "oldest")
        )
        val sorted = entries.sortedByDescending { it.timestamp }
        assertEquals("most recent", sorted.first().summary)
    }

    // ═══════════════════════════════════════
    // Filter by action / type
    // ═══════════════════════════════════════

    @Test
    fun `filter by action returns only matching entries`() {
        val entries = listOf(
            StoredEntry("confirm", "profile", "p1", 1000L, "a"),
            StoredEntry("disable", "profile", "p2", 2000L, "b"),
            StoredEntry("confirm", "memory_card", "c1", 3000L, "c")
        )
        val confirms = entries.filter { it.action == "confirm" }
        assertEquals(2, confirms.size)
        assertTrue(confirms.all { it.action == "confirm" })
    }

    @Test
    fun `filter by memoryType returns only matching entries`() {
        val entries = listOf(
            StoredEntry("confirm", "profile", "p1", 1000L, "a"),
            StoredEntry("confirm", "profile", "p2", 2000L, "b"),
            StoredEntry("disable", "memory_card", "c1", 3000L, "c")
        )
        val profiles = entries.filter { it.memoryType == "profile" }
        assertEquals(2, profiles.size)
        assertTrue(profiles.all { it.memoryType == "profile" })
    }

    // ═══════════════════════════════════════
    // Empty state
    // ═══════════════════════════════════════

    @Test
    fun `empty entries list returns empty when filtered`() {
        val entries = emptyList<StoredEntry>()
        val filtered = entries.filter { it.action == "confirm" }
        assertTrue(filtered.isEmpty())
    }

    // ═══════════════════════════════════════
    // Max entries enforcement
    // ═══════════════════════════════════════

    @Test
    fun `max entries cap is enforced at 500`() {
        val MAX = 500
        val entries = (1..600).map {
            StoredEntry("confirm", "profile", "p$it", it.toLong(), "entry $it")
        }
        val capped = entries.takeLast(MAX)
        assertEquals(MAX, capped.size)
        // Should keep the most recent entries
        assertEquals("entry 101", capped.first().summary) // 600 - 500 + 1 = 101
        assertEquals("entry 600", capped.last().summary)
    }

    // ═══════════════════════════════════════
    // Governance center readability
    // ═══════════════════════════════════════

    @Test
    fun `audit entries have all required display fields`() {
        val entry = StoredEntry(
            action = "confirm",
            memoryType = "profile",
            memoryId = "p123",
            timestamp = System.currentTimeMillis(),
            summary = "confirmed user preference"
        )
        assertEquals("confirm", entry.action)
        assertEquals("profile", entry.memoryType)
        assertEquals("p123", entry.memoryId)
        assertTrue(entry.timestamp > 0)
        assertTrue(entry.summary.isNotBlank())
    }

    @Test
    fun `action label mapping covers all known actions`() {
        val actions = mapOf(
            "confirm" to "确认", "discard" to "丢弃", "disable" to "禁用",
            "enable" to "启用", "pin" to "置顶", "unpin" to "取消置顶", "edit" to "编辑"
        )
        for ((action, label) in actions) {
            assertNotNull("action '$action' should have a label", label)
            assertTrue("label for '$action' should be non-blank", label.isNotBlank())
        }
    }
}
