package com.example.myapplication.data.store

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.model.EchoPlan
import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.data.model.UserProfileMemory
import com.example.myapplication.data.repository.DiaryRepository
import com.example.myapplication.data.repository.LifeRecordRepository
import com.example.myapplication.data.repository.MemoryRepository
import com.example.myapplication.data.repository.PlanRepository
import com.example.myapplication.memory.WeeklyReviewBuilder
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Echo 数据导出工具。
 * 将所有数据打包为 zip 文件，通过系统分享发送。
 */
object DataExporter {

    private const val TAG = "DataExporter"
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * 导出所有 Echo 数据为 zip 文件，返回导出的 File。
     */
    suspend fun exportAll(context: Context): File = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val zipName = "echo_export_$timestamp.zip"
        val exportDir = File(context.cacheDir, "exports")
        exportDir.mkdirs()
        val zipFile = File(exportDir, zipName)

        val exportedEntries = mutableListOf<String>()
        var hasError = false
        var errorMessage = ""

        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                // JSON 数据文件
                addJsonToZip(zos, EchoFileStore.lifeRecordsFile, "life_records.json")
                exportedEntries.add("life_records.json")
                addJsonToZip(zos, EchoFileStore.dailyDiariesFile, "daily_diaries.json")
                exportedEntries.add("daily_diaries.json")
                addJsonToZip(zos, EchoFileStore.plansFile, "plans.json")
                exportedEntries.add("plans.json")
                addJsonToZip(zos, EchoFileStore.memoryCardsFile, "memory_cards.json")
                exportedEntries.add("memory_cards.json")
                addJsonToZip(zos, EchoFileStore.userProfileFile, "user_profile.json")
                exportedEntries.add("user_profile.json")

                // Audit log
                val auditFile = EchoFileStore.workspaceDir.resolve("memory_audit_log.json")
                if (auditFile.exists()) {
                    addFileToZip(zos, auditFile, "memory_audit_log.json")
                    exportedEntries.add("memory_audit_log.json")
                }

                // Weekly review data (freshly built)
                try {
                    val review = WeeklyReviewBuilder.build()
                    val reviewJson = gson.toJson(mapOf(
                        "dateRange" to review.dateRange,
                        "lifeRecordCount" to review.lifeRecordCount,
                        "diaryCount" to review.diaryCount,
                        "memoryCardCount" to review.memoryCardCount,
                        "completedPlanCount" to review.completedPlanCount,
                        "totalPlanCount" to review.totalPlanCount,
                        "topKeywords" to review.topKeywords,
                        "dominantMood" to review.dominantMood,
                        "summary" to review.summary
                    ))
                    zos.putNextEntry(ZipEntry("weekly_review.json"))
                    zos.write(reviewJson.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                    exportedEntries.add("weekly_review.json")
                } catch (e: Exception) {
                    Log.w(TAG, "Weekly review export skipped: ${e.message}")
                }

                // Growth timeline data (aggregated from all repos)
                try {
                    val timelineJson = buildTimelineExport()
                    zos.putNextEntry(ZipEntry("growth_timeline.json"))
                    zos.write(timelineJson.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                    exportedEntries.add("growth_timeline.json")
                } catch (e: Exception) {
                    Log.w(TAG, "Timeline export skipped: ${e.message}")
                }

                // README.txt
                val readme = buildReadme(timestamp)
                zos.putNextEntry(ZipEntry("README.txt"))
                zos.write(readme.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                exportedEntries.add("README.txt")

                // Workspace markdown 文件
                addFileToZip(zos, EchoFileStore.workspaceDir.resolve("echo_profile.md"), "echo_profile.md")
                exportedEntries.add("echo_profile.md")

                // 旧 workspace 文件（notes.md, memory.md）
                val oldWorkspace = File(context.filesDir, "workspace")
                if (oldWorkspace.isDirectory) {
                    addFileToZip(zos, oldWorkspace.resolve("notes.md"), "notes.md")
                    addFileToZip(zos, oldWorkspace.resolve("memory.md"), "memory.md")
                    addFileToZip(zos, oldWorkspace.resolve("AGENTS.md"), "AGENTS.md")
                }
            }

            // API key exclusion check: scan zip entries for sensitive patterns
            val sensitiveFound = checkForSensitiveContent(zipFile)
            if (sensitiveFound.isNotEmpty()) {
                Log.w(TAG, "Sensitive patterns found in export: $sensitiveFound")
                // Don't block export, but log diagnostic
                writeDiagnostic(context, timestamp, "Sensitive content detected: $sensitiveFound")
            }

            Log.d(TAG, "Exported to ${zipFile.absolutePath} (${zipFile.length()} bytes)")
        } catch (e: Exception) {
            hasError = true
            errorMessage = e.message ?: "Unknown error"
            Log.e(TAG, "Export failed", e)
            writeDiagnostic(context, timestamp, errorMessage)
            throw e
        }

        zipFile
    }

    /** Build aggregated timeline JSON for export. */
    private suspend fun buildTimelineExport(): String {
        val recordRepo = LifeRecordRepository()
        val diaryRepo = DiaryRepository()
        val memoryRepo = MemoryRepository()
        val planRepo = PlanRepository()

        val records = try { recordRepo.getAll() } catch (_: Exception) { emptyList<LifeRecord>() }
        val diaries = try { diaryRepo.getAll() } catch (_: Exception) { emptyList<DailyDiary>() }
        val cards = try { memoryRepo.getAllCards() } catch (_: Exception) { emptyList<MemoryCard>() }
        val profiles = try { memoryRepo.getAllProfiles() } catch (_: Exception) { emptyList<UserProfileMemory>() }
        val plans = try { planRepo.getAll() } catch (_: Exception) { emptyList<EchoPlan>() }

        data class TimelineEntry(
            val type: String,
            val id: String,
            val timestamp: Long,
            val date: String? = null,
            val summary: String,
            val mood: String? = null,
            val tags: List<String> = emptyList(),
            val status: String? = null
        )

        val entries = mutableListOf<TimelineEntry>()
        for (r in records) {
            entries.add(TimelineEntry("life_record", r.id, r.createdAt, r.date, r.content.take(200), r.mood, r.tags))
        }
        for (d in diaries) {
            entries.add(TimelineEntry("diary", d.id, d.createdAt, d.date, d.summary.take(200), d.mood, d.tags))
        }
        for (c in cards) {
            entries.add(TimelineEntry("memory_card", c.id, c.createdAt, c.memoryDate, c.quote.take(200), c.mood, c.tags, c.status))
        }
        for (p in profiles) {
            entries.add(TimelineEntry("user_profile", p.id, p.createdAt, null, "[${p.category}] ${p.key}: ${p.value.take(100)}", status = "${p.enabled}:${p.status}"))
        }
        for (p in plans) {
            entries.add(TimelineEntry("plan", p.id, p.createdAt, null, "${p.title}: ${p.message.take(100)}", tags = p.tags))
        }

        entries.sortByDescending { it.timestamp }

        return gson.toJson(mapOf(
            "schemaVersion" to 1,
            "totalEntries" to entries.size,
            "entries" to entries.take(500)
        ))
    }

    /** Build README.txt content. */
    private fun buildReadme(timestamp: String): String {
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
- Echo 的数据始终存储在本地，不上传到第三方服务器
""".trimIndent()
    }

    /** Check zip entries for sensitive API key patterns. Returns list of matches found. */
    private fun checkForSensitiveContent(zipFile: File): List<String> {
        val sensitivePatterns = listOf(
            Regex("sk-[a-zA-Z0-9]{20,}"),     // OpenAI API key pattern
            Regex("Bearer\\s+[a-zA-Z0-9_\\-]{20,}"), // Bearer token
            Regex("\"api[_-]?key\"\\s*:\\s*\"[^\"]{10,}\"", RegexOption.IGNORE_CASE)
        )
        val found = mutableListOf<String>()
        try {
            java.util.zip.ZipFile(zipFile).use { zip ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory) continue
                    val content = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
                    for (pattern in sensitivePatterns) {
                        if (pattern.containsMatchIn(content)) {
                            found.add("${entry.name}: matched pattern")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sensitive content check failed: ${e.message}")
        }
        return found
    }

    /** Write diagnostic JSON on export failure or sensitive content detection. */
    private fun writeDiagnostic(context: Context, timestamp: String, error: String) {
        try {
            val diagFile = File(context.cacheDir, "export_error_$timestamp.json")
            diagFile.writeText(gson.toJson(mapOf(
                "timestamp" to timestamp,
                "error" to error,
                "deviceModel" to android.os.Build.MODEL,
                "androidVersion" to android.os.Build.VERSION.SDK_INT
            )))
            Log.d(TAG, "Diagnostic written to ${diagFile.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write diagnostic: ${e.message}")
        }
    }

    private fun addJsonToZip(zos: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists()) {
            zos.putNextEntry(ZipEntry(entryName))
            zos.write("{\"schemaVersion\":1,\"items\":[]}".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            return
        }
        addFileToZip(zos, file, entryName)
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists()) return
        try {
            zos.putNextEntry(ZipEntry(entryName))
            file.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        } catch (e: Exception) {
            Log.w(TAG, "Skipping ${file.name}: ${e.message}")
        }
    }

    /** 启动系统分享 Intent */
    fun shareZip(context: Context, zipFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                zipFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "导出 Echo 数据"))
        } catch (e: Exception) {
            Log.e(TAG, "Share failed", e)
        }
    }
}
