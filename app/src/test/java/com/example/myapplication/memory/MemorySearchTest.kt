package com.example.myapplication.memory

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MemorySearchTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    @Test
    fun `keywordSearch finds English term in MEMORY_md`() {
        val workspace = tmpDir.newFolder("workspace")
        File(workspace, "MEMORY.md").writeText("User profile: name is Alice, age is 25, likes basketball.")

        val results = MemorySearch.keywordSearch(workspace, "Alice")
        assertEquals("Should find 1 result", 1, results.size)
        assertTrue("Score should be positive", results[0].score > 0.0)
        assertTrue("Should contain match", results[0].snippet.contains("Alice"))
    }

    @Test
    fun `keywordSearch finds Chinese term via bigram tokenization`() {
        val workspace = tmpDir.newFolder("workspace")
        File(workspace, "MEMORY.md").writeText("用户喜欢去咖啡馆工作，周末经常去图书馆。")

        val results = MemorySearch.keywordSearch(workspace, "咖啡馆")
        assertEquals("Should find Chinese result", 1, results.size)
        assertTrue("Score positive", results[0].score > 0.0)
        assertTrue("Snippet contains match", results[0].snippet.contains("咖啡馆"))
    }

    @Test
    fun `keywordSearch finds Chinese short match`() {
        val workspace = tmpDir.newFolder("workspace")
        File(workspace, "MEMORY.md").writeText("我叫小明，今年25岁。")

        val results = MemorySearch.keywordSearch(workspace, "小明")
        assertEquals(1, results.size)
        assertTrue(results[0].snippet.contains("小明"))
    }

    @Test
    fun `keywordSearch searches memory_subdirectory`() {
        val workspace = tmpDir.newFolder("workspace")
        val memoryDir = File(workspace, "memory")
        memoryDir.mkdirs()
        File(memoryDir, "preferences.md").writeText("Favorite color is blue, likes noodles.")

        val results = MemorySearch.keywordSearch(workspace, "blue")
        assertEquals("Should find in memory/ subdirectory", 1, results.size)
        assertTrue(results[0].snippet.contains("blue"))
    }

    @Test
    fun `keywordSearch ranks by score`() {
        val workspace = tmpDir.newFolder("workspace")
        File(workspace, "MEMORY.md").writeText("hello hello hello")
        val memoryDir = File(workspace, "memory")
        memoryDir.mkdirs()
        File(memoryDir, "other.md").writeText("hello")

        val results = MemorySearch.keywordSearch(workspace, "hello")
        assertEquals(2, results.size)
        assertTrue("First result should have higher score", results[0].score > results[1].score)
    }

    @Test
    fun `keywordSearch with empty workspace returns empty`() {
        val workspace = tmpDir.newFolder("empty_workspace")
        val results = MemorySearch.keywordSearch(workspace, "anything")
        assertTrue("Empty workspace should return empty", results.isEmpty())
    }

    @Test
    fun `keywordSearch with empty query returns empty`() {
        val workspace = tmpDir.newFolder("workspace")
        File(workspace, "MEMORY.md").writeText("some content here")
        val results = MemorySearch.keywordSearch(workspace, "")
        assertTrue("Empty query should return empty", results.isEmpty())
    }

    @Test
    fun `keywordSearch respects maxResults`() {
        val workspace = tmpDir.newFolder("workspace")
        File(workspace, "MEMORY.md").writeText("common term here")
        val memoryDir = File(workspace, "memory")
        memoryDir.mkdirs()
        File(memoryDir, "a.md").writeText("common term here too")
        File(memoryDir, "b.md").writeText("common term again")

        val results = MemorySearch.keywordSearch(workspace, "common", maxResults = 2)
        assertTrue("Should respect maxResults", results.size <= 2)
    }

    @Test
    fun `keywordSearch snippet does not exceed length`() {
        val workspace = tmpDir.newFolder("workspace")
        val longContent = "line1\n".repeat(50) + "target line\n" + "line2\n".repeat(50)
        File(workspace, "MEMORY.md").writeText(longContent)

        val results = MemorySearch.keywordSearch(workspace, "target")
        assertEquals(1, results.size)
        assertTrue("Snippet should be <= 410 chars", results[0].snippet.length <= 410)
    }

    @Test
    fun `keywordSearch with Chinese mixed English`() {
        val workspace = tmpDir.newFolder("workspace")
        File(workspace, "MEMORY.md").writeText("用户 Alice 在北京工作，喜欢喝咖啡。")

        val results = MemorySearch.keywordSearch(workspace, "Alice 咖啡")
        assertTrue("Should find results for mixed query", results.isNotEmpty())
    }

    @Test
    fun `keywordSearch ignores disabled facts section in MEMORY_md`() {
        val workspace = tmpDir.newFolder("workspace")
        File(workspace, "MEMORY.md").writeText("""
## ✅ 确认的记忆
用户叫小明，在北京工作。

## 🚫 已禁用
用户叫小红，在上海工作。
""".trimIndent())

        val results = MemorySearch.keywordSearch(workspace, "小明")
        assertTrue("Should find active fact", results.isNotEmpty())

        val resultsDisabled = MemorySearch.keywordSearch(workspace, "小红")
        assertTrue("Should NOT find disabled fact", resultsDisabled.isEmpty())
    }

    @Test
    fun `keywordSearch does not return results for disabled section content`() {
        val workspace = tmpDir.newFolder("workspace")
        File(workspace, "MEMORY.md").writeText("""
## ✅ 确认的记忆
用户叫小明。

## 🚫 已禁用
用户叫小红。
""".trimIndent())

        val results = MemorySearch.keywordSearch(workspace, "小红")
        assertTrue("Disabled section content should not be searchable", results.isEmpty())
    }

    @Test
    fun `keywordSearch skips disabled section in MEMORY_md`() {
        val workspace = tmpDir.newFolder("workspace")
        File(workspace, "MEMORY.md").writeText("""
## ✅ 确认的记忆
用户喜欢咖啡。

## 🚫 已禁用
用户喜欢奶茶。
""".trimIndent())

        val results = MemorySearch.keywordSearch(workspace, "奶茶")
        assertTrue("Search for disabled-only term should return empty", results.isEmpty())
    }

    @Test
    fun `keywordSearch skips pending section in MEMORY_md`() {
        val workspace = tmpDir.newFolder("workspace")
        File(workspace, "MEMORY.md").writeText("""
## ✅ 确认的记忆
用户叫小明。

## ⏳ 待确认
用户可能25岁。
""".trimIndent())

        val results = MemorySearch.keywordSearch(workspace, "25岁")
        assertTrue("Pending section content should not be searchable", results.isEmpty())
    }

    @Test
    fun `keywordSearch searches only confirmed section in MEMORY_md`() {
        val workspace = tmpDir.newFolder("workspace")
        File(workspace, "MEMORY.md").writeText("""
## ✅ 确认的记忆
用户在北京工作。

## ⏳ 待确认
用户可能喜欢游泳。

## 🚫 已禁用
用户以前在上海。
""".trimIndent())

        // Should find confirmed
        val results = MemorySearch.keywordSearch(workspace, "北京")
        assertEquals(1, results.size)

        // Should NOT find pending
        val resultsPending = MemorySearch.keywordSearch(workspace, "游泳")
        assertTrue(resultsPending.isEmpty())

        // Should NOT find disabled
        val resultsDisabled = MemorySearch.keywordSearch(workspace, "上海")
        assertTrue(resultsDisabled.isEmpty())
    }

    @Test
    fun `keywordSearch with legacy marker treats content as confirmed`() {
        val workspace = tmpDir.newFolder("workspace")
        File(workspace, "MEMORY.md").writeText("""
## Echo 记住的关于你的事
用户叫小明。
""".trimIndent())

        // Legacy content should be treated as confirmed
        val results = MemorySearch.keywordSearch(workspace, "小明")
        assertEquals(1, results.size)
        assertTrue(results[0].snippet.contains("小明"))
    }

    @Test
    fun `confirmed memory_md files searchable pending ones not`() {
        val workspace = tmpDir.newFolder("workspace")
        val memoryDir = File(workspace, "memory")
        memoryDir.mkdirs()
        File(memoryDir, "card1.md").writeText("""
## ✅ 确认的记忆
重要回忆内容。
""".trimIndent())
        File(memoryDir, "card2.md").writeText("""
## ⏳ 待确认
待定回忆内容。
""".trimIndent())

        val confirmedResults = MemorySearch.keywordSearch(workspace, "重要回忆")
        assertEquals(1, confirmedResults.size)

        // Use a term unique to the pending section (won't match anything in confirmed)
        val pendingResults = MemorySearch.keywordSearch(workspace, "待定")
        assertTrue("Pending-only content should not be searchable", pendingResults.isEmpty())
    }
}
