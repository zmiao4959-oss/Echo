package com.example.myapplication.memory

import org.junit.Assert.*
import org.junit.Test

class MemoryMdParserTest {

    @Test
    fun `parse extracts confirmed section`() {
        val content = """
## ✅ 确认的记忆
用户叫小明。
用户在北京工作。
""".trimIndent()

        val parsed = MemoryMdParser.parse(content)
        assertTrue(parsed.confirmed.contains("用户叫小明"))
        assertTrue(parsed.confirmed.contains("用户在北京工作"))
        assertTrue(parsed.pending.isBlank())
        assertTrue(parsed.disabled.isBlank())
    }

    @Test
    fun `parse extracts all three sections`() {
        val content = """
## ✅ 确认的记忆
已确认的事实。

## ⏳ 待确认
待确认的事实。

## 🚫 已禁用
已禁用的事实。
""".trimIndent()

        val parsed = MemoryMdParser.parse(content)
        assertTrue(parsed.confirmed.contains("已确认的事实"))
        assertTrue(parsed.pending.contains("待确认的事实"))
        assertTrue(parsed.disabled.contains("已禁用的事实"))
    }

    @Test
    fun `readConfirmedSection excludes pending and disabled`() {
        val content = """
## ✅ 确认的记忆
line1
## ⏳ 待确认
line2
## 🚫 已禁用
line3
""".trimIndent()

        val confirmed = MemoryMdParser.readConfirmedSection(content)
        assertTrue(confirmed.contains("line1"))
        assertFalse(confirmed.contains("line2"))
        assertFalse(confirmed.contains("line3"))
    }

    @Test
    fun `readConfirmedSection includes prelude`() {
        val content = """
Some prelude content here.

## ✅ 确认的记忆
confirmed content.
""".trimIndent()

        val confirmed = MemoryMdParser.readConfirmedSection(content)
        assertTrue(confirmed.contains("Some prelude content"))
        assertTrue(confirmed.contains("confirmed content"))
    }

    @Test
    fun `migrateIfNeeded converts legacy marker`() {
        val content = """
## Echo 记住的关于你的事
旧格式的事实。
""".trimIndent()

        val migrated = MemoryMdParser.migrateIfNeeded(content)
        assertFalse(migrated.contains("## Echo 记住的关于你的事"))
        assertTrue(migrated.contains("## ✅ 确认的记忆"))
        assertTrue(migrated.contains("旧格式的事实"))
    }

    @Test
    fun `migrateIfNeeded no-op when new marker already present`() {
        val content = """
## ✅ 确认的记忆
已有新格式。
""".trimIndent()

        val migrated = MemoryMdParser.migrateIfNeeded(content)
        assertEquals(content, migrated)
    }

    @Test
    fun `appendFact creates section if missing`() {
        val content = "Some prelude text"
        val updated = MemoryMdParser.appendFact(content, "- [2026-06-26 14:30] [basic_info] test\n")
        assertTrue(updated.contains("## ✅ 确认的记忆"))
        assertTrue(updated.contains("test"))
    }

    @Test
    fun `appendFact appends to existing confirmed section`() {
        val content = """
## ✅ 确认的记忆
- [2026-06-26 14:00] [basic_info] 已有事实
""".trimIndent()

        val updated = MemoryMdParser.appendFact(content, "- [2026-06-26 14:30] [basic_info] 新事实\n")
        assertTrue(updated.contains("已有事实"))
        assertTrue(updated.contains("新事实"))
        // New fact should be inserted right after the section header (before existing facts)
        val newIdx = updated.indexOf("新事实")
        val oldIdx = updated.indexOf("已有事实")
        assertTrue("New fact should appear before existing fact", newIdx < oldIdx)
    }

    @Test
    fun `moveFact moves from confirmed to disabled`() {
        val content = """
## ✅ 确认的记忆
- [2026-06-26 14:30] [basic_info] 准备搬家
""".trimIndent()

        val updated = MemoryMdParser.moveFact(
            content,
            "- [2026-06-26 14:30] [basic_info] 准备搬家",
            MemoryMdParser.SECTION_CONFIRMED,
            MemoryMdParser.SECTION_DISABLED
        )
        val parsed = MemoryMdParser.parse(updated)
        assertFalse(parsed.confirmed.contains("准备搬家"))
        assertTrue(parsed.disabled.contains("准备搬家"))
    }

    @Test
    fun `moveFact no-op when fact not found`() {
        val content = """
## ✅ 确认的记忆
现有事实。
""".trimIndent()

        val updated = MemoryMdParser.moveFact(
            content,
            "不存在的行",
            MemoryMdParser.SECTION_CONFIRMED,
            MemoryMdParser.SECTION_DISABLED
        )
        assertEquals(content, updated)
    }

    @Test
    fun `parse with no markers treats all as prelude`() {
        val content = """
用户叫小明。
用户在北京工作。
""".trimIndent()

        val parsed = MemoryMdParser.parse(content)
        assertTrue(parsed.prelude.contains("小明"))
        assertTrue(parsed.confirmed.isBlank())
        assertTrue(parsed.pending.isBlank())
        assertTrue(parsed.disabled.isBlank())
    }

    @Test
    fun `parse with legacy marker treats content as confirmed`() {
        val content = """
## Echo 记住的关于你的事
旧格式的事实。
""".trimIndent()

        val parsed = MemoryMdParser.parse(content)
        assertTrue(parsed.confirmed.contains("旧格式的事实"))
        assertTrue(parsed.pending.isBlank())
        assertTrue(parsed.disabled.isBlank())
    }
}
