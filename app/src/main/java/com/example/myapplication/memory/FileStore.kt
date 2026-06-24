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
}
