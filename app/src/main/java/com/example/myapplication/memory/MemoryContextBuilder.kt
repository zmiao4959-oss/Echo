package com.example.myapplication.memory

import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.data.model.UserProfileMemory
import com.example.myapplication.data.repository.DiaryRepository
import com.example.myapplication.data.repository.LifeRecordRepository
import com.example.myapplication.data.repository.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Unified memory injection entry point — all Agent memory injection must go through here.
 *
 * Phase E: Multi-source retrieval with unified scoring across:
 * - UserProfileMemory (enabled + confirmed)
 * - MemoryCard (confirmed)
 * - MEMORY.md (confirmed sections only)
 * - LifeRecord (last 30 days)
 * - DailyDiary (last 30 days)
 *
 * Permission rules (centralized, Agent no longer decides):
 * - Only confirmed + enabled content is injectable
 * - Total injection capped at ~2000 chars
 */
object MemoryContextBuilder {

    /** Lightweight memory source reference for UI transparency (E4). */
    data class MemorySource(
        val type: String,       // "profile" | "memory_card" | "memory_md" | "life_record" | "diary"
        val label: String,      // "用户画像" | "记忆卡片" | "长期记忆" | "生活记录" | "日记"
        val snippet: String     // short non-private summary (<= 30 chars)
    )

    data class MemoryContext(
        val profileSummary: String,
        val relevantMemorySection: String,
        val memoryPrefix: String,
        val structuredProfileInjection: String,
        /** Sources actually used this round (for UI memory hints, E4). */
        val sources: List<MemorySource> = emptyList()
    )

    /** Unified search result with source metadata. */
    private data class UnifiedHit(
        val sourceType: String,
        val sourceLabel: String,
        val sourceId: String,
        val snippet: String,
        val score: Double
    )

    // Source weight multipliers
    private val SOURCE_WEIGHT = mapOf(
        "profile" to 1.2,
        "memory_card" to 1.1,
        "memory_md" to 1.0,
        "life_record" to 0.9,
        "diary" to 0.8
    )

    // Content length limits
    private const val MAX_PROFILE_SUMMARY = 600
    private const val MAX_RELEVANT_MEMORY = 800
    private const val MAX_MEMORY_PREFIX = 600
    private const val MAX_SNIPPET_PER_HIT = 200

    /**
     * Single call, returns all memory context. Agent calls once, distributes fields.
     */
    suspend fun build(userMessage: String): MemoryContext = withContext(Dispatchers.IO) {
        val repo = MemoryRepository()
        val recordRepo = LifeRecordRepository()
        val diaryRepo = DiaryRepository()

        val allHits = mutableListOf<UnifiedHit>()

        // 1. MEMORY.md + memory/*.md (confirmed-only, via MemorySearch)
        if (userMessage.isNotBlank()) {
            val mdResults = MemorySearch.keywordSearch(FileStore.workspaceDir, userMessage, maxResults = 5)
            for (r in mdResults) {
                allHits.add(UnifiedHit(
                    sourceType = "memory_md",
                    sourceLabel = "长期记忆",
                    sourceId = r.filePath,
                    snippet = r.snippet.take(MAX_SNIPPET_PER_HIT),
                    score = r.score * (SOURCE_WEIGHT["memory_md"] ?: 1.0)
                ))
            }
        }

        // 2. UserProfileMemory (enabled + confirmed)
        val profiles = repo.getEnabledProfiles().filter { it.status == "confirmed" }
        if (userMessage.isNotBlank()) {
            for (p in profiles) {
                val keywordScore = computeKeywordScore(userMessage, p.value)
                if (keywordScore > 0) {
                    allHits.add(UnifiedHit(
                        sourceType = "profile",
                        sourceLabel = "用户画像",
                        sourceId = p.id,
                        snippet = p.value.take(MAX_SNIPPET_PER_HIT),
                        score = keywordScore * (SOURCE_WEIGHT["profile"] ?: 1.0)
                    ))
                }
            }
        }

        // 3. MemoryCard (confirmed, search quote + note)
        val confirmedCards = repo.getAllCards().filter { it.status == "confirmed" }
        if (userMessage.isNotBlank()) {
            for (c in confirmedCards) {
                val keywordScore = computeKeywordScore(userMessage, c.quote + " " + c.note)
                val adjustedScore = if (c.pinned) keywordScore * 1.5 else keywordScore
                if (adjustedScore > 0) {
                    allHits.add(UnifiedHit(
                        sourceType = "memory_card",
                        sourceLabel = "记忆卡片",
                        sourceId = c.id,
                        snippet = c.quote.take(MAX_SNIPPET_PER_HIT),
                        score = adjustedScore * (SOURCE_WEIGHT["memory_card"] ?: 1.0)
                    ))
                }
            }
        }

        // 4. LifeRecord (last 30 days)
        if (userMessage.isNotBlank()) {
            val recentRecords = getRecentRecords(recordRepo, 30)
            for (r in recentRecords) {
                val keywordScore = computeKeywordScore(userMessage, r.content)
                if (keywordScore > 0) {
                    val ageDays = (System.currentTimeMillis() - r.createdAt) / 86_400_000.0
                    val timeDecay = 1.0 / (1.0 + ageDays * 0.1)
                    allHits.add(UnifiedHit(
                        sourceType = "life_record",
                        sourceLabel = "生活记录",
                        sourceId = r.id,
                        snippet = r.content.take(MAX_SNIPPET_PER_HIT),
                        score = keywordScore * timeDecay * (SOURCE_WEIGHT["life_record"] ?: 1.0)
                    ))
                }
            }
        }

        // 5. Diary (last 30 days)
        if (userMessage.isNotBlank()) {
            val recentDiaries = getRecentDiaries(diaryRepo, 30)
            for (d in recentDiaries) {
                val searchText = "${d.title} ${d.summary} ${d.diaryText}"
                val keywordScore = computeKeywordScore(userMessage, searchText)
                if (keywordScore > 0) {
                    val ageDays = (System.currentTimeMillis() - d.createdAt) / 86_400_000.0
                    val timeDecay = 1.0 / (1.0 + ageDays * 0.1)
                    allHits.add(UnifiedHit(
                        sourceType = "diary",
                        sourceLabel = "日记",
                        sourceId = d.id,
                        snippet = d.summary.take(MAX_SNIPPET_PER_HIT),
                        score = keywordScore * timeDecay * (SOURCE_WEIGHT["diary"] ?: 1.0)
                    ))
                }
            }
        }

        // Sort by score descending, take top results
        val sorted = allHits.sortedByDescending { it.score }
        val topHits = sorted.take(8)

        // Read confirmed section of MEMORY.md for profile summary
        val confirmedMdContent = MemoryMdParser.readConfirmedSection(
            FileStore.readWorkspaceFile("MEMORY.md")
        )

        // Recent confirmed cards for backup context
        val recentCards = confirmedCards.sortedByDescending { it.createdAt }.take(2)

        // Build sources list for E4 memory transparency
        val sources = topHits.map { h ->
            MemorySource(
                type = h.sourceType,
                label = h.sourceLabel,
                snippet = h.snippet.take(30)
            )
        }

        MemoryContext(
            profileSummary = buildProfileSummary(confirmedMdContent),
            relevantMemorySection = buildRelevantMemorySection(topHits, recentCards),
            memoryPrefix = buildMemoryPrefix(topHits),
            structuredProfileInjection = buildStructuredProfileInjection(profiles),
            sources = sources
        )
    }

    // -- private builders --

    private fun buildProfileSummary(confirmedContent: String): String {
        if (confirmedContent.isBlank()) return ""
        val lines = confirmedContent.split("\n").filter { it.isNotBlank() }
        val summary = lines.take(10).joinToString("\n")
        return if (summary.length > MAX_PROFILE_SUMMARY) summary.take(MAX_PROFILE_SUMMARY) + "…" else summary
    }

    private fun buildRelevantMemorySection(
        hits: List<UnifiedHit>,
        recentCards: List<MemoryCard>
    ): String {
        if (hits.isEmpty() && recentCards.isEmpty()) return ""
        val sb = StringBuilder()
        var totalLen = 0
        for (h in hits) {
            if (totalLen >= MAX_RELEVANT_MEMORY) break
            val line = "\n[${h.sourceLabel}] ${h.snippet.take(200)}\n"
            sb.append(line)
            totalLen += line.length
        }
        for (c in recentCards) {
            if (totalLen >= MAX_RELEVANT_MEMORY) break
            val line = "\n[最近: 记忆卡片] ${c.quote.take(200)}\n"
            sb.append(line)
            totalLen += line.length
        }
        return sb.toString().trimEnd()
    }

    private fun buildMemoryPrefix(hits: List<UnifiedHit>): String {
        if (hits.isEmpty()) return ""
        val lines = mutableListOf("[Memory Search Results]")
        var totalLen = 0
        for (h in hits) {
            if (totalLen >= MAX_MEMORY_PREFIX) break
            val line = "Source: ${h.sourceLabel} (score: ${"%.2f".format(h.score)})"
            lines.add(line)
            lines.add(h.snippet.take(150))
            lines.add("")
            totalLen += line.length + h.snippet.length + 2
        }
        return lines.joinToString("\n")
    }

    private fun buildStructuredProfileInjection(profiles: List<UserProfileMemory>): String {
        if (profiles.isEmpty()) return ""
        val cats = linkedMapOf(
            "identity" to "身份", "preference" to "偏好", "habit" to "习惯",
            "goal" to "目标", "project" to "项目", "relationship" to "关系"
        )
        return buildString {
            append("\n\n## 结构化用户画像\n")
            for ((cat, label) in cats) {
                val items = profiles.filter { it.category == cat }
                if (items.isNotEmpty()) {
                    append("- $label: ${items.joinToString("；") { it.value }}\n")
                }
            }
        }
    }

    // -- keyword scoring (same bigram tokenization as MemorySearch) --

    private fun isCJK(c: Char): Boolean = c in '一'..'鿿' || c in '㐀'..'䶿'

    private fun tokenize(query: String): List<String> {
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

    private fun computeKeywordScore(query: String, text: String): Double {
        val queryTerms = tokenize(query)
        if (queryTerms.isEmpty()) return 0.0
        val textLower = text.lowercase()
        var score = 0.0
        for (term in queryTerms) {
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

    // -- date range helpers --

    private suspend fun getRecentRecords(repo: LifeRecordRepository, days: Int): List<LifeRecord> {
        return try {
            val cal = Calendar.getInstance()
            val endDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, -days)
            val startDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            repo.getByDateRange(startDate, endDate)
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun getRecentDiaries(repo: DiaryRepository, days: Int): List<DailyDiary> {
        return try {
            val cal = Calendar.getInstance()
            val endDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, -days)
            val startDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            repo.getByDateRange(startDate, endDate)
        } catch (_: Exception) { emptyList() }
    }
}
