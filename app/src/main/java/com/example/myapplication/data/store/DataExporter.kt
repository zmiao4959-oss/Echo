package com.example.myapplication.data.store

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.example.myapplication.data.repository.DiaryRepository
import com.example.myapplication.data.repository.LifeRecordRepository
import com.example.myapplication.data.repository.MemoryRepository
import com.example.myapplication.data.repository.PlanRepository
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

    /**
     * 导出所有 Echo 数据为 zip 文件，返回导出的 File。
     */
    suspend fun exportAll(context: Context): File = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val zipName = "echo_export_$timestamp.zip"
        val exportDir = File(context.cacheDir, "exports")
        exportDir.mkdirs()
        val zipFile = File(exportDir, zipName)

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // JSON 数据文件
            addJsonToZip(zos, EchoFileStore.lifeRecordsFile, "life_records.json")
            addJsonToZip(zos, EchoFileStore.dailyDiariesFile, "daily_diaries.json")
            addJsonToZip(zos, EchoFileStore.plansFile, "plans.json")
            addJsonToZip(zos, EchoFileStore.memoryCardsFile, "memory_cards.json")
            addJsonToZip(zos, EchoFileStore.userProfileFile, "user_profile.json")

            // Workspace markdown 文件
            addFileToZip(zos, EchoFileStore.workspaceDir.resolve("echo_profile.md"), "echo_profile.md")

            // 旧 workspace 文件（notes.md, memory.md）
            val oldWorkspace = File(context.filesDir, "workspace")
            if (oldWorkspace.isDirectory) {
                addFileToZip(zos, oldWorkspace.resolve("notes.md"), "notes.md")
                addFileToZip(zos, oldWorkspace.resolve("memory.md"), "memory.md")
                addFileToZip(zos, oldWorkspace.resolve("AGENTS.md"), "AGENTS.md")
            }
        }

        Log.d(TAG, "Exported to ${zipFile.absolutePath} (${zipFile.length()} bytes)")
        zipFile
    }

    private fun addJsonToZip(zos: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists()) {
            // 写入空 JSON
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
