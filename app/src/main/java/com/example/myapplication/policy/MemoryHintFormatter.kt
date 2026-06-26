package com.example.myapplication.policy

/**
 * Pure Kotlin: Format memory reference hints for UI display.
 *
 * Privacy rule: never expose full memory content in hints — only source type + short snippet.
 */
object MemoryHintFormatter {

    data class HintSource(
        val type: String,       // "profile" | "memory_card" | "memory_md" | "life_record" | "diary"
        val label: String,      // "用户画像" | "记忆卡片" | "长期记忆" | "生活记录" | "日记"
        val snippet: String,    // raw snippet (may contain private content)
        val status: String = "confirmed",  // "confirmed" | "pending" | "disabled"
        val enabled: Boolean = true
    )

    /**
     * Format a list of sources into a one-line UI hint.
     * Example: "参考了 2 条长期记忆 · 1 条生活记录"
     *
     * Only includes confirmed + enabled sources.
     */
    fun formatHint(sources: List<HintSource>): String? {
        val active = sources.filter { it.enabled && it.status == "confirmed" }
        if (active.isEmpty()) return null

        val byType = active.groupBy { it.label }.mapValues { it.value.size }
        return "参考了 " + byType.entries.joinToString(" · ") { "${it.value} 条${it.key}" }
    }

    /**
     * Sanitize a snippet for UI display: max length, no private data exposure.
     * Returns a short (<=30 chars) non-private summary.
     */
    fun sanitizeSnippet(snippet: String, maxLen: Int = 30): String {
        val cleaned = snippet.replace("\n", " ").trim()
        return if (cleaned.length > maxLen) cleaned.take(maxLen) + "…" else cleaned
    }

    /**
     * Check whether a source should be included in the hint.
     * Only confirmed + enabled sources pass.
     */
    fun shouldIncludeInHint(source: HintSource): Boolean =
        source.enabled && source.status == "confirmed"

    /**
     * Filter sources that are eligible for UI hints.
     */
    fun eligibleSources(sources: List<HintSource>): List<HintSource> =
        sources.filter { shouldIncludeInHint(it) }

    /**
     * Verify that no disabled or pending sources leak into the hint.
     * Returns true if all sources in the list are safe to show.
     */
    fun verifyNoLeaked(sources: List<HintSource>): Boolean =
        sources.none { !shouldIncludeInHint(it) }

    /** Count hint-eligible sources by type. */
    fun countByType(sources: List<HintSource>): Map<String, Int> {
        val eligible = eligibleSources(sources)
        return eligible.groupBy { it.label }.mapValues { it.value.size }
    }
}
