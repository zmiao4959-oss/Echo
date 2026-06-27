package com.example.myapplication.diagnostics

import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.data.model.UserProfileMemory
import com.example.myapplication.data.store.AuditLogStore
import com.example.myapplication.data.store.EchoFileStore
import com.example.myapplication.data.store.JsonAtomicWriter
import com.example.myapplication.memory.FileStore
import com.example.myapplication.memory.MemoryMdParser
import com.example.myapplication.search.SearchIndex
import java.io.File

/**
 * 数据健康检查器。
 * 扫描所有数据文件，分 info/warning/error 三级，支持一键修复安全项。
 */
object DataHealthChecker {

    /** 测试用：覆盖数据根目录。null 恢复默认。 */
    private var testDataDir: File? = null

    fun setTestDataDir(dir: File?) { testDataDir = dir }
    fun resetForTesting() { testDataDir = null }

    /** 解析文件路径：测试环境用 testDataDir，否则用 EchoFileStore */
    private fun dataFile(relativePath: String): File {
        testDataDir?.let { return File(it, relativePath) }
        return when (relativePath) {
            "records/life_records.json" -> EchoFileStore.lifeRecordsFile
            "diaries/daily_diaries.json" -> EchoFileStore.dailyDiariesFile
            "plans/plans.json" -> EchoFileStore.plansFile
            "memories/memory_cards.json" -> EchoFileStore.memoryCardsFile
            "memories/user_profile.json" -> EchoFileStore.userProfileFile
            else -> File(EchoFileStore.echoDir, relativePath)
        }
    }

    private fun sessionsDir(): File =
        testDataDir?.let { File(it, "sessions") }
            ?: File(EchoFileStore.echoDir.parentFile, "sessions")

    enum class Severity { INFO, WARNING, ERROR }

    data class HealthFinding(
        val checkId: String,
        val name: String,
        val severity: Severity,
        val message: String,
        val fixable: Boolean = false,
        val fixLabel: String? = null
    )

    data class HealthReport(
        val timestamp: Long,
        val findings: List<HealthFinding>
    ) {
        val summary: String get() {
            val errors = findings.count { it.severity == Severity.ERROR }
            val warnings = findings.count { it.severity == Severity.WARNING }
            val infos = findings.count { it.severity == Severity.INFO }
            val fixableCount = findings.count { it.fixable }
            return "$errors errors, $warnings warnings, $infos info — $fixableCount fixable"
        }
    }

    /** 运行全部检查 */
    fun runAllChecks(): HealthReport {
        val findings = mutableListOf<HealthFinding>()
        findings.addAll(checkJsonIntegrity())
        findings.addAll(checkSessions())
        findings.addAll(checkMemoryMdSections())
        findings.addAll(checkOrphanCardRefs())
        findings.addAll(checkDuplicateProfileKeys())
        findings.addAll(checkAuditLogSize())
        findings.addAll(checkSearchIndexStale())
        findings.addAll(checkExportFiles())
        return HealthReport(System.currentTimeMillis(), findings)
    }

    /** 一键修复 */
    fun applyFix(checkId: String): HealthFinding? {
        return when {
            checkId.startsWith("json_corruption_") -> fixJsonCorruption(checkId.removePrefix("json_corruption_"))
            checkId == "session_corruption" -> fixSessionCorruption()
            checkId == "memory_md_section_format" -> fixMemoryMdSections()
            checkId == "audit_log_oversized" -> fixAuditLogSize()
            checkId == "search_index_stale" -> fixSearchIndex()
            else -> null
        }
    }

    /** 快速摘要（诊断页用） */
    fun quickSummary(): String = runAllChecks().summary

    // ── 1. JSON 完整性 ──

    private fun checkJsonIntegrity(): List<HealthFinding> {
        val results = mutableListOf<HealthFinding>()
        val files = mapOf(
            "life_records" to dataFile("records/life_records.json"),
            "daily_diaries" to dataFile("diaries/daily_diaries.json"),
            "plans" to dataFile("plans/plans.json"),
            "memory_cards" to dataFile("memories/memory_cards.json"),
            "user_profile" to dataFile("memories/user_profile.json")
        )
        for ((name, file) in files) {
            if (!file.exists()) continue
            try {
                val json = file.readText(Charsets.UTF_8)
                if (json.isBlank()) {
                    results.add(HealthFinding("json_corruption_$name", "$name JSON 文件为空",
                        Severity.ERROR, "文件存在但内容为空", fixable = true, fixLabel = "创建空文件"))
                } else {
                    // Also verify it's valid JSON
                    com.google.gson.Gson().fromJson(json, com.google.gson.JsonObject::class.java)
                }
            } catch (e: Exception) {
                results.add(HealthFinding("json_corruption_$name", "$name JSON 损坏",
                    Severity.ERROR, e.message ?: "读取失败", fixable = true, fixLabel = "备份并重建"))
            }
        }
        return results
    }

    private fun fixJsonCorruption(name: String): HealthFinding {
        val file = dataFile(when (name) {
            "life_records" -> "records/life_records.json"
            "daily_diaries" -> "diaries/daily_diaries.json"
            "plans" -> "plans/plans.json"
            "memory_cards" -> "memories/memory_cards.json"
            "user_profile" -> "memories/user_profile.json"
            else -> return HealthFinding("json_corruption_$name", name, Severity.ERROR, "未知文件", fixable = false)
        })
        try {
            JsonAtomicWriter.backupCorrupted(file)
            file.writeText("{\"schemaVersion\":1,\"items\":[]}", Charsets.UTF_8)
            return HealthFinding("json_corruption_$name", "$name JSON 已修复",
                Severity.INFO, "已备份旧文件并创建空文件", fixable = false)
        } catch (e: Exception) {
            return HealthFinding("json_corruption_$name", "$name 修复失败",
                Severity.ERROR, e.message ?: "", fixable = false)
        }
    }

    // ── 2. Session 完整性 ──

    private fun checkSessions(): List<HealthFinding> {
        val results = mutableListOf<HealthFinding>()
        val sDir = sessionsDir()
        if (!sDir.isDirectory) return results
        val corruptCount = sDir.listFiles()?.count { f ->
            f.extension == "json" && try {
                f.readText(Charsets.UTF_8).isBlank()
            } catch (e: Exception) { true }
        } ?: 0
        if (corruptCount > 0) {
            results.add(HealthFinding("session_corruption", "Session 文件损坏",
                Severity.WARNING, "$corruptCount 个 session 文件损坏", fixable = true, fixLabel = "备份损坏文件"))
        }
        return results
    }

    private fun fixSessionCorruption(): HealthFinding {
        val sDir = sessionsDir()
        var fixed = 0
        sDir.listFiles()?.forEach { f ->
            if (f.extension == "json" && try { f.readText(Charsets.UTF_8).isBlank() } catch (e: Exception) { true }) {
                val bak = File("${f.absolutePath}.bak.${System.currentTimeMillis()}")
                f.renameTo(bak)
                fixed++
            }
        }
        return HealthFinding("session_corruption", "Session 文件已修复",
            Severity.INFO, "已备份 $fixed 个损坏文件", fixable = false)
    }

    // ── 3. MEMORY.md section 格式 ──

    private fun checkMemoryMdSections(): List<HealthFinding> {
        val results = mutableListOf<HealthFinding>()
        val md = readMemoMdFile()
        if (md.isBlank()) return results
        if (!md.contains(MemoryMdParser.SECTION_CONFIRMED) &&
            md.contains("## Echo 记住的关于你的事")) {
            results.add(HealthFinding("memory_md_section_format", "MEMORY.md 旧格式",
                Severity.INFO, "仍使用旧版 section 标记，建议迁移", fixable = true, fixLabel = "自动迁移"))
        }
        return results
    }

    private fun fixMemoryMdSections(): HealthFinding {
        val md = readMemoMdFile()
        val updated = MemoryMdParser.migrateIfNeeded(md)
        val mdFile = testDataDir?.let { File(it, "workspace/MEMORY.md") }
            ?: File(FileStore.workspaceDir, "MEMORY.md")
        mdFile.parentFile?.mkdirs()
        mdFile.writeText(updated, Charsets.UTF_8)
        return HealthFinding("memory_md_section_format", "MEMORY.md 已迁移",
            Severity.INFO, "section 标记已更新为新格式", fixable = false)
    }

    private fun readMemoMdFile(): String {
        testDataDir?.let {
            val f = File(it, "workspace/MEMORY.md")
            return if (f.exists()) f.readText(Charsets.UTF_8) else ""
        }
        return try { FileStore.readWorkspaceFile("MEMORY.md") } catch (_: Exception) { "" }
    }

    // ── 4. Orphan MemoryCard 引用 ──

    private fun checkOrphanCardRefs(): List<HealthFinding> {
        val results = mutableListOf<HealthFinding>()
        try {
            val cards = JsonAtomicWriter.readItems<MemoryCard>(dataFile("memories/memory_cards.json"))
            // Check if sourceType/sourceId pairs reference existing data
            // Simple check: just flag cards with empty sourceId as suspicious
            val orphan = cards.count { it.sourceId.isBlank() || it.sourceType.isBlank() }
            if (orphan > 0) {
                results.add(HealthFinding("orphan_memory_card_refs", "可疑的卡片引用",
                    Severity.WARNING, "$orphan 张卡片缺少来源信息", fixable = false))
            }
        } catch (e: Exception) {
            results.add(HealthFinding("orphan_memory_card_refs", "无法检查卡片引用",
                Severity.WARNING, e.message ?: "", fixable = false))
        }
        return results
    }

    // ── 5. 重复 profile key ──

    private fun checkDuplicateProfileKeys(): List<HealthFinding> {
        val results = mutableListOf<HealthFinding>()
        try {
            val profiles = JsonAtomicWriter.readItems<UserProfileMemory>(dataFile("memories/user_profile.json"))
            val dupes = profiles.groupBy { it.key }.filter { it.value.size > 1 }
            if (dupes.isNotEmpty()) {
                results.add(HealthFinding("duplicate_profile_keys", "重复画像 key",
                    Severity.WARNING, "${dupes.size} 个 key 有重复条目: ${dupes.keys.joinToString(", ")}", fixable = false))
            }
        } catch (e: Exception) { /* file error handled by json_corruption check */ }
        return results
    }

    // ── 6. Audit log 过大 ──

    private fun checkAuditLogSize(): List<HealthFinding> {
        val results = mutableListOf<HealthFinding>()
        try {
            val entries = kotlinx.coroutines.runBlocking { AuditLogStore.readAll() }
            if (entries.size >= 500) {
                results.add(HealthFinding("audit_log_oversized", "审计日志过大",
                    Severity.INFO, "审计日志有 ${entries.size} 条（上限 500），建议清理", fixable = true, fixLabel = "裁剪到 500"))
            }
        } catch (e: Exception) { /* handled elsewhere */ }
        return results
    }

    private fun fixAuditLogSize(): HealthFinding {
        try {
            kotlinx.coroutines.runBlocking {
                val entries = AuditLogStore.readAll()
                if (entries.size > 500) {
                    AuditLogStore.clear()
                    AuditLogStore.append(entries.takeLast(500).map {
                        com.example.myapplication.policy.MemoryGovernanceService.AuditEntry(
                            action = it.action, memoryType = it.memoryType, memoryId = it.memoryId,
                            timestamp = it.timestamp, summary = it.summary
                        )
                    })
                }
            }
            return HealthFinding("audit_log_oversized", "审计日志已裁剪",
                Severity.INFO, "已裁剪到最近 500 条", fixable = false)
        } catch (e: Exception) {
            return HealthFinding("audit_log_oversized", "裁剪失败",
                Severity.ERROR, e.message ?: "", fixable = false)
        }
    }

    // ── 7. 索引过期 ──

    private fun checkSearchIndexStale(): List<HealthFinding> {
        val results = mutableListOf<HealthFinding>()
        if (SearchIndex.entryCount() == 0) {
            results.add(HealthFinding("search_index_stale", "搜索索引未构建",
                Severity.INFO, "启动时将自动构建", fixable = true, fixLabel = "立即重建"))
        } else if (SearchIndex.isStale()) {
            results.add(HealthFinding("search_index_stale", "搜索索引过期",
                Severity.INFO, "数据已变更，索引需更新", fixable = true, fixLabel = "重建索引"))
        }
        return results
    }

    private fun fixSearchIndex(): HealthFinding {
        return try {
            kotlinx.coroutines.runBlocking { SearchIndex.rebuild() }
            HealthFinding("search_index_stale", "索引已重建",
                Severity.INFO, "搜索索引已更新", fixable = false)
        } catch (e: Exception) {
            HealthFinding("search_index_stale", "索引重建失败",
                Severity.ERROR, e.message ?: "", fixable = false)
        }
    }

    // ── 8. 导出文件完整性 ──

    private fun checkExportFiles(): List<HealthFinding> {
        val results = mutableListOf<HealthFinding>()
        val expected = listOf(
            dataFile("records/life_records.json"), dataFile("diaries/daily_diaries.json"),
            dataFile("plans/plans.json"), dataFile("memories/memory_cards.json"), dataFile("memories/user_profile.json")
        )
        val missing = expected.filter { !it.exists() }
        if (missing.isNotEmpty()) {
            results.add(HealthFinding("export_missing_files", "导出缺少文件",
                Severity.WARNING, "缺少: ${missing.joinToString { it.name }}", fixable = false))
        }
        return results
    }

    // ── StoredEntry adapter for AuditLogStore ──

    // AuditLogStore uses its own StoredEntry; we access it via readAll()
    // The fix uses AuditLogStore.append() which expects MemoryGovernanceService.AuditEntry
    // This is a type mismatch but structurally compatible since AuditLogStore stores
    // its entries as its own StoredEntry and readAll() returns them as AuditEntry.
    // The fixAuditLogSize above handles this by reconstructing AuditEntry objects.
}
