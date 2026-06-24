package com.example.myapplication.memory

import com.example.myapplication.MyApplication
import java.io.File

/**
 * 工作区文件读写。
 */
object FileStore {

    val workspaceDir: File
        get() = MyApplication.instance.filesDir.resolve("workspace")

    /** 系统提示源文件列表 */
    val SYSTEM_PROMPT_FILES = listOf("AGENTS.md", "SOUL.md", "IDENTITY.md", "USER.md", "MEMORY.md")

    /** 读取工作区文件内容，不存在返回空字符串 */
    fun readWorkspaceFile(filename: String): String {
        val file = File(workspaceDir, filename)
        return if (file.exists()) file.readText(Charsets.UTF_8) else ""
    }

    /** 获取所有系统提示文件的内容 */
    fun loadSystemPromptFiles(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (name in SYSTEM_PROMPT_FILES) {
            val content = readWorkspaceFile(name)
            if (content.isNotBlank()) {
                result[name] = content
            }
        }
        return result
    }

    /** 获取完整系统提示（不含工具列表和运行时信息） */
    fun buildStaticSystemPrompt(skillsPrompt: String = ""): String {
        val parts = mutableListOf<String>()

        for (name in listOf("AGENTS.md", "SOUL.md", "IDENTITY.md", "USER.md")) {
            val content = readWorkspaceFile(name)
            if (content.isNotBlank()) {
                parts.add(content)
            }
        }

        val memContent = readWorkspaceFile("MEMORY.md")
        if (memContent.isNotBlank()) {
            parts.add("## Long-term Memory\n$memContent")
        }

        if (skillsPrompt.isNotBlank()) {
            parts.add(skillsPrompt)
        }

        return parts.joinToString("\n\n")
    }

    /** 确保工作区目录存在 */
    fun ensureWorkspaceDir() {
        workspaceDir.mkdirs()
        File(workspaceDir, "memory").mkdirs()
    }

    /** 写入工作区文件（自动创建父目录），返回是否成功 */
    fun writeWorkspaceFile(filename: String, content: String): Boolean {
        return try {
            val file = File(workspaceDir, filename)
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 列出工作区下所有 .md 文件（根目录 + memory/ 子目录），返回 (相对路径, File) */
    fun listWorkspaceMdFiles(): List<Pair<String, File>> {
        ensureWorkspaceDir()
        val results = mutableListOf<Pair<String, File>>()
        workspaceDir.listFiles()?.filter { it.isFile && it.extension == "md" }?.forEach {
            results.add(it.name to it)
        }
        val memoryDir = File(workspaceDir, "memory")
        if (memoryDir.isDirectory) {
            memoryDir.listFiles()?.filter { it.isFile && it.extension == "md" }?.forEach {
                results.add("memory/${it.name}" to it)
            }
        }
        return results.sortedBy { it.first }
    }
}
