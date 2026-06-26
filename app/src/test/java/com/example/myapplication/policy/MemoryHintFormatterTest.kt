package com.example.myapplication.policy

import org.junit.Assert.*
import org.junit.Test

class MemoryHintFormatterTest {

    private val fmt = MemoryHintFormatter

    // ── Hint formatting ──

    @Test
    fun `formatHint returns null for empty sources`() {
        assertNull(fmt.formatHint(emptyList()))
    }

    @Test
    fun `formatHint formats single source type`() {
        val sources = listOf(
            MemoryHintFormatter.HintSource("profile", "用户画像", "用户叫小明", "confirmed", true)
        )
        val hint = fmt.formatHint(sources)
        assertNotNull(hint)
        assertTrue(hint!!.contains("参考了"))
        assertTrue(hint.contains("用户画像"))
    }

    @Test
    fun `formatHint formats multiple source types`() {
        val sources = listOf(
            MemoryHintFormatter.HintSource("profile", "用户画像", "用户叫小明", "confirmed", true),
            MemoryHintFormatter.HintSource("life_record", "生活记录", "今天去了咖啡馆", "confirmed", true),
            MemoryHintFormatter.HintSource("life_record", "生活记录", "今天下雨", "confirmed", true)
        )
        val hint = fmt.formatHint(sources)
        assertNotNull(hint)
        assertTrue(hint!!.contains("用户画像"))
        assertTrue(hint.contains("生活记录"))
        // Should say "2 条生活记录"
        assertTrue(hint.contains("2"))
    }

    // ── Privacy: disabled/pending not in hints ──

    @Test
    fun `formatHint excludes disabled sources`() {
        val sources = listOf(
            MemoryHintFormatter.HintSource("profile", "用户画像", "secret1", "confirmed", true),
            MemoryHintFormatter.HintSource("profile", "用户画像", "secret2", "confirmed", false)  // disabled
        )
        val hint = fmt.formatHint(sources)
        assertNotNull(hint)
        // Should only count 1, not 2
        assertFalse(hint!!.contains("2 条用户画像"))
    }

    @Test
    fun `formatHint excludes pending sources`() {
        val sources = listOf(
            MemoryHintFormatter.HintSource("memory_card", "记忆卡片", "内容", "confirmed", true),
            MemoryHintFormatter.HintSource("memory_card", "记忆卡片", "待确认内容", "pending", true)
        )
        val hint = fmt.formatHint(sources)
        assertNotNull(hint)
        assertFalse(hint!!.contains("2 条记忆卡片"))
    }

    @Test
    fun `formatHint returns null when all sources are disabled`() {
        val sources = listOf(
            MemoryHintFormatter.HintSource("profile", "用户画像", "s", "disabled", false),
            MemoryHintFormatter.HintSource("memory_card", "记忆卡片", "s", "disabled", true)
        )
        assertNull(fmt.formatHint(sources))
    }

    // ── Sanitization ──

    @Test
    fun `sanitizeSnippet caps length`() {
        val long = "a".repeat(100)
        val sanitized = fmt.sanitizeSnippet(long, 30)
        assertTrue(sanitized.length <= 33)  // 30 + "…"
    }

    @Test
    fun `sanitizeSnippet preserves short snippets`() {
        val short = "hello"
        assertEquals("hello", fmt.sanitizeSnippet(short, 30))
    }

    // ── Eligibility ──

    @Test
    fun `shouldIncludeInHint true for confirmed and enabled`() {
        val source = MemoryHintFormatter.HintSource("profile", "用户画像", "s", "confirmed", true)
        assertTrue(fmt.shouldIncludeInHint(source))
    }

    @Test
    fun `shouldIncludeInHint false for disabled`() {
        val source = MemoryHintFormatter.HintSource("profile", "用户画像", "s", "confirmed", false)
        assertFalse(fmt.shouldIncludeInHint(source))
    }

    @Test
    fun `shouldIncludeInHint false for pending`() {
        val source = MemoryHintFormatter.HintSource("memory_card", "记忆卡片", "s", "pending", true)
        assertFalse(fmt.shouldIncludeInHint(source))
    }

    // ── verifyNoLeaked ──

    @Test
    fun `verifyNoLeaked true when all confirmed and enabled`() {
        val sources = listOf(
            MemoryHintFormatter.HintSource("profile", "用户画像", "s", "confirmed", true),
            MemoryHintFormatter.HintSource("life_record", "生活记录", "s", "confirmed", true)
        )
        assertTrue(fmt.verifyNoLeaked(sources))
    }

    @Test
    fun `verifyNoLeaked false when pending present`() {
        val sources = listOf(
            MemoryHintFormatter.HintSource("profile", "用户画像", "s", "confirmed", true),
            MemoryHintFormatter.HintSource("profile", "用户画像", "s", "pending", true)
        )
        assertFalse(fmt.verifyNoLeaked(sources))
    }

    // ── countByType ──

    @Test
    fun `countByType groups eligible sources correctly`() {
        val sources = listOf(
            MemoryHintFormatter.HintSource("profile", "用户画像", "s1", "confirmed", true),
            MemoryHintFormatter.HintSource("profile", "用户画像", "s2", "confirmed", true),
            MemoryHintFormatter.HintSource("life_record", "生活记录", "s3", "confirmed", true),
            MemoryHintFormatter.HintSource("memory_card", "记忆卡片", "s4", "pending", true)
        )
        val counts = fmt.countByType(sources)
        assertEquals(2, counts["用户画像"])
        assertEquals(1, counts["生活记录"])
        assertNull(counts["记忆卡片"])  // pending → excluded
    }

    // ── eligibleSources ──

    @Test
    fun `eligibleSources filters out disabled and pending`() {
        val sources = listOf(
            MemoryHintFormatter.HintSource("p1", "用户画像", "a", "confirmed", true),
            MemoryHintFormatter.HintSource("p2", "用户画像", "b", "pending", true),
            MemoryHintFormatter.HintSource("p3", "用户画像", "c", "confirmed", false)
        )
        val eligible = fmt.eligibleSources(sources)
        assertEquals(1, eligible.size)
        assertEquals("a", eligible[0].snippet)
    }
}
