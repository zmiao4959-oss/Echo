package com.example.myapplication.memory

import com.example.myapplication.MyApplication
import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.data.model.UserProfileMemory
import com.example.myapplication.data.repository.DiaryRepository
import com.example.myapplication.data.repository.LifeRecordRepository
import com.example.myapplication.data.repository.MemoryRepository
import com.example.myapplication.data.store.EmbeddingCacheStore
import com.example.myapplication.policy.MemoryRetrievalPolicy
import com.example.myapplication.policy.RetrievalEngine
import com.example.myapplication.policy.SemanticRetrievalEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Unified memory injection entry point — all Agent memory injection must go through here.
 *
 * Phase I-Fix: 统一通过 [HybridRetrievalEngine] 获取检索结果，由 [AppConfig.retrievalMode] 控制模式。
 * - rule_only：行为与 Phase I 前一致（规则引擎）。
 * - hybrid：规则 + 语义合并去重，规则优先。
 * - semantic_experiment：语义为空时自动回退规则结果。
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
        val structuredProfileInjection: String,
        val sources: List<MemorySource> = emptyList()
    )

    /** 来源类型 → 中文标签 */
    private val sourceLabels = mapOf(
        "profile" to "用户画像",
        "memory_card" to "记忆卡片",
        "memory_md" to "长期记忆",
        "life_record" to "生活记录",
        "diary" to "日记"
    )

    suspend fun build(userMessage: String): MemoryContext = withContext(Dispatchers.IO) {
        val app = MyApplication.instance
        val config = app.appConfig

        // ── 1. 获取原始数据（与 Phase I 前一致） ──
        val repo = MemoryRepository()
        val recordRepo = LifeRecordRepository()
        val diaryRepo = DiaryRepository()

        val confirmedCards = repo.getAllCards().filter { it.status == "confirmed" }
        val profiles = repo.getEnabledProfiles().filter { it.status == "confirmed" }

        // ── 2. 收集 SourceHits（规则引擎数据） ──
        val allHits = collectAllHits(userMessage, repo, recordRepo, diaryRepo, confirmedCards, profiles)

        // ── 3. 收集 CandidateEntry（语义引擎数据） ──
        val allCandidates = collectAllCandidates(confirmedCards, profiles, recordRepo, diaryRepo)

        // ── 4. 加载引擎 + 检索 ──
        app.ruleEngine.loadData(allHits)
        app.semanticEngine.loadData(allCandidates)
        app.hybridEngine.mode = config.retrievalMode

        val retrievalResults = if (userMessage.isNotBlank()) {
            app.hybridEngine.retrieve(
                RetrievalEngine.RetrievalRequest(userMessage, limit = 8)
            )
        } else {
            emptyList()
        }

        // 持久化 embedding 缓存（避免重启后重新向量化）
        if (config.retrievalMode != "rule_only") {
            try {
                EmbeddingCacheStore.save(app.semanticEngine.exportCache())
            } catch (_: Exception) {}
        }

        // ── 5. 转换结果用于格式化 ──
        val sorted = retrievalResults.map { it.toSourceHit() }

        // ── 6. 构建记忆上下文（格式化与 Phase I 前一致） ──
        val confirmedMdContent = MemoryMdParser.readConfirmedSection(
            FileStore.readWorkspaceFile("MEMORY.md")
        )

        val recentCards = confirmedCards.sortedByDescending { it.createdAt }.take(2)

        val sources = retrievalResults.map { r ->
            MemorySource(
                type = r.sourceType,
                label = sourceLabels[r.sourceType] ?: r.sourceType,
                snippet = r.snippet.take(30),
                explain = r.explain,
                sourceId = r.sourceId,
                timestamp = r.timestamp
            )
        }

        MemoryContext(
            profileSummary = MemoryRetrievalPolicy.capProfileSummary(confirmedMdContent),
            relevantMemorySection = MemoryRetrievalPolicy.capRelevantMemory(sorted),
            structuredProfileInjection = buildStructuredProfileInjection(profiles),
            sources = sources
        )
    }

    // ── 数据收集：规则引擎用 SourceHit ──

    private suspend fun collectAllHits(
        userMessage: String,
        repo: MemoryRepository,
        recordRepo: LifeRecordRepository,
        diaryRepo: DiaryRepository,
        confirmedCards: List<MemoryCard>,
        profiles: List<UserProfileMemory>
    ): List<MemoryRetrievalPolicy.SourceHit> {
        val hits = mutableListOf<MemoryRetrievalPolicy.SourceHit>()

        // 1. MEMORY.md
        if (userMessage.isNotBlank()) {
            val mdResults = MemorySearch.keywordSearch(FileStore.workspaceDir, userMessage, maxResults = 5)
            for (r in mdResults) {
                val rawScore = MemoryRetrievalPolicy.keywordScore(userMessage, r.snippet)
                hits.add(MemoryRetrievalPolicy.SourceHit(
                    sourceType = "memory_md", sourceLabel = "长期记忆", sourceId = r.filePath,
                    snippet = r.snippet.take(MemoryRetrievalPolicy.MAX_SNIPPET_PER_HIT),
                    rawScore = rawScore
                ))
            }
        }

        // 2. UserProfileMemory
        if (userMessage.isNotBlank()) {
            for (p in profiles) {
                val rawScore = MemoryRetrievalPolicy.keywordScore(userMessage, p.value)
                if (rawScore > 0) {
                    hits.add(MemoryRetrievalPolicy.SourceHit(
                        sourceType = "profile", sourceLabel = "用户画像", sourceId = p.id,
                        snippet = p.value.take(MemoryRetrievalPolicy.MAX_SNIPPET_PER_HIT),
                        rawScore = rawScore
                    ))
                }
            }
        }

        // 3. MemoryCard
        if (userMessage.isNotBlank()) {
            for (c in confirmedCards) {
                val rawScore = MemoryRetrievalPolicy.keywordScore(userMessage, c.quote + " " + c.note)
                if (rawScore > 0) {
                    hits.add(MemoryRetrievalPolicy.SourceHit(
                        sourceType = "memory_card", sourceLabel = "记忆卡片", sourceId = c.id,
                        snippet = c.quote.take(MemoryRetrievalPolicy.MAX_SNIPPET_PER_HIT),
                        rawScore = rawScore, pinned = c.pinned
                    ))
                }
            }
        }

        // 4. LifeRecord
        if (userMessage.isNotBlank()) {
            val recentRecords = getRecentRecords(recordRepo, 30)
            for (r in recentRecords) {
                val rawScore = MemoryRetrievalPolicy.keywordScore(userMessage, r.content)
                if (rawScore > 0) {
                    val ageDays = (System.currentTimeMillis() - r.createdAt) / 86_400_000.0
                    hits.add(MemoryRetrievalPolicy.SourceHit(
                        sourceType = "life_record", sourceLabel = "生活记录", sourceId = r.id,
                        snippet = r.content.take(MemoryRetrievalPolicy.MAX_SNIPPET_PER_HIT),
                        rawScore = rawScore, ageDays = ageDays
                    ))
                }
            }
        }

        // 5. Diary
        if (userMessage.isNotBlank()) {
            val recentDiaries = getRecentDiaries(diaryRepo, 30)
            for (d in recentDiaries) {
                val searchText = "${d.title} ${d.summary} ${d.diaryText}"
                val rawScore = MemoryRetrievalPolicy.keywordScore(userMessage, searchText)
                if (rawScore > 0) {
                    val ageDays = (System.currentTimeMillis() - d.createdAt) / 86_400_000.0
                    hits.add(MemoryRetrievalPolicy.SourceHit(
                        sourceType = "diary", sourceLabel = "日记", sourceId = d.id,
                        snippet = d.summary.take(MemoryRetrievalPolicy.MAX_SNIPPET_PER_HIT),
                        rawScore = rawScore, ageDays = ageDays
                    ))
                }
            }
        }

        return hits
    }

    // ── 数据收集：语义引擎用 CandidateEntry ──

    private suspend fun collectAllCandidates(
        confirmedCards: List<MemoryCard>,
        profiles: List<UserProfileMemory>,
        recordRepo: LifeRecordRepository,
        diaryRepo: DiaryRepository
    ): List<SemanticRetrievalEngine.CandidateEntry> {
        val candidates = mutableListOf<SemanticRetrievalEngine.CandidateEntry>()

        // Profiles
        for (p in profiles) {
            candidates.add(SemanticRetrievalEngine.CandidateEntry(
                sourceType = "profile", sourceId = p.id,
                text = p.value, enabled = true, status = "confirmed",
                timestamp = p.updatedAt
            ))
        }

        // MemoryCards
        for (c in confirmedCards) {
            candidates.add(SemanticRetrievalEngine.CandidateEntry(
                sourceType = "memory_card", sourceId = c.id,
                text = "${c.quote} ${c.note}", enabled = true, status = "confirmed",
                timestamp = c.createdAt
            ))
        }

        // MEMORY.md confirmed section
        try {
            val md = FileStore.readWorkspaceFile("MEMORY.md")
            val confirmed = MemoryMdParser.readConfirmedSection(md)
            if (confirmed.isNotBlank()) {
                candidates.add(SemanticRetrievalEngine.CandidateEntry(
                    sourceType = "memory_md", sourceId = "MEMORY.md",
                    text = confirmed, enabled = true, status = "confirmed",
                    timestamp = null
                ))
            }
        } catch (_: Exception) {}

        // LifeRecords (recent 30 days)
        try {
            for (r in getRecentRecords(recordRepo, 30)) {
                candidates.add(SemanticRetrievalEngine.CandidateEntry(
                    sourceType = "life_record", sourceId = r.id,
                    text = r.content, enabled = true, status = "confirmed",
                    timestamp = r.createdAt
                ))
            }
        } catch (_: Exception) {}

        // Diaries (recent 30 days)
        try {
            for (d in getRecentDiaries(diaryRepo, 30)) {
                val text = "${d.title} ${d.summary} ${d.diaryText}"
                candidates.add(SemanticRetrievalEngine.CandidateEntry(
                    sourceType = "diary", sourceId = d.id,
                    text = text, enabled = true, status = "confirmed",
                    timestamp = d.createdAt
                ))
            }
        } catch (_: Exception) {}

        return candidates
    }

    // ── 转换：RetrievalResult → SourceHit（兼容现有 formatter） ──

    private fun RetrievalEngine.RetrievalResult.toSourceHit(): MemoryRetrievalPolicy.SourceHit {
        return MemoryRetrievalPolicy.SourceHit(
            sourceType = sourceType,
            sourceLabel = sourceLabels[sourceType] ?: sourceType,
            sourceId = sourceId,
            snippet = snippet,
            rawScore = score,
            explain = explain
        )
    }

    // ── 工具方法 ──

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
