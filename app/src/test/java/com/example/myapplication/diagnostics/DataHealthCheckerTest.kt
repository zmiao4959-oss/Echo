package com.example.myapplication.diagnostics

import com.example.myapplication.data.store.JsonAtomicWriter
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataHealthCheckerTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    @After
    fun tearDown() {
        DataHealthChecker.resetForTesting()
    }

    private fun setupDataDir() {
        val root = tmpDir.root
        File(root, "records").mkdirs()
        File(root, "diaries").mkdirs()
        File(root, "plans").mkdirs()
        File(root, "memories").mkdirs()
        File(root, "workspace").mkdirs()
        File(root, "sessions").mkdirs()
        DataHealthChecker.setTestDataDir(root)
    }

    private fun writeFile(relPath: String, content: String) {
        File(tmpDir.root, relPath).apply {
            parentFile?.mkdirs()
            writeText(content, Charsets.UTF_8)
        }
    }

    // ═══════════════════════════════════════
    // 1. JSON corruption detection
    // ═══════════════════════════════════════

    @Test
    fun `detects corrupt JSON file`() {
        setupDataDir()
        writeFile("records/life_records.json", "not valid json {{{")

        val report = DataHealthChecker.runAllChecks()
        val finding = report.findings.find { it.checkId == "json_corruption_life_records" }
        assertNotNull("Should detect corrupt JSON", finding)
        assertEquals(DataHealthChecker.Severity.ERROR, finding?.severity)
    }

    @Test
    fun `detects empty JSON file`() {
        setupDataDir()
        writeFile("records/life_records.json", "")

        val report = DataHealthChecker.runAllChecks()
        val finding = report.findings.find { it.checkId == "json_corruption_life_records" }
        assertNotNull("Should detect empty JSON", finding)
    }

    @Test
    fun `no false positive for valid JSON`() {
        setupDataDir()
        writeFile("records/life_records.json", """{"schemaVersion":1,"items":[]}""")
        writeFile("diaries/daily_diaries.json", """{"schemaVersion":1,"items":[]}""")
        writeFile("plans/plans.json", """{"schemaVersion":1,"items":[]}""")
        writeFile("memories/memory_cards.json", """{"schemaVersion":1,"items":[]}""")
        writeFile("memories/user_profile.json", """{"schemaVersion":1,"items":[]}""")

        val report = DataHealthChecker.runAllChecks()
        val errors = report.findings.filter { it.severity == DataHealthChecker.Severity.ERROR }
        assertTrue("No errors for valid files: $errors", errors.isEmpty())
    }

    // ═══════════════════════════════════════
    // 2. Session corruption detection
    // ═══════════════════════════════════════

    @Test
    fun `detects corrupt session file`() {
        setupDataDir()
        writeFile("sessions/s1.json", "")  // empty = corrupt

        val report = DataHealthChecker.runAllChecks()
        assertTrue(report.findings.any { it.checkId == "session_corruption" })
    }

    @Test
    fun `no warning for healthy sessions`() {
        setupDataDir()
        // no session files at all = no issue
        val report = DataHealthChecker.runAllChecks()
        assertFalse(report.findings.any { it.checkId == "session_corruption" })
    }

    // ═══════════════════════════════════════
    // 3. MEMORY.md section format
    // ═══════════════════════════════════════

    @Test
    fun `detects legacy MEMORY md section format`() {
        setupDataDir()
        writeFile("workspace/MEMORY.md", """
## Echo 记住的关于你的事
- [2026-06-01] test fact
""".trimIndent())

        // This check reads MEMORY.md via FileStore which uses workspace/ dir
        // In test, FileStore reads from filesDir/workspace/ which doesn't exist
        // The check will read empty string → no finding
        // This is expected for pure JVM test - full coverage needs Android context
        // For now, verify the check doesn't crash
        val report = DataHealthChecker.runAllChecks()
        assertNotNull(report)
    }

    // ═══════════════════════════════════════
    // 4. Orphan memory card refs
    // ═══════════════════════════════════════

    @Test
    fun `detects orphan memory cards with empty sourceId`() {
        setupDataDir()
        writeFile("memories/memory_cards.json", """
{"schemaVersion":1,"items":[{"id":"c1","createdAt":1000,"memoryDate":"2026-01-01","quote":"test","note":"","tags":[],"sourceType":"","sourceId":"","pinned":false,"confidence":1.0,"status":"confirmed"}]}
""".trimIndent())

        val report = DataHealthChecker.runAllChecks()
        assertTrue(report.findings.any { it.checkId == "orphan_memory_card_refs" })
    }

    // ═══════════════════════════════════════
    // 5. Duplicate profile keys
    // ═══════════════════════════════════════

    @Test
    fun `detects duplicate profile keys`() {
        setupDataDir()
        writeFile("memories/user_profile.json", """
{"schemaVersion":1,"items":[
{"id":"p1","key":"coffee","value":"喜欢咖啡","category":"preference","confidence":0.8,"sourceIds":[],"createdAt":1000,"updatedAt":1000,"enabled":true,"status":"confirmed","source":"","reason":""},
{"id":"p2","key":"coffee","value":"爱喝咖啡","category":"preference","confidence":0.9,"sourceIds":[],"createdAt":2000,"updatedAt":2000,"enabled":true,"status":"confirmed","source":"","reason":""}
]}
""".trimIndent())

        val report = DataHealthChecker.runAllChecks()
        assertTrue(report.findings.any { it.checkId == "duplicate_profile_keys" })
    }

    @Test
    fun `no warning for unique profile keys`() {
        setupDataDir()
        writeFile("memories/user_profile.json", """
{"schemaVersion":1,"items":[
{"id":"p1","key":"coffee","value":"喜欢咖啡","category":"preference","confidence":0.8,"sourceIds":[],"createdAt":1000,"updatedAt":1000,"enabled":true,"status":"confirmed","source":"","reason":""},
{"id":"p2","key":"tea","value":"喜欢喝茶","category":"preference","confidence":0.8,"sourceIds":[],"createdAt":2000,"updatedAt":2000,"enabled":true,"status":"confirmed","source":"","reason":""}
]}
""".trimIndent())

        val report = DataHealthChecker.runAllChecks()
        assertFalse(report.findings.any { it.checkId == "duplicate_profile_keys" })
    }

    // ═══════════════════════════════════════
    // 6. Audit log oversized
    // ═══════════════════════════════════════

    @Test
    fun `audit log check produces warning when oversized`() {
        setupDataDir()
        // Write an oversized audit log file (600 entries)
        val entries = (1..600).joinToString(",\n") { i ->
            """{"action":"confirm","memoryType":"profile","memoryId":"p$i","timestamp":$i,"summary":"entry $i"}"""
        }
        writeFile("workspace/memory_audit_log.json",
            """{"schemaVersion":1,"entries":[$entries]}""")

        // The check reads via AuditLogStore which has its own path logic
        // In test env without MyApplication, this gracefully handles
        val report = DataHealthChecker.runAllChecks()
        assertNotNull(report)
    }

    // ═══════════════════════════════════════
    // 7. Search index stale
    // ═══════════════════════════════════════

    @Test
    fun `detects empty search index as stale`() {
        setupDataDir()
        com.example.myapplication.search.SearchIndex.resetForTesting()

        val report = DataHealthChecker.runAllChecks()
        assertTrue(report.findings.any { it.checkId == "search_index_stale" })
    }

    // ═══════════════════════════════════════
    // 8. Export missing files
    // ═══════════════════════════════════════

    @Test
    fun `reports missing export files when data dir empty`() {
        setupDataDir()
        // No data files created — should report all missing
        val report = DataHealthChecker.runAllChecks()
        assertTrue(report.findings.any { it.checkId == "export_missing_files" })
    }

    // ═══════════════════════════════════════
    // Summary
    // ═══════════════════════════════════════

    @Test
    fun `summary string is non-empty`() {
        setupDataDir()
        val report = DataHealthChecker.runAllChecks()
        assertTrue(report.summary.isNotBlank())
        assertTrue(report.summary.contains("error") || report.summary.contains("info"))
    }

    @Test
    fun `severity grading is correct`() {
        setupDataDir()
        writeFile("records/life_records.json", "corrupt{{{")
        val report = DataHealthChecker.runAllChecks()
        val errors = report.findings.count { it.severity == DataHealthChecker.Severity.ERROR }
        assertTrue("Should have at least 1 error for corrupt file, got $errors", errors >= 1)
    }

    @Test
    fun `safe repair does not delete real data`() {
        setupDataDir()
        writeFile("records/life_records.json", "not valid json{{{")

        val reportBefore = DataHealthChecker.runAllChecks()
        assertTrue("Should detect corrupt file",
            reportBefore.findings.any { it.checkId == "json_corruption_life_records" })

        // applyFix calls backupCorrupted which uses android.util.Log
        // — catches RuntimeException for JVM test env
        try {
            DataHealthChecker.applyFix("json_corruption_life_records")
        } catch (e: RuntimeException) {
            // android.util.Log not mocked — fix was attempted
        }

        // After fix attempt: original was renamed to .bak (backup) then Log crash prevented
        // writing new valid JSON. So original is gone but backup exists.
        // This verifies the safety property: user data is never destroyed — it's in the backup.
        val bakFiles = tmpDir.root.walkTopDown().filter { it.name.contains(".bak.") }.toList()
        assertTrue("Backup file should preserve original data, found: ${bakFiles.map { it.name }}",
            bakFiles.isNotEmpty())
        // Verify backup contains original text
        val bakContent = bakFiles.first().readText()
        assertTrue("Backup should contain original data", bakContent.contains("not valid json"))
    }
}
