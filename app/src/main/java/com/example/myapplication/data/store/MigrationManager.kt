package com.example.myapplication.data.store

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream

/**
 * 轻量级 schema 迁移引擎。
 *
 * 启动时检测每个数据文件的 schemaVersion，如果低于目标版本，自动备份 → 迁移 → 写日志。
 * 迁移失败时回滚（恢复备份），不破坏原数据。
 */
object MigrationManager {

    private const val TAG = "MigrationManager"
    private val gson = Gson()

    data class MigrationEntry(
        val timestamp: Long,
        val targetFile: String,
        val fromVersion: Int,
        val toVersion: Int,
        val success: Boolean,
        val error: String? = null,
        val backupPath: String? = null
    )

    private data class MigrationLog(
        val schemaVersion: Int = 1,
        val entries: MutableList<MigrationEntry> = mutableListOf()
    )

    /** 测试用：覆盖迁移日志目录。null 恢复默认。 */
    private var testLogDir: File? = null

    fun setTestLogDir(dir: File?) { testLogDir = dir }

    /** 已注册的迁移步骤：targetFile → { 迁移函数 } */
    private val migrations = mutableMapOf<String, (File) -> Boolean>()

    /**
     * 对每个管理的文件检查版本 → 执行迁移链 → 返回日志。
     */
    fun runMigrations(): List<MigrationEntry> {
        val results = mutableListOf<MigrationEntry>()
        val managed = listOf(
            EchoFileStore.lifeRecordsFile to SchemaVersions.LIFE_RECORDS,
            EchoFileStore.dailyDiariesFile to SchemaVersions.DAILY_DIARIES,
            EchoFileStore.plansFile to SchemaVersions.PLANS,
            EchoFileStore.memoryCardsFile to SchemaVersions.MEMORY_CARDS,
            EchoFileStore.userProfileFile to SchemaVersions.USER_PROFILE,
            EchoFileStore.workspaceDir.resolve("memory_audit_log.json") to SchemaVersions.AUDIT_LOG,
            EchoFileStore.workspaceDir.resolve("discarded_profiles.json") to SchemaVersions.DISCARDED_PROFILES
        )

        for ((file, targetVersion) in managed) {
            if (!file.exists()) continue

            val currentVersion = JsonAtomicWriter.readSchemaVersion(file)
            if (currentVersion >= targetVersion) continue

            // 备份
            val backupFile = File("${file.absolutePath}.bak.${System.currentTimeMillis()}")
            try {
                file.copyTo(backupFile, overwrite = true)
            } catch (e: Exception) {
                Log.w(TAG, "Backup failed for ${file.name}: ${e.message}")
                results.add(MigrationEntry(
                    timestamp = System.currentTimeMillis(),
                    targetFile = file.name,
                    fromVersion = currentVersion,
                    toVersion = targetVersion,
                    success = false,
                    error = "Backup failed: ${e.message}"
                ))
                continue
            }

            // 执行迁移
            val key = file.absolutePath
            val migrateFn = migrations[key]
            val success = if (migrateFn != null) {
                try {
                    if (migrateFn(file)) {
                        // 更新 schemaVersion
                        updateSchemaVersion(file, targetVersion)
                        true
                    } else false
                } catch (e: Exception) {
                    Log.e(TAG, "Migration failed for ${file.name}: ${e.message}", e)
                    // 回滚
                    try { backupFile.copyTo(file, overwrite = true) } catch (_: Exception) {}
                    false
                }
            } else {
                // 无人注册迁移函数，只更新 schemaVersion（兼容未来空迁移）
                updateSchemaVersion(file, targetVersion)
                true
            }

            results.add(MigrationEntry(
                timestamp = System.currentTimeMillis(),
                targetFile = file.name,
                fromVersion = currentVersion,
                toVersion = targetVersion,
                success = success,
                error = if (!success) "Migration function returned false" else null,
                backupPath = backupFile.absolutePath
            ))

            if (success) {
                Log.i(TAG, "${file.name}: v$currentVersion → v$targetVersion OK")
                // 迁移成功，删除备份
                try { backupFile.delete() } catch (_: Exception) {}
            } else {
                Log.w(TAG, "${file.name}: v$currentVersion → v$targetVersion FAILED, backup at ${backupFile.name}")
            }
        }

        appendMigrationLog(results)
        return results
    }

    /**
     * 注册一个特定文件的迁移函数。
     * @param file  目标文件
     * @param migrate  迁移逻辑，返回 true 表示成功
     */
    fun registerMigration(file: File, migrate: (File) -> Boolean) {
        migrations[file.absolutePath] = migrate
    }

    /**
     * 测试用：对单个文件执行迁移，不依赖 EchoFileStore 路径。
     * 返回 MigrationEntry 列表。
     */
    fun runMigrationOnFileForTesting(file: File, targetVersion: Int, migrateFn: ((File) -> Boolean)?): List<MigrationEntry> {
        val results = mutableListOf<MigrationEntry>()
        if (!file.exists()) return results

        val currentVersion = JsonAtomicWriter.readSchemaVersion(file)
        if (currentVersion >= targetVersion) return results

        // 备份
        val backupFile = File("${file.absolutePath}.bak.${System.currentTimeMillis()}")
        try { file.copyTo(backupFile, overwrite = true) } catch (e: Exception) {
            results.add(MigrationEntry(System.currentTimeMillis(), file.name, currentVersion, targetVersion, false, "Backup failed"))
            return results
        }

        val success = if (migrateFn != null) {
            try {
                if (migrateFn(file)) { updateSchemaVersion(file, targetVersion); true }
                else false
            } catch (e: Exception) {
                try { backupFile.copyTo(file, overwrite = true) } catch (_: Exception) {}
                false
            }
        } else { updateSchemaVersion(file, targetVersion); true }

        results.add(MigrationEntry(
            System.currentTimeMillis(), file.name, currentVersion, targetVersion, success,
            error = if (!success) "Migration failed" else null,
            backupPath = if (!success) backupFile.absolutePath else null
        ))

        if (success) { try { backupFile.delete() } catch (_: Exception) {} }
        appendMigrationLog(results)
        return results
    }

    /** 读取持久化的迁移日志 */
    fun getMigrationLog(): List<MigrationEntry> {
        val logFile = migrationLogFile()
        if (!logFile.exists()) return emptyList()
        return try {
            val json = logFile.readText(Charsets.UTF_8)
            val type = object : TypeToken<MigrationLog>() {}.type
            val log: MigrationLog = gson.fromJson(json, type)
            log.entries.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun appendMigrationLog(entries: List<MigrationEntry>) {
        val logFile = migrationLogFile()
        val existing = getMigrationLog().toMutableList()
        existing.addAll(entries)
        val log = MigrationLog(entries = existing)
        try {
            logFile.parentFile?.mkdirs()
            val json = gson.toJson(log)
            val tmp = File(logFile.absolutePath + ".tmp")
            tmp.writeText(json, Charsets.UTF_8)
            tmp.renameTo(logFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write migration log", e)
        }
    }

    private fun updateSchemaVersion(file: File, newVersion: Int) {
        try {
            val items = JsonAtomicWriter.readItems<Any>(file)
            JsonAtomicWriter.writeItems(file, items, newVersion)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update schemaVersion for ${file.name}", e)
        }
    }

    private fun migrationLogFile(): File {
        testLogDir?.let { return it.resolve("migration_log.json") }
        return EchoFileStore.workspaceDir.resolve("migration_log.json")
    }
}
