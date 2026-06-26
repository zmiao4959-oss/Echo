package com.example.myapplication.data.store

import com.example.myapplication.policy.MemoryGovernanceService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persistent storage for memory governance audit log.
 *
 * File: echo/memory_audit_log.json
 * Format: {"schemaVersion":1, "entries":[...]}
 *
 * Atomic write: tmp → rename.
 */
object AuditLogStore {

    private const val SCHEMA_VERSION = 1
    private val gson = Gson()

    private fun getFile(): File = EchoFileStore.workspaceDir.resolve("memory_audit_log.json")

    /** Append audit entries and persist atomically. Keeps last 500 entries. */
    suspend fun append(entries: List<MemoryGovernanceService.AuditEntry>) = withContext(Dispatchers.IO) {
        try {
            val existing = readAllRaw()
            val stored = entries.map { e ->
                StoredEntry(e.action, e.memoryType, e.memoryId, e.timestamp, e.summary)
            }
            val all = (existing + stored).takeLast(500)
            writeAtomic(all)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Read all entries from persistent storage (most recent first). */
    suspend fun readAll(): List<MemoryGovernanceService.AuditEntry> = withContext(Dispatchers.IO) {
        try {
            readAllRaw().sortedByDescending { it.timestamp }.map { e ->
                MemoryGovernanceService.AuditEntry(e.action, e.memoryType, e.memoryId, e.timestamp, e.summary)
            }
        } catch (_: Exception) { emptyList() }
    }

    /** Clear audit log (for testing). */
    suspend fun clear() = withContext(Dispatchers.IO) {
        try { getFile().delete() } catch (_: Exception) {}
    }

    // -- internal --

    private data class StoredEntry(
        val action: String,
        val memoryType: String,
        val memoryId: String,
        val timestamp: Long,
        val summary: String
    )

    private data class AuditContainer(
        val schemaVersion: Int,
        val entries: List<StoredEntry>
    )

    private fun readAllRaw(): List<StoredEntry> {
        val file = getFile()
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<AuditContainer>() {}.type
            val container: AuditContainer = gson.fromJson(file.readText(Charsets.UTF_8), type)
            container.entries
        } catch (_: Exception) { emptyList() }
    }

    private fun writeAtomic(entries: List<StoredEntry>) {
        val file = getFile()
        file.parentFile?.mkdirs()
        val container = AuditContainer(schemaVersion = SCHEMA_VERSION, entries = entries)
        val json = gson.toJson(container)
        val tmpFile = File(file.absolutePath + ".tmp")
        try {
            tmpFile.writeText(json, Charsets.UTF_8)
            if (!tmpFile.renameTo(file)) {
                file.writeText(json, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            tmpFile.delete()
            throw e
        }
    }
}
