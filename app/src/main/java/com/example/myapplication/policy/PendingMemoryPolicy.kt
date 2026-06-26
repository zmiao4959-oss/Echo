package com.example.myapplication.policy

/**
 * Pure Kotlin: Pending memory dedup and validation logic.
 *
 * Ensures discarded memories don't immediately reappear as pending.
 */
object PendingMemoryPolicy {

    /**
     * Check if a new pending profile value is similar to a previously discarded one.
     *
     * Similarity rule: same category AND (newValue contains oldValue OR oldValue contains newValue).
     */
    fun isSimilar(newValue: String, newCategory: String,
                  discardedValues: List<DiscardedEntry>): Boolean {
        return discardedValues.any { entry ->
            entry.category == newCategory &&
            (newValue.contains(entry.value) || entry.value.contains(newValue))
        }
    }

    data class DiscardedEntry(
        val value: String,
        val category: String,
        val discardedAt: Long
    )

    /**
     * Build a dedup key for a discarded entry.
     * This could be used for more sophisticated dedup (e.g., hash-based).
     */
    fun dedupKey(value: String, category: String): String =
        "${category}::${value.lowercase().trim()}"

    /**
     * Check if two profiles have the same dedup key.
     */
    fun hasDuplicateDedupKey(value: String, category: String,
                             existingKeys: Set<String>): Boolean {
        return dedupKey(value, category) in existingKeys
    }

    /**
     * Validate that a pending profile has sufficient info for user review.
     * Returns list of missing fields (empty = valid).
     */
    fun validatePendingInfo(source: String, reason: String, confidence: Float): List<String> {
        val issues = mutableListOf<String>()
        if (source.isBlank()) issues.add("缺少来源信息")
        if (reason.isBlank()) issues.add("缺少待确认原因")
        if (confidence < 0f || confidence > 1f) issues.add("置信度超出范围")
        return issues
    }

    /**
     * Build a human-readable reason string for display.
     */
    fun formatReason(reason: String, confidence: Float): String {
        if (reason.isNotBlank()) return reason
        return if (confidence < 0.7f) "置信度较低（${"%.0f".format(confidence * 100)}%），建议确认"
        else "需要用户确认"
    }

    /**
     * Extract pending entries that should be shown for user review.
     * Sorted by confidence ascending (least confident first, most urgent).
     */
    fun sortPendingByUrgency(entries: List<PendingEntry>): List<PendingEntry> =
        entries.sortedBy { it.confidence }

    data class PendingEntry(
        val id: String,
        val value: String,
        val category: String,
        val confidence: Float,
        val source: String,
        val reason: String
    )

    // Source label mapping
    val SOURCE_LABELS = mapOf(
        "chat" to "对话记录",
        "life_record" to "生活记录",
        "diary" to "日记",
        "manual" to "手动添加"
    )

    fun sourceLabel(source: String): String = SOURCE_LABELS[source] ?: source.ifEmpty { "未知来源" }
}
