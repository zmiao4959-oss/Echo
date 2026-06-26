package com.example.myapplication.memory

/**
 * MEMORY.md 章节解析器 — 支持 confirmed/pending/disabled 权限分区。
 *
 * 三个章节标记：
 * - ## ✅ 确认的记忆  → confirmed（可检索、可注入）
 * - ## ⏳ 待确认      → pending（不检索、不注入）
 * - ## 🚫 已禁用      → disabled（不检索、不注入）
 *
 * 向后兼容：无任何标记时，全部内容视为 prelude（归入 confirmed）。
 * 惰性迁移：首次写入时自动将旧标记 ## Echo 记住的关于你的事 替换为新标记。
 */
object MemoryMdParser {

    const val SECTION_CONFIRMED = "## ✅ 确认的记忆"
    const val SECTION_PENDING = "## ⏳ 待确认"
    const val SECTION_DISABLED = "## 🚫 已禁用"
    const val SECTION_LEGACY = "## Echo 记住的关于你的事"

    private val ALL_MARKERS = listOf(SECTION_CONFIRMED, SECTION_PENDING, SECTION_DISABLED, SECTION_LEGACY)

    data class ParsedSections(
        val confirmed: String,
        val pending: String,
        val disabled: String,
        /** 第一个章节标记之前的内容 */
        val prelude: String
    )

    /**
     * 将 MEMORY.md 全文按章节标记拆分为 ParsedSections。
     * 旧标记 ## Echo 记住的关于你的事 等同于 confirmed。
     */
    fun parse(content: String): ParsedSections {
        val lines = content.split("\n")
        var confirmedStart = -1
        var pendingStart = -1
        var disabledStart = -1
        var legacyStart = -1

        for (i in lines.indices) {
            when (lines[i].trim()) {
                SECTION_CONFIRMED -> confirmedStart = i
                SECTION_PENDING -> pendingStart = i
                SECTION_DISABLED -> disabledStart = i
                SECTION_LEGACY -> legacyStart = i
            }
        }

        val markerPositions = listOf(confirmedStart, pendingStart, disabledStart, legacyStart)
            .filter { it >= 0 }
        val preludeEnd = if (markerPositions.isNotEmpty()) markerPositions.min() else lines.size
        val prelude = lines.subList(0, preludeEnd).joinToString("\n").trim()

        fun extractSection(startLine: Int): String {
            if (startLine < 0) return ""
            val nextMarker = markerPositions.filter { it > startLine }.minOrNull()
            val endLine = nextMarker ?: lines.size
            return lines.subList(startLine + 1, endLine).joinToString("\n").trim()
        }

        val confirmedFromNew = extractSection(confirmedStart)
        val confirmedFromLegacy = if (confirmedStart < 0 && legacyStart >= 0) extractSection(legacyStart) else ""
        val confirmed = if (confirmedFromNew.isNotBlank()) confirmedFromNew else confirmedFromLegacy

        return ParsedSections(
            confirmed = confirmed,
            pending = extractSection(pendingStart),
            disabled = extractSection(disabledStart),
            prelude = prelude
        )
    }

    /**
     * 返回仅包含 confirmed 的内容（prelude + confirmed section），供 MemorySearch 使用。
     */
    fun readConfirmedSection(content: String): String {
        val parsed = parse(content)
        val parts = mutableListOf<String>()
        if (parsed.prelude.isNotBlank()) parts.add(parsed.prelude)
        if (parsed.confirmed.isNotBlank()) parts.add(parsed.confirmed)
        return parts.joinToString("\n")
    }

    /**
     * 惰性迁移：如果存在旧标记且没有新标记，将旧标记替换为新标记。
     */
    fun migrateIfNeeded(content: String): String {
        val hasLegacy = content.contains(SECTION_LEGACY)
        val hasNewConfirmed = content.contains(SECTION_CONFIRMED)
        if (hasLegacy && !hasNewConfirmed) {
            return content.replace(SECTION_LEGACY, SECTION_CONFIRMED)
        }
        return content
    }

    /**
     * 向 confirmed 章节追加一条事实。
     * 自动执行惰性迁移。
     */
    fun appendFact(content: String, factLine: String): String {
        var updated = migrateIfNeeded(content)

        return if (updated.contains(SECTION_CONFIRMED)) {
            val idx = updated.indexOf(SECTION_CONFIRMED)
            val afterHeader = updated.indexOf("\n", idx)
            val insertAt = if (afterHeader >= 0) afterHeader + 1 else updated.length
            updated.substring(0, insertAt) + factLine + updated.substring(insertAt)
        } else {
            updated.trimEnd() + "\n\n$SECTION_CONFIRMED\n$factLine"
        }
    }

    /**
     * 在章节之间移动一条事实。
     * @param content MEMORY.md 全文
     * @param factLine 要移动的条目文本（去除首尾空白后匹配）
     * @param fromSection 源章节标记
     * @param toSection 目标章节标记
     * @return 更新后的全文；若条目未找到则返回原文
     */
    fun moveFact(
        content: String,
        factLine: String,
        fromSection: String,
        toSection: String
    ): String {
        var updated = migrateIfNeeded(content)

        // 确保目标章节存在
        if (!updated.contains(toSection)) {
            updated = updated.trimEnd() + "\n\n$toSection\n"
        }

        val lines = updated.split("\n").toMutableList()
        val target = factLine.trim()

        // 找到并移除源章节中的条目
        var inSource = false
        var removed = false
        val iter = lines.listIterator()
        while (iter.hasNext()) {
            val line = iter.next()
            val trimmed = line.trim()
            if (trimmed == fromSection) {
                inSource = true
                continue
            }
            if (inSource && trimmed in ALL_MARKERS && trimmed != fromSection) {
                inSource = false
            }
            if (inSource && trimmed == target) {
                iter.remove()
                removed = true
                break
            }
        }

        if (!removed) return content

        // 插入到目标章节
        val result = lines.joinToString("\n")
        return appendFactToSection(result, target, toSection)
    }

    private fun appendFactToSection(content: String, factLine: String, section: String): String {
        val idx = content.indexOf(section)
        if (idx < 0) {
            return content.trimEnd() + "\n\n$section\n$factLine\n"
        }
        val afterHeader = content.indexOf("\n", idx)
        val insertAt = if (afterHeader >= 0) afterHeader + 1 else content.length
        return content.substring(0, insertAt) + factLine + "\n" + content.substring(insertAt)
    }
}
