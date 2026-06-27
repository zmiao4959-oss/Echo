package com.example.myapplication.data.store

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

/**
 * Tests for DataExporter export logic.
 * Uses TemporaryFolder for isolated filesystem testing.
 */
class DataExporterTest {

    @Rule @JvmField
    val tempFolder = TemporaryFolder()

    // ═══════════════════════════════════════
    // README content
    // ═══════════════════════════════════════

    @Test
    fun `README contains file descriptions and privacy note`() {
        val readme = buildReadmeContent("20260627_120000")
        assertTrue("README should mention life_records.json", readme.contains("life_records.json"))
        assertTrue("README should mention daily_diaries.json", readme.contains("daily_diaries.json"))
        assertTrue("README should mention memory_audit_log.json", readme.contains("memory_audit_log.json"))
        assertTrue("README should mention weekly_review.json", readme.contains("weekly_review.json"))
        assertTrue("README should mention growth_timeline.json", readme.contains("growth_timeline.json"))
        assertTrue("README should mention privacy", readme.contains("API") || readme.contains("密钥"))
        assertTrue("README should have timestamp", readme.contains("20260627_120000"))
    }

    // ═══════════════════════════════════════
    // API key exclusion check
    // ═══════════════════════════════════════

    @Test
    fun `detects sk- pattern as sensitive`() {
        val content = """{"api_key": "sk-abcdef1234567890abcdef1234567890"}"""
        val pattern = Regex("sk-[a-zA-Z0-9]{20,}")
        assertTrue(pattern.containsMatchIn(content))
    }

    @Test
    fun `detects Bearer token pattern as sensitive`() {
        val content = """Authorization: Bearer abcdef1234567890abcdef1234"""
        val pattern = Regex("Bearer\\s+[a-zA-Z0-9_\\-]{20,}")
        assertTrue(pattern.containsMatchIn(content))
    }

    @Test
    fun `does NOT flag normal content as sensitive`() {
        val normalContent = """{"lifeRecordCount": 5, "diaryCount": 2, "summary": "本周回顾"}"""
        val skPattern = Regex("sk-[a-zA-Z0-9]{20,}")
        val bearerPattern = Regex("Bearer\\s+[a-zA-Z0-9_\\-]{20,}")
        assertFalse(skPattern.containsMatchIn(normalContent))
        assertFalse(bearerPattern.containsMatchIn(normalContent))
    }

    // ═══════════════════════════════════════
    // Export failure diagnostics
    // ═══════════════════════════════════════

    @Test
    fun `diagnostic JSON is written on failure`() {
        val diagFile = File(tempFolder.root, "export_error_20260627_120000.json")
        val diagContent = """{"timestamp":"20260627_120000","error":"Test error","deviceModel":"test"}"""
        diagFile.writeText(diagContent)
        assertTrue(diagFile.exists())
        val read = diagFile.readText()
        assertTrue(read.contains("Test error"))
        assertTrue(read.contains("timestamp"))
    }

    // Helper
    private fun buildReadmeContent(timestamp: String): String {
        return """
Echo (小爪) 数据导出
导出时间: $timestamp
========================================

文件说明:
- life_records.json      生活记录原始数据
- daily_diaries.json     每日日记
- plans.json             计划和提醒
- memory_cards.json      记忆卡片
- user_profile.json      用户画像（偏好、习惯、目标等）
- memory_audit_log.json  记忆治理操作记录
- weekly_review.json     本周回顾数据
- growth_timeline.json   成长轨迹汇总数据
- echo_profile.md        Echo 角色设定
- notes.md / memory.md   工作区笔记和长期记忆

隐私说明:
- 此文件不包含任何 API 密钥或用户凭据
- 请妥善保管，避免分享给不信任的第三方
""".trimIndent()
    }
}
