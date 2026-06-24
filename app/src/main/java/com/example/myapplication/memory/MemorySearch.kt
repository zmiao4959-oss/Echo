package com.example.myapplication.memory

import java.io.File

/**
 * 记忆检索 — 关键词搜索 MEMORY.md 和 memory 目录下的 md 文件。
 */
object MemorySearch {

    data class SearchResult(
        val filePath: String,
        val score: Double,
        val snippet: String
    )

    fun keywordSearch(workspaceDir: File, query: String, maxResults: Int = 3): List<SearchResult> {
        val memoryFile = File(workspaceDir, "MEMORY.md")
        val memoryDir = File(workspaceDir, "memory")

        val files = mutableListOf<File>()
        if (memoryFile.exists()) files.add(memoryFile)
        if (memoryDir.exists()) {
            memoryDir.listFiles()?.filter { it.extension == "md" }?.let { files.addAll(it) }
        }

        val queryLower = query.lowercase()
        val queryTerms = Regex("\\w+").findAll(queryLower).map { it.value }.toList()

        val results = mutableListOf<SearchResult>()
        for (file in files) {
            val content = file.readText(Charsets.UTF_8)
            val contentLower = content.lowercase()

            var score = 0.0
            for (term in queryTerms) {
                score += contentLower.countSubstring(term) * term.length
            }

            if (score > 0) {
                val lines = content.split("\n")
                val matchedLines = lines.filter { line ->
                    queryTerms.any { term -> line.lowercase().contains(term) }
                }
                val snippet = matchedLines.take(10).joinToString("\n")
                results.add(SearchResult(file.name, score, snippet))
            }
        }

        return results.sortedByDescending { it.score }.take(maxResults)
    }

    private fun String.countSubstring(sub: String): Int {
        var count = 0
        var idx = 0
        while (idx <= length - sub.length) {
            idx = indexOf(sub, idx)
            if (idx < 0) break
            count++
            idx += sub.length
        }
        return count
    }
}
