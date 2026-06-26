package com.example.myapplication.memory

import java.io.File

/**
 * 记忆检索 — 关键词搜索 MEMORY.md 和 memory 目录下的 md 文件。
 *
 * Phase C 优化：
 *  - 中文分词（空白切分 + CJK bigram）
 *  - 时间衰减加权（越新分越高）
 *  - 标题行 / MEMORY.md 加权
 *  - 控制 snippet 长度
 */
object MemorySearch {

    data class SearchResult(
        val filePath: String,
        val score: Double,
        val snippet: String
    )

    /** CJK Unicode 范围 */
    private fun isCJK(c: Char): Boolean = c in '一'..'鿿' || c in '㐀'..'䶿'

    /**
     * 分词：空白切分后，中文段提取重叠 bigram + 原段；ASCII 段整段保留。
     */
    private fun tokenize(query: String): List<String> {
        val terms = mutableListOf<String>()
        for (segment in query.split("\\s+".toRegex())) {
            if (segment.isEmpty()) continue
            val hasCJK = segment.any { isCJK(it) }
            if (hasCJK) {
                // 中文：重叠 bigram（"咖啡馆" → "咖啡", "啡馆"）+ 整段
                if (segment.length >= 2) {
                    for (i in 0..segment.length - 2) {
                        terms.add(segment.substring(i, i + 2))
                    }
                }
                terms.add(segment)
            } else {
                terms.add(segment.lowercase())
            }
        }
        return terms
    }

    /**
     * 关键词搜索，按分数降序返回。
     *
     * @param workspaceDir   工作区根目录（含 MEMORY.md 和 memory/ 子目录）
     * @param query           用户查询
     * @param maxResults      最大返回数
     */
    fun keywordSearch(workspaceDir: File, query: String, maxResults: Int = 3): List<SearchResult> {
        val memoryFile = File(workspaceDir, "MEMORY.md")
        val memoryDir = File(workspaceDir, "memory")

        val files = mutableListOf<File>()
        if (memoryFile.exists()) files.add(memoryFile)
        if (memoryDir.exists()) {
            memoryDir.listFiles()?.filter { it.extension == "md" }?.let { files.addAll(it) }
        }

        val queryTerms = tokenize(query)
        if (queryTerms.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val results = mutableListOf<SearchResult>()

        for (file in files) {
            val content = file.readText(Charsets.UTF_8)
            val contentLower = content.lowercase()

            // ── 匹配计分 ──
            var score = 0.0
            for (term in queryTerms) {
                val count = contentLower.countSubstring(term)
                if (count > 0) {
                    // 基础分：匹配次数 × 词长度
                    score += count * term.length.toDouble()
                }
            }

            if (score <= 0.0) continue

            // ── 时间衰减加权：越新文件分越高 ──
            val ageDays = (now - file.lastModified()) / 86_400_000.0
            val timeWeight = 1.0 / (1.0 + ageDays * 0.1)  // 10天后衰减 ~50%
            score *= timeWeight

            // ── 内容价值加权 ──
            // 标题行（# 开头）命中 → +20%
            val lines = content.split("\n")
            val titleLines = lines.filter { it.trimStart().startsWith("#") }
            for (tl in titleLines) {
                for (term in queryTerms) {
                    if (tl.lowercase().contains(term)) {
                        score *= 1.2
                        break
                    }
                }
            }
            // MEMORY.md（用户画像/长期记忆主文件）→ +30%
            if (file.name == "MEMORY.md") {
                score *= 1.3
            }

            // ── Snippet：匹配行取前若干行，截断 ──
            val matchedLines = lines.filter { line ->
                queryTerms.any { term -> line.lowercase().contains(term) }
            }
            var snippet = matchedLines.take(8).joinToString("\n")
            if (snippet.length > 400) {
                snippet = snippet.take(400) + "…"
            }

            results.add(SearchResult(file.name, score, snippet))
        }

        return results.sortedByDescending { it.score }.take(maxResults)
    }

    /** 不重叠子串计数 */
    private fun String.countSubstring(sub: String): Int {
        var count = 0
        var idx = 0
        val lower = this.lowercase()
        val lowerSub = sub.lowercase()
        while (idx <= length - sub.length) {
            idx = lower.indexOf(lowerSub, idx)
            if (idx < 0) break
            count++
            idx += sub.length
        }
        return count
    }
}
