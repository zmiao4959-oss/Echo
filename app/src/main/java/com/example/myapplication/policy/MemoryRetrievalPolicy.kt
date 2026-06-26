package com.example.myapplication.policy

/**
 * Pure Kotlin: Multi-source memory retrieval scoring, filtering, and capping.
 *
 * Zero Android dependencies — testable with plain JUnit.
 */
object MemoryRetrievalPolicy {

    /** Source weight multipliers for unified scoring. */
    val SOURCE_WEIGHTS = mapOf(
        "profile" to 1.2,
        "memory_card" to 1.1,
        "memory_md" to 1.0,
        "life_record" to 0.9,
        "diary" to 0.8
    )

    /** Content length caps. */
    const val MAX_PROFILE_SUMMARY = 600
    const val MAX_RELEVANT_MEMORY = 800
    const val MAX_MEMORY_PREFIX = 600
    const val MAX_SNIPPET_PER_HIT = 200

    data class SourceHit(
        val sourceType: String,
        val sourceLabel: String,
        val sourceId: String,
        val snippet: String,
        val rawScore: Double,
        val pinned: Boolean = false,
        val ageDays: Double = 0.0,
        val explain: String = ""
    )

    data class MemorySource(
        val type: String,
        val label: String,
        val snippet: String,
        val explain: String = "",
        val sourceId: String = "",
        val timestamp: Long? = null
    )

    // ── Scoring ──

    /** Compute unified score with source weight, time decay, and pinned bonus. */
    fun score(hit: SourceHit): Double {
        var s = hit.rawScore
        // Source weight
        s *= SOURCE_WEIGHTS[hit.sourceType] ?: 1.0
        // Time decay
        s *= timeDecay(hit.ageDays)
        // Pinned bonus
        if (hit.pinned) s *= 1.5
        return s
    }

    /** Keyword match score: sum of (matchCount * termLength) for each token. */
    fun keywordScore(query: String, text: String): Double {
        val terms = tokenize(query)
        if (terms.isEmpty()) return 0.0
        val textLower = text.lowercase()
        var score = 0.0
        for (term in terms) {
            var idx = 0
            var count = 0
            while (idx <= textLower.length - term.length) {
                idx = textLower.indexOf(term.lowercase(), idx)
                if (idx < 0) break
                count++
                idx += term.length
            }
            if (count > 0) score += count * term.length.toDouble()
        }
        return score
    }

    fun timeDecay(ageDays: Double): Double = 1.0 / (1.0 + ageDays * 0.1)

    // ── Filtering ──

    /** Return only hits with confirmed + enabled status. */
    fun filterConfirmedEnabled(hits: List<SourceHit>, statuses: Map<String, StatusInfo>): List<SourceHit> {
        return hits.filter { hit ->
            val s = statuses[hit.sourceId] ?: StatusInfo(enabled = true, status = "confirmed")
            s.enabled && s.status == "confirmed"
        }
    }

    data class StatusInfo(val enabled: Boolean, val status: String)

    // ── Length capping ──

    fun capProfileSummary(text: String): String {
        if (text.isBlank()) return ""
        val lines = text.split("\n").filter { it.isNotBlank() }
        val summary = lines.take(10).joinToString("\n")
        return if (summary.length > MAX_PROFILE_SUMMARY) summary.take(MAX_PROFILE_SUMMARY) + "…" else summary
    }

    fun capRelevantMemory(hits: List<SourceHit>): String {
        val sb = StringBuilder()
        var totalLen = 0
        for (h in hits) {
            if (totalLen >= MAX_RELEVANT_MEMORY) break
            val line = "\n[${h.sourceLabel}] ${h.snippet.take(200)}\n"
            sb.append(line)
            totalLen += line.length
        }
        return sb.toString().trimEnd()
    }

    fun capMemoryPrefix(hits: List<SourceHit>): String {
        if (hits.isEmpty()) return ""
        val lines = mutableListOf("[Memory Search Results]")
        var totalLen = 0
        for (h in hits) {
            if (totalLen >= MAX_MEMORY_PREFIX) break
            lines.add("Source: ${h.sourceLabel} (score: ${"%.2f".format(score(h))})")
            lines.add(h.snippet.take(150))
            lines.add("")
            totalLen += h.snippet.length + 50
        }
        return lines.joinToString("\n")
    }

    /** Sort by computed score descending. */
    fun sortByScore(hits: List<SourceHit>): List<SourceHit> =
        hits.sortedByDescending { score(it) }

    // ── Tokenization (same bigram logic as MemorySearch) ──

    fun tokenize(query: String): List<String> {
        val terms = mutableListOf<String>()
        for (segment in query.split("\\s+".toRegex())) {
            if (segment.isEmpty()) continue
            val hasCJK = segment.any { isCJK(it) }
            if (hasCJK) {
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

    private fun isCJK(c: Char): Boolean = c in '一'..'鿿' || c in '㐀'..'䶿'

    // ── Explain generation (F4: explainable retrieval) ──

    /**
     * Generate a human-readable explanation for why this hit was matched.
     * Never includes raw scores — only natural language reasons.
     */
    fun explainHit(hit: SourceHit, query: String): String {
        val reasons = mutableListOf<String>()

        // Keyword match
        val matchedTerms = findMatchedTerms(query, hit.snippet)
        if (matchedTerms.isNotEmpty()) {
            reasons.add("命中「${matchedTerms.take(2).joinToString("、")}」")
        }

        // Pinned
        if (hit.pinned) {
            reasons.add("置顶记忆")
        }

        // Recent
        if (hit.ageDays < 2.0 && hit.ageDays > 0) {
            reasons.add("最近记录")
        }

        // Source weight (high-weight types)
        val weight = SOURCE_WEIGHTS[hit.sourceType] ?: 1.0
        if (weight >= 1.1 && hit.sourceType != "memory_md") {
            reasons.add("${hit.sourceLabel}匹配")
        }

        return if (reasons.isEmpty()) "相关内容" else reasons.joinToString(" · ")
    }

    /** Find which query terms actually matched in the text. */
    fun findMatchedTerms(query: String, text: String): List<String> {
        val terms = tokenize(query)
        val textLower = text.lowercase()
        return terms.filter { term ->
            textLower.contains(term.lowercase())
        }.distinct()
    }
}
