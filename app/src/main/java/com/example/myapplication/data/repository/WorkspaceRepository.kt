package com.example.myapplication.data.repository

import com.example.myapplication.data.store.EchoFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * WorkspaceRepository —— 管理工作区 Markdown 文件。
 * 对接 echo/workspace/ 目录，延续原有 FileStore 的文件式记忆模式。
 */
class WorkspaceRepository {

    private val dir: File get() = EchoFileStore.workspaceDir

    /** 读取工作区文件内容 */
    suspend fun readFile(filename: String): String = withContext(Dispatchers.IO) {
        val file = File(dir, filename)
        if (file.exists()) file.readText(Charsets.UTF_8) else ""
    }

    /** 写入工作区文件（自动创建父目录） */
    suspend fun writeFile(filename: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(dir, filename)
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 列出工作区所有 .md 文件 */
    suspend fun listFiles(): List<String> = withContext(Dispatchers.IO) {
        dir.listFiles()
            ?.filter { it.isFile && it.extension == "md" }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    /** 读取 echo_profile.md */
    suspend fun getEchoProfile(): String = readFile("echo_profile.md")

    /** 更新 echo_profile.md */
    suspend fun updateEchoProfile(content: String): Boolean = writeFile("echo_profile.md", content)
}
