package com.example.myapplication.policy

import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.data.model.MemoryCard

/**
 * Pure Kotlin: Rule-based memory consolidation from recent records.
 *
 * Extracts recurring themes, frequent entities, mood trends, and generates
 * low-confidence candidate profiles/cards for user confirmation.
 *
 * Zero Android dependencies — testable with plain JUnit.
 * No LLM required — rule-based only.
 * Never fabricates — every candidate must be traceable to source text.
 */
object MemoryConsolidationPolicy {

    // ── Input ──

    data class ConsolidationInput(
        val recentLifeRecords: List<LifeRecord>,       // recent N days (recommend 14)
        val recentDiaries: List<DailyDiary>,            // recent N days (recommend 14)
        val recentCards: List<MemoryCard>,              // recent N days (recommend 30)
        val existingDiscarded: List<PendingMemoryPolicy.DiscardedEntry> = emptyList()
    )

    // ── Output ──

    data class ConsolidationOutput(
        val recurringThemes: List<ThemeResult>,
        val frequentEntities: List<EntityResult>,
        val moodTrend: MoodTrend?,
        val candidateProfiles: List<CandidateProfile>,
        val candidateCards: List<CandidateCard>
    ) {
        /** True when no themes, entities, or candidates were found. */
        val isEmpty: Boolean get() =
            recurringThemes.isEmpty() && frequentEntities.isEmpty() &&
            candidateProfiles.isEmpty() && candidateCards.isEmpty()
    }

    data class ThemeResult(
        val keyword: String,
        val occurrenceCount: Int,
        val sourceRecords: List<String>,   // record/card IDs
        val firstSeen: Long,
        val lastSeen: Long
    )

    data class EntityResult(
        val entityType: String,            // "person" | "place" | "project" | "activity"
        val name: String,
        val occurrenceCount: Int,
        val contexts: List<String>         // surrounding snippets
    )

    data class MoodTrend(
        val dominantMood: String,
        val moodDistribution: Map<String, Int>,
        val trendDescription: String       // e.g. "连续3天心情愉快"
    )

    data class CandidateProfile(
        val key: String,
        val value: String,
        val category: String,              // preference | habit | goal | identity | project | relationship
        val confidence: Float,             // 0.0–1.0; rule-based capped at 0.6
        val reason: String,
        val sourceIds: List<String>
    )

    data class CandidateCard(
        val quote: String,
        val note: String,
        val tags: List<String>,
        val mood: String?,
        val confidence: Float,
        val reason: String,
        val sourceId: String
    )

    /** Minimum occurrences across distinct days to qualify as a "recurring theme". */
    const val MIN_THEME_OCCURRENCES = 3

    /** Minimum occurrences for entity detection. */
    const val MIN_ENTITY_OCCURRENCES = 2

    /** Maximum confidence for rule-based candidates (never confirmed auto). */
    const val MAX_RULE_CONFIDENCE = 0.6f

    /** Minimum consecutive same-mood days to detect a trend. */
    const val MIN_TREND_DAYS = 3

    /** Maximum number of candidate profiles to generate (avoid overwhelming user). */
    const val MAX_CANDIDATE_PROFILES = 5

    // ── Public API ──

    /**
     * Main entry point: run all consolidation analyses and return unified output.
     */
    fun consolidate(input: ConsolidationInput): ConsolidationOutput {
        if (input.recentLifeRecords.isEmpty() && input.recentDiaries.isEmpty() && input.recentCards.isEmpty()) {
            return ConsolidationOutput(emptyList(), emptyList(), null, emptyList(), emptyList())
        }

        // Collect all text for theme/entity extraction, with metadata
        val textSnippets = mutableListOf<TextSnippet>()
        val moodEntries = mutableListOf<MoodEntry>()
        val allThemes = mutableListOf<ThemeResult>()
        val allEntities = mutableListOf<EntityResult>()

        // From LifeRecords
        for (r in input.recentLifeRecords) {
            textSnippets.add(TextSnippet(r.content, r.id, r.date, r.createdAt))
            if (r.mood != null && r.mood.isNotBlank()) {
                moodEntries.add(MoodEntry(r.mood, r.createdAt))
            }
        }

        // From Diaries
        for (d in input.recentDiaries) {
            val diaryText = listOfNotNull(d.title, d.summary, d.diaryText).joinToString(" ")
            textSnippets.add(TextSnippet(diaryText, d.id, d.date, d.createdAt))
            if (d.mood.isNotBlank()) {
                moodEntries.add(MoodEntry(d.mood, d.createdAt))
            }
        }

        // From MemoryCards
        for (c in input.recentCards) {
            val cardText = listOfNotNull(c.quote, c.note).joinToString(" ")
            textSnippets.add(TextSnippet(cardText, c.id, c.memoryDate, c.createdAt))
            if (c.mood != null && c.mood.isNotBlank()) {
                moodEntries.add(MoodEntry(c.mood, c.createdAt))
            }
        }

        // Extract
        val allTexts = textSnippets.map { it.text }
        val themes = extractRecurringThemes(textSnippets)
        val entities = extractFrequentEntities(allTexts)
        val moodTrend = detectMoodTrend(moodEntries.sortedBy { it.timestamp })
        val profiles = generateCandidateProfiles(themes, entities, input.existingDiscarded)
        val cards = generateCandidateCards(themes, textSnippets, input.existingDiscarded)

        return ConsolidationOutput(
            recurringThemes = themes,
            frequentEntities = entities,
            moodTrend = moodTrend,
            candidateProfiles = profiles.take(MAX_CANDIDATE_PROFILES),
            candidateCards = cards.take(MAX_CANDIDATE_PROFILES)
        )
    }

    // ── Theme Extraction ──

    /**
     * Extract recurring themes from text snippets using bigram frequency.
     * A theme is "recurring" when count >= MIN_THEME_OCCURRENCES
     * AND appears across >= 2 distinct dates.
     */
    fun extractRecurringThemes(snippets: List<TextSnippet>, topN: Int = 10): List<ThemeResult> {
        if (snippets.isEmpty()) return emptyList()

        // Build bigram → (count, sourceIds, firstSeen, lastSeen, dates)
        data class Acc(var count: Int = 0, val sourceIds: MutableList<String> = mutableListOf(),
                       var firstSeen: Long = Long.MAX_VALUE, var lastSeen: Long = 0L,
                       val dates: MutableSet<String> = mutableSetOf())

        val bigramAcc = mutableMapOf<String, Acc>()

        for (snip in snippets) {
            val bigrams = extractBigrams(snip.text)
            for (bg in bigrams) {
                val acc = bigramAcc.getOrPut(bg) { Acc() }
                acc.count++
                if (snip.sourceId !in acc.sourceIds) acc.sourceIds.add(snip.sourceId)
                if (snip.timestamp < acc.firstSeen) acc.firstSeen = snip.timestamp
                if (snip.timestamp > acc.lastSeen) acc.lastSeen = snip.timestamp
                acc.dates.add(snip.date)
            }
        }

        return bigramAcc.entries
            .filter { it.value.count >= MIN_THEME_OCCURRENCES && it.value.dates.size >= 2 }
            .sortedByDescending { it.value.count }
            .take(topN)
            .map { (keyword, acc) ->
                ThemeResult(
                    keyword = keyword,
                    occurrenceCount = acc.count,
                    sourceRecords = acc.sourceIds.toList(),
                    firstSeen = acc.firstSeen,
                    lastSeen = acc.lastSeen
                )
            }
    }

    /**
     * Extract bigrams from text, filtering stop words and punctuation.
     * Reuses the same bigram approach as WeeklyAggregationPolicy and MemoryRetrievalPolicy.
     */
    fun extractBigrams(text: String): List<String> {
        val results = mutableListOf<String>()
        val punctChars = setOf(',', '.', '!', '?', ';', ':', '"', '\'', '(', ')',
            '[', ']', '「', '」', '“', '”', '、', '。', '，', '．', '？', '！', '；', '：')

        val segments = text.split("\\s+".toRegex())
        for (seg in segments) {
            if (seg.isEmpty()) continue
            val hasCJK = seg.any { isCJK(it) }
            if (hasCJK) {
                if (seg.length >= 2) {
                    for (i in 0..seg.length - 2) {
                        val bg = seg.substring(i, i + 2)
                        if (bg.length >= 2 && bg.none { it.isWhitespace() || it in punctChars }) {
                            val lower = bg.lowercase()
                            if (lower !in WeeklyAggregationPolicy.STOP_WORDS) {
                                results.add(lower)
                            }
                        }
                    }
                }
            } else {
                val lower = seg.lowercase()
                if (lower.length >= 2 && lower !in WeeklyAggregationPolicy.STOP_WORDS) {
                    results.add(lower)
                }
            }
        }
        return results
    }

    // ── Mood Trend Detection ──

    /**
     * Detect mood trends from time-ordered mood entries.
     * Returns null if insufficient data or no clear trend.
     */
    fun detectMoodTrend(moods: List<MoodEntry>): MoodTrend? {
        if (moods.isEmpty()) return null

        // Distribution
        val distribution = moods.groupBy { it.mood }.mapValues { it.value.size }

        // Dominant mood (mode)
        val dominant = distribution.maxByOrNull { it.value } ?: return null

        // Check for consecutive same-mood streak (>= MIN_TREND_DAYS)
        val sorted = moods.sortedBy { it.timestamp }
        var streakLength = 1
        var maxStreakLength = 1
        var streakMood = sorted.first().mood
        var maxStreakMood = streakMood

        for (i in 1 until sorted.size) {
            if (sorted[i].mood == sorted[i - 1].mood) {
                streakLength++
            } else {
                if (streakLength > maxStreakLength) {
                    maxStreakLength = streakLength
                    maxStreakMood = sorted[i - 1].mood
                }
                streakLength = 1
            }
        }
        if (streakLength > maxStreakLength) {
            maxStreakLength = streakLength
            maxStreakMood = sorted.last().mood
        }

        val trendDescription = if (maxStreakLength >= MIN_TREND_DAYS) {
            "连续${maxStreakLength}天心情${maxStreakMood}"
        } else {
            "情绪以${dominant.key}为主，共${dominant.value}天"
        }

        return MoodTrend(
            dominantMood = dominant.key,
            moodDistribution = distribution,
            trendDescription = trendDescription
        )
    }

    // ── Entity Extraction ──

    /**
     * Extract frequent entities (people, places, projects, activities) using
     * simple pattern matching. Designed for Chinese text.
     *
     * Patterns:
     * - place: "在XX" followed by location-like context
     * - person: "和XX" or "跟XX" suggesting companionship
     * - project: "XX项目" or "XX计划"
     * - activity: "去XX" or "做XX" suggesting activities
     */
    fun extractFrequentEntities(texts: List<String>): List<EntityResult> {
        val entities = mutableMapOf<String, MutableList<String>>()  // key → contexts

        // Place patterns: 在+{location-like word}
        val placePattern = Regex("在([\\u4e00-\\u9fff㐀-\\u4dbf]{1,8}(?:馆|店|园|城|家|公司|学校|医院|超市|商场|公园|广场|餐厅|咖啡|图书馆|健身房|办公室|工作室)?)")
        // Person patterns: 和/跟/同 + {name-like word}
        val personPattern = Regex("[和跟同]([\\u4e00-\\u9fff㐀-\\u4dbf]{1,6}(?:老师|同学|朋友|同事|家人|妈妈|爸爸|姐姐|妹妹|哥哥|弟弟)?)")
        // Project patterns: XX项目, XX计划
        val projectPattern = Regex("([\\u4e00-\\u9fff㐀-\\u4dbf]{1,10}(?:项目|计划|方案|工作|任务))")
        // Activity patterns: 去/做 + activity-like word
        val activityPattern = Regex("[去做搞]([\\u4e00-\\u9fff㐀-\\u4dbf]{1,6}(?:运动|跑步|游泳|健身|瑜伽|旅行|旅游|爬山|做饭|看书|学习|写作|画画|练琴)?)")

        val patterns = listOf(
            "place" to placePattern,
            "person" to personPattern,
            "project" to projectPattern,
            "activity" to activityPattern
        )

        for (text in texts) {
            for ((type, pattern) in patterns) {
                val matches = pattern.findAll(text)
                for (m in matches) {
                    val name = m.groupValues[1].trim()
                    if (name.length in 1..8) {
                        val key = "$type::$name"
                        entities.getOrPut(key) { mutableListOf() }.add(text.around(m.range, 20))
                    }
                }
            }
        }

        return entities.entries
            .filter { it.value.size >= MIN_ENTITY_OCCURRENCES }
            .sortedByDescending { it.value.size }
            .map { (key, contexts) ->
                val parts = key.split("::", limit = 2)
                EntityResult(
                    entityType = parts[0],
                    name = parts[1],
                    occurrenceCount = contexts.size,
                    contexts = contexts.take(5)
                )
            }
    }

    // ── Candidate Profile Generation ──

    /**
     * Generate candidate UserProfileMemory entries from recurring themes and entities.
     * All candidates have low confidence (capped at MAX_RULE_CONFIDENCE).
     * Deduplicates against discarded entries via PendingMemoryPolicy.isSimilar().
     */
    fun generateCandidateProfiles(
        themes: List<ThemeResult>,
        entities: List<EntityResult>,
        discarded: List<PendingMemoryPolicy.DiscardedEntry>
    ): List<CandidateProfile> {
        val candidates = mutableListOf<CandidateProfile>()

        // Themes → candidate profiles (preference or habit)
        for (theme in themes) {
            val category = inferCategory(theme.keyword)
            val value = inferValue(theme.keyword, category)
            candidates.add(CandidateProfile(
                key = "关于「${theme.keyword}」的偏好",
                value = value,
                category = category,
                confidence = clampConfidence(0.3f + theme.occurrenceCount * 0.05f),
                reason = "近期${theme.occurrenceCount}次提及「${theme.keyword}」",
                sourceIds = theme.sourceRecords
            ))
        }

        // Entities → candidate profiles
        for (entity in entities) {
            val profile = when (entity.entityType) {
                "place" -> CandidateProfile(
                    key = "常去地点",
                    value = "经常去「${entity.name}」",
                    category = "habit",
                    confidence = clampConfidence(0.3f + entity.occurrenceCount * 0.05f),
                    reason = "近期${entity.occurrenceCount}次提及地点「${entity.name}」",
                    sourceIds = emptyList()
                )
                "person" -> CandidateProfile(
                    key = "重要人际关系",
                    value = "经常和「${entity.name}」在一起",
                    category = "relationship",
                    confidence = clampConfidence(0.3f + entity.occurrenceCount * 0.05f),
                    reason = "近期${entity.occurrenceCount}次提及「${entity.name}」",
                    sourceIds = emptyList()
                )
                "project" -> CandidateProfile(
                    key = "当前项目",
                    value = "在进行「${entity.name}」",
                    category = "project",
                    confidence = clampConfidence(0.3f + entity.occurrenceCount * 0.05f),
                    reason = "近期${entity.occurrenceCount}次提及「${entity.name}」",
                    sourceIds = emptyList()
                )
                "activity" -> CandidateProfile(
                    key = "习惯活动",
                    value = "经常「${entity.name}」",
                    category = "habit",
                    confidence = clampConfidence(0.3f + entity.occurrenceCount * 0.05f),
                    reason = "近期${entity.occurrenceCount}次提及活动「${entity.name}」",
                    sourceIds = emptyList()
                )
                else -> continue
            }
            candidates.add(profile)
        }

        // Deduplicate against discarded entries
        val filtered = candidates.filter { candidate ->
            !PendingMemoryPolicy.isSimilar(candidate.value, candidate.category, discarded)
        }

        // Sort by confidence descending, but all pending
        return filtered.sortedByDescending { it.confidence }
    }

    // ── Candidate Card Generation ──

    /**
     * Generate candidate MemoryCard entries from recurring themes.
     * Only suggests cards when themes are emotionally meaningful or interesting.
     */
    fun generateCandidateCards(
        themes: List<ThemeResult>,
        snippets: List<TextSnippet>,
        discarded: List<PendingMemoryPolicy.DiscardedEntry>
    ): List<CandidateCard> {
        // For now, generate candidate cards from top themes only
        val cards = mutableListOf<CandidateCard>()

        for (theme in themes.take(3)) {
            // Find a representative snippet
            val matchingSnippet = snippets.find { snip ->
                extractBigrams(snip.text).any { it == theme.keyword }
            } ?: continue

            val quote = matchingSnippet.text.take(100).trim()
            if (quote.length < 5) continue

            cards.add(CandidateCard(
                quote = quote,
                note = "主题「${theme.keyword}」出现了${theme.occurrenceCount}次",
                tags = listOf("成长轨迹", theme.keyword),
                mood = null,
                confidence = clampConfidence(0.3f + theme.occurrenceCount * 0.05f),
                reason = "基于近期${theme.occurrenceCount}次提及「${theme.keyword}」",
                sourceId = theme.sourceRecords.firstOrNull() ?: ""
            ))
        }

        return cards
    }

    // ── Utility ──

    internal fun clampConfidence(raw: Float): Float =
        raw.coerceIn(0.0f, MAX_RULE_CONFIDENCE)

    /**
     * Infer UserProfileMemory category from keyword context.
     */
    fun inferCategory(keyword: String): String {
        // Preference-like keywords
        val preferenceIndicators = setOf("喜欢", "爱", "偏好", "最爱", "讨厌", "不喜欢")
        val habitIndicators = setOf("每天", "总是", "习惯", "经常", "通常", "一直", "每次")
        val goalIndicators = setOf("想", "要", "准备", "打算", "目标", "希望", "计划")
        val projectIndicators = setOf("项目", "工作", "任务", "学习", "考试", "面试")

        for (indicator in preferenceIndicators) {
            if (keyword.contains(indicator)) return "preference"
        }
        for (indicator in habitIndicators) {
            if (keyword.contains(indicator)) return "habit"
        }
        for (indicator in goalIndicators) {
            if (keyword.contains(indicator)) return "goal"
        }
        for (indicator in projectIndicators) {
            if (keyword.contains(indicator)) return "project"
        }

        // Default: preference for simple keywords
        return "preference"
    }

    /**
     * Build a human-readable value string from keyword and category.
     */
    fun inferValue(keyword: String, category: String): String = when (category) {
        "preference" -> "对「${keyword}」有兴趣"
        "habit" -> "有与「${keyword}」相关的习惯"
        "goal" -> "有与「${keyword}」相关的目标"
        "project" -> "在参与与「${keyword}」相关的项目"
        "identity" -> "与「${keyword}」相关的身份特征"
        "relationship" -> "与「${keyword}」相关的人际关系"
        else -> "与「${keyword}」相关"
    }

    private fun isCJK(c: Char): Boolean = c in '一'..'鿿' || c in '㐀'..'䶿'

    // ── Internal data classes ──

    data class TextSnippet(
        val text: String,
        val sourceId: String,
        val date: String,          // yyyy-MM-dd
        val timestamp: Long
    )

    data class MoodEntry(
        val mood: String,
        val timestamp: Long
    )

    /** Extract a context window around a match range. */
    private fun String.around(range: IntRange, window: Int): String {
        val start = (range.first - window).coerceAtLeast(0)
        val end = (range.last + 1 + window).coerceAtMost(this.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < this.length) "…" else ""
        return "$prefix${this.substring(start, end)}$suffix"
    }
}
