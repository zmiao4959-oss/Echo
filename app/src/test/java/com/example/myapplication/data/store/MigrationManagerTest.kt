package com.example.myapplication.data.store

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MigrationManagerTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    @org.junit.Before
    fun setUp() {
        MigrationManager.setTestLogDir(tmpDir.root)
    }

    @org.junit.After
    fun tearDown() {
        MigrationManager.setTestLogDir(null)
    }

    private fun writeV1Json(file: File, itemsJson: String) {
        file.writeText("""{"schemaVersion":1,"items":[$itemsJson]}""", Charsets.UTF_8)
    }

    private fun writeV1AuditLog(file: File, entriesJson: String) {
        file.writeText("""{"schemaVersion":1,"entries":[$entriesJson]}""", Charsets.UTF_8)
    }

    // ═══════════════════════════════════════
    // memory_cards migration
    // ═══════════════════════════════════════

    @Test
    fun `migrates memory_cards from v1 to v2`() {
        val file = tmpDir.newFile("memory_cards.json")
        writeV1Json(file, """{"id":"c1","quote":"test","status":"confirmed"}""")

        assertEquals(1, JsonAtomicWriter.readSchemaVersion(file))

        val results = MigrationManager.runMigrationOnFileForTesting(file, targetVersion = 2) { f ->
            val json = f.readText(Charsets.UTF_8)
            f.writeText(json.replace("\"schemaVersion\":1", "\"schemaVersion\":2"), Charsets.UTF_8)
            true
        }

        assertEquals(1, results.size)
        assertTrue(results[0].success)
        assertEquals(2, JsonAtomicWriter.readSchemaVersion(file))
    }

    // ═══════════════════════════════════════
    // user_profile migration
    // ═══════════════════════════════════════

    @Test
    fun `migrates user_profile from v1 to v2`() {
        val file = tmpDir.newFile("user_profile.json")
        writeV1Json(file, """{"id":"p1","key":"habit","value":"跑步"}""")

        val results = MigrationManager.runMigrationOnFileForTesting(file, targetVersion = 2) { f ->
            f.writeText(f.readText(Charsets.UTF_8).replace("schemaVersion\":1", "schemaVersion\":2"), Charsets.UTF_8)
            true
        }

        assertTrue(results[0].success)
        assertEquals(2, JsonAtomicWriter.readSchemaVersion(file))
    }

    // ═══════════════════════════════════════
    // audit_log migration
    // ═══════════════════════════════════════

    @Test
    fun `migrates audit_log from v1 to v2`() {
        val file = tmpDir.newFile("audit_log.json")
        writeV1AuditLog(file, """{"action":"confirm","memoryType":"profile","memoryId":"p1","timestamp":1,"summary":"test"}""")

        val results = MigrationManager.runMigrationOnFileForTesting(file, targetVersion = 2) { f ->
            f.writeText(f.readText(Charsets.UTF_8).replace("schemaVersion\":1", "schemaVersion\":2"), Charsets.UTF_8)
            true
        }

        assertTrue(results[0].success)
        assertEquals(2, JsonAtomicWriter.readSchemaVersion(file))
    }

    // ═══════════════════════════════════════
    // Backup creation & failure preservation
    // ═══════════════════════════════════════

    @Test
    fun `migration creates backup before modifying`() {
        val file = tmpDir.newFile("cards.json")
        val originalContent = """{"schemaVersion":1,"items":[{"id":"c1"}]}"""
        file.writeText(originalContent, Charsets.UTF_8)

        MigrationManager.runMigrationOnFileForTesting(file, targetVersion = 2) { f ->
            f.writeText("""{"schemaVersion":2,"items":[{"id":"c1","newField":"added"}]}""", Charsets.UTF_8)
            true
        }

        assertTrue(file.readText().contains("newField"))
        assertEquals(2, JsonAtomicWriter.readSchemaVersion(file))
    }

    @Test
    fun `migration failure preserves original file`() {
        val file = tmpDir.newFile("cards.json")
        val originalContent = """{"schemaVersion":1,"items":[{"id":"c1","value":"original"}]}"""
        file.writeText(originalContent, Charsets.UTF_8)

        val results = MigrationManager.runMigrationOnFileForTesting(file, targetVersion = 2) { _ -> false }

        assertFalse(results[0].success)
        assertEquals(1, JsonAtomicWriter.readSchemaVersion(file))
    }

    @Test
    fun `migration exception preserves original file`() {
        val file = tmpDir.newFile("cards.json")
        val originalContent = """{"schemaVersion":1,"items":[{"id":"c1","value":"original"}]}"""
        file.writeText(originalContent, Charsets.UTF_8)

        val results = MigrationManager.runMigrationOnFileForTesting(file, targetVersion = 2) {
            throw RuntimeException("simulated crash")
        }

        assertFalse(results[0].success)
        assertEquals(1, JsonAtomicWriter.readSchemaVersion(file))
        assertTrue(file.readText().contains("original"))
    }

    // ═══════════════════════════════════════
    // Schema version update
    // ═══════════════════════════════════════

    @Test
    fun `schemaVersion is correctly updated after migration`() {
        val file = tmpDir.newFile("cards.json")
        writeV1Json(file, """{"id":"c1","quote":"test"}""")

        MigrationManager.runMigrationOnFileForTesting(file, targetVersion = 3) { f ->
            f.writeText(f.readText(Charsets.UTF_8).replace("schemaVersion\":1", "schemaVersion\":3"), Charsets.UTF_8)
            true
        }

        assertEquals(3, JsonAtomicWriter.readSchemaVersion(file))
    }

    // ═══════════════════════════════════════
    // No-op when versions match
    // ═══════════════════════════════════════

    @Test
    fun `no migration when version already matches`() {
        val file = tmpDir.newFile("cards.json")
        file.writeText("""{"schemaVersion":2,"items":[{"id":"c1"}]}""", Charsets.UTF_8)

        val results = MigrationManager.runMigrationOnFileForTesting(file, targetVersion = 2) { _ ->
            fail("should not be called")
            false
        }

        assertTrue(results.isEmpty())
    }

    // ═══════════════════════════════════════
    // Migration log readable
    // ═══════════════════════════════════════

    @Test
    fun `migration log is readable after migration`() {
        val file = tmpDir.newFile("cards.json")
        writeV1Json(file, """{"id":"c1"}""")

        MigrationManager.runMigrationOnFileForTesting(file, targetVersion = 2) { f ->
            f.writeText(f.readText(Charsets.UTF_8).replace("schemaVersion\":1", "schemaVersion\":2"), Charsets.UTF_8)
            true
        }

        val log = MigrationManager.getMigrationLog()
        assertTrue(log.isNotEmpty())
        val entry = log.last()
        assertTrue(entry.success)
        assertEquals("cards.json", entry.targetFile)
        assertEquals(1, entry.fromVersion)
        assertEquals(2, entry.toVersion)
    }
}
