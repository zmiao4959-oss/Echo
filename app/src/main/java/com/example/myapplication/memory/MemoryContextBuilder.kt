package com.example.myapplication.memory

import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.data.model.UserProfileMemory
import com.example.myapplication.data.repository.DiaryRepository
import com.example.myapplication.data.repository.LifeRecordRepository
import com.example.myapplication.data.repository.MemoryRepository
import com.example.myapplication.policy.MemoryRetrievalPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Unified memory injection entry point — all Agent memory injection must go through here.
 *
 * Phase E: Multi-source retrieval with unified scoring (delegated to MemoryRetrievalPolicy).
 * Android layer handles file I/O and repo access; pure scoring/filtering/capping is in policy.
 */
object MemoryContextBuilder {

    data class MemorySource(
        val type: String,
        val label: String,
        val snippet: String,
        val explain: String = "",
        val sourceId: String = "",
        val timestamp: Long? = null
    )

    data class MemoryContext(
        val profileSummary: String,
        val relevantMemorySection: String,
        val memoryPrefix: String,
        val structuredProfileInjection: String,
        val sources: List<MemorySource> = emptyList()
    )

    suspend fun build(userMessage: String): MemoryContext = withContext(Dispatchers.IO) {
        val repo = MemoryRepository()
        val recordRepo = LifeRecordRepository()
        val diaryRepo = DiaryRepository()

        val allHits = mutableListOf<MemoryRetrievalPolicy.SourceHit>()

        // 1. MEMORY.md (confirmed-only, via MemorySearch)
        if (userMessage.isNotBlank()) {
            val mdResults = MemorySearch.keywordSearch(FileStore.workspaceDir, userMessage, maxResults = 5)
            for (r in mdResults) {
                val rawScore = MemoryRetrievalPolicy.keywordScore(userMessage, r.snippet)
                allHits.add(MemoryRetrievalPolicy.SourceHit(
                    sourceType = "memory_md", sourceLabel = "长期记忆", sourceId = r.filePath,
                    snippet = r.snippet.take(MemoryRetrievalPolicy.MAX_SNIPPET_PER_HIT),
                    rawScore = rawScore
                ))
            }
        }

        // 2. UserProfileMemory (enabled + confirmed)
        val profiles = repo.getEnabledProfiles().filter { it.status == "confirmed" }
        if (userMessage.isNotBlank()) {
            for (p in profiles) {
                val rawScore = MemoryRetrievalPolicy.keywordScore(userMessage, p.value)
                if (rawScore > 0) {
                    allHits.add(MemoryRetrievalPolicy.SourceHit(
                        sourceType = "profile", sourceLabel = "用户画像", sourceId = p.id,
                        snippet = p.value.take(MemoryRetrievalPolicy.MAX_SNIPPET_PER_HIT),
                        rawScore = rawScore
                    ))
                }
            }
        }

        // 3. MemoryCard (confirmed)
        val confirmedCards = repo.getAllCards().filter { it.status == "confirmed" }
        if (userMessage.isNotBlank()) {
            for (c in confirmedCards) {
                val rawScore = MemoryRetrievalPolicy.keywordScore(userMessage, c.quote + " " + c.note)
                if (rawScore > 0) {
                    allHits.add(MemoryRetrievalPolicy.SourceHit(
                        sourceType = "memory_card", sourceLabel = "记忆卡片", sourceId = c.id,
                        snippet = c.quote.take(MemoryRetrievalPolicy.MAX_SNIPPET_PER_HIT),
                        rawScore = rawScore, pinned = c.pinned
                    ))
                }
            }
        }

        // 4. LifeRecord (last 30 days)
        if (userMessage.isNotBlank()) {
            val recentRecords = getRecentRecords(recordRepo, 30)
            for (r in recentRecords) {
                val rawScore = MemoryRetrievalPolicy.keywordScore(userMessage, r.content)
                if (rawScore > 0) {
                    val ageDays = (System.currentTimeMillis() - r.createdAt) / 86_400_000.0
                    allHits.add(MemoryRetrievalPolicy.SourceHit(
                        sourceType = "life_record", sourceLabel = "生活记录", sourceId = r.id,
                        snippet = r.content.take(MemoryRetrievalPolicy.MAX_SNIPPET_PER_HIT),
                        rawScore = rawScore, ageDays = ageDays
                    ))
                }
            }
        }

        // 5. Diary (last 30 days)
        if (userMessage.isNotBlank()) {
            val recentDiaries = getRecentDiaries(diaryRepo, 30)
            for (d in recentDiaries) {
                val searchText = "${d.title} ${d.summary} ${d.diaryText}"
                val rawScore = MemoryRetrievalPolicy.keywordScore(userMessage, searchText)
                if (rawScore > 0) {
                    val ageDays = (System.currentTimeMillis() - d.createdAt) / 86_400_000.0
                    allHits.add(MemoryRetrievalPolicy.SourceHit(
                        sourceType = "diary", sourceLabel = "日记", sourceId = d.id,
                        snippet = d.summary.take(MemoryRetrievalPolicy.MAX_SNIPPET_PER_HIT),
                        rawScore = rawScore, ageDays = ageDays
                    ))
                }
            }
        }

        // Sort by score using policy, then annotate with explanations
        val sorted = MemoryRetrievalPolicy.sortByScore(allHits).take(8).map { hit ->
            hit.copy(explain = MemoryRetrievalPolicy.explainHit(hit, userMessage))
        }

        val confirmedMdContent = MemoryMdParser.readConfirmedSection(
            FileStore.readWorkspaceFile("MEMORY.md")
        )

        val recentCards = confirmedCards.sortedByDescending { it.createdAt }.take(2)

        // Build sources with explain for F1 memory reference detail
        val sources = sorted.map { h ->
            MemorySource(
                type = h.sourceType, label = h.sourceLabel,
                snippet = h.snippet.take(30),
                explain = h.explain,
                sourceId = h.sourceId,
                timestamp = null
            )
        }

        MemoryContext(
            profileSummary = MemoryRetrievalPolicy.capProfileSummary(confirmedMdContent),
            relevantMemorySection = MemoryRetrievalPolicy.capRelevantMemory(sorted),
            memoryPrefix = MemoryRetrievalPolicy.capMemoryPrefix(sorted),
            structuredProfileInjection = buildStructuredProfileInjection(profiles),
            sources = sources
        )
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
