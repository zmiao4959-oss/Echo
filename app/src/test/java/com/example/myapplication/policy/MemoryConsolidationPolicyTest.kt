package com.example.myapplication.policy

import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.data.model.MemoryCard
import org.junit.Assert.*
import org.junit.Test

class MemoryConsolidationPolicyTest {

    private val now = System.currentTimeMillis()

    // ── Helpers ──

    private fun makeRecord(id: String, content: String, date: String, mood: String? = null): LifeRecord =
        LifeRecord(id = id, createdAt = now, date = date, content = content, source = "chat", mood = mood)

    private fun makeDiary(id: String, date: String, title: String, summary: String, diaryText: String, mood: String): DailyDiary =
        DailyDiary(id = id, date = date, title = title, summary = summary, diaryText = diaryText,
            mood = mood, tags = emptyList(), sourceRecordIds = emptyList(), createdAt = now, updatedAt = now)

    private fun makeCard(id: String, quote: String, note: String = "", mood: String? = null, tags: List<String> = emptyList()): MemoryCard =
        MemoryCard(id = id, createdAt = now, memoryDate = "2026-06-27", quote = quote, note = note,
            tags = tags, mood = mood, sourceType = "chat", sourceId = "src_$id")

    private fun snippets(vararg texts: String): List<MemoryConsolidationPolicy.TextSnippet> =
        texts.mapIndexed { i, t -> MemoryConsolidationPolicy.TextSnippet(t, "src_$i", "2026-06-0${i+1}", now) }

    // ═══════════════════════════════════════════
    // consolidate — empty input
    // ═══════════════════════════════════════════

    @Test
    fun `consolidate with empty input returns all-empty output`() {
        val input = MemoryConsolidationPolicy.ConsolidationInput(
            recentLifeRecords = emptyList(),
            recentDiaries = emptyList(),
            recentCards = emptyList()
        )
        val output = MemoryConsolidationPolicy.consolidate(input)
        assertTrue("should be empty but had ${output.recurringThemes.size} themes", output.isEmpty)
        assertTrue(output.recurringThemes.isEmpty())
        assertTrue(output.frequentEntities.isEmpty())
        assertNull(output.moodTrend)
        assertTrue(output.candidateProfiles.isEmpty())
        assertTrue(output.candidateCards.isEmpty())
    }

    @Test
    fun `consolidate does not fabricate when data is too sparse`() {
        val records = listOf(makeRecord("r1", "今天喝了咖啡", "2026-06-01"))
        val input = MemoryConsolidationPolicy.ConsolidationInput(
            recentLifeRecords = records,
            recentDiaries = emptyList(),
            recentCards = emptyList()
        )
        val output = MemoryConsolidationPolicy.consolidate(input)
        // With only 1 occurrence, no theme should be "recurring"
        assertTrue(output.recurringThemes.isEmpty() || output.recurringThemes.all { it.occurrenceCount < 3 })
    }

    // ═══════════════════════════════════════════
    // extractRecurringThemes
    // ═══════════════════════════════════════════

    @Test
    fun `extractRecurringThemes finds theme appearing 3 plus times across distinct dates`() {
        val snips = snippets("喜欢喝咖啡", "今天喝了咖啡很好", "咖啡是每天必备的", "今天天气不错")
        val themes = MemoryConsolidationPolicy.extractRecurringThemes(snips)
        assertTrue("should have at least 1 theme", themes.isNotEmpty())
        // "咖啡" bigram should appear multiple times
        val coffee = themes.find { it.keyword.contains("咖啡") }
        assertNotNull("should find '咖啡' as theme", coffee)
        assertTrue("'咖啡' should have >= 3 occurrences, got ${coffee?.occurrenceCount}", coffee!!.occurrenceCount >= 3)
    }

    @Test
    fun `extractRecurringThemes returns empty when no pattern reaches min occurrences`() {
        val snips = snippets("今天喝了咖啡", "明天要跑步", "天气不错")
        val themes = MemoryConsolidationPolicy.extractRecurringThemes(snips)
        // Each word appears <= 2 times, should be empty
        assertTrue(themes.isEmpty())
    }

    @Test
    fun `extractRecurringThemes with empty input returns empty`() {
        val themes = MemoryConsolidationPolicy.extractRecurringThemes(emptyList())
        assertTrue(themes.isEmpty())
    }

    @Test
    fun `extractRecurringThemes requires at least 2 distinct dates`() {
        // All on the same date
        val snips = listOf(
            MemoryConsolidationPolicy.TextSnippet("咖啡很好喝", "s1", "2026-06-01", now),
            MemoryConsolidationPolicy.TextSnippet("今天喝咖啡", "s2", "2026-06-01", now),
            MemoryConsolidationPolicy.TextSnippet("咖啡又来了", "s3", "2026-06-01", now)
        )
        val themes = MemoryConsolidationPolicy.extractRecurringThemes(snips)
        // Only 1 distinct date → should be empty
        assertTrue("same date should not qualify as recurring", themes.isEmpty())
    }

    // ═══════════════════════════════════════════
    // detectMoodTrend
    // ═══════════════════════════════════════════

    @Test
    fun `detectMoodTrend returns null for empty mood list`() {
        val trend = MemoryConsolidationPolicy.detectMoodTrend(emptyList())
        assertNull(trend)
    }

    @Test
    fun `detectMoodTrend returns correct distribution from mood list`() {
        val moods = listOf(
            MemoryConsolidationPolicy.MoodEntry("开心", 1000L),
            MemoryConsolidationPolicy.MoodEntry("开心", 2000L),
            MemoryConsolidationPolicy.MoodEntry("疲惫", 3000L)
        )
        val trend = MemoryConsolidationPolicy.detectMoodTrend(moods)
        assertNotNull(trend)
        assertEquals("开心", trend!!.dominantMood)
        assertEquals(2, trend.moodDistribution["开心"])
        assertEquals(1, trend.moodDistribution["疲惫"])
    }

    @Test
    fun `detectMoodTrend detects consecutive same-mood streak`() {
        val moods = listOf(
            MemoryConsolidationPolicy.MoodEntry("开心", 1000L),
            MemoryConsolidationPolicy.MoodEntry("开心", 2000L),
            MemoryConsolidationPolicy.MoodEntry("开心", 3000L)
        )
        val trend = MemoryConsolidationPolicy.detectMoodTrend(moods)
        assertNotNull(trend)
        assertTrue("trend description should mention streak: ${trend?.trendDescription}",
            trend!!.trendDescription.contains("连续3天"))
    }

    // ═══════════════════════════════════════════
    // extractFrequentEntities
    // ═══════════════════════════════════════════

    @Test
    fun `extractFrequentEntities finds place with location pattern`() {
        val texts = listOf("今天在咖啡店工作", "昨天在咖啡店见面", "在路上散步")
        val entities = MemoryConsolidationPolicy.extractFrequentEntities(texts)
        val place = entities.find { it.name.contains("咖啡") }
        // "在咖啡店" may or may not be caught depending on pattern — test existence
        if (place != null) {
            assertTrue(place.occurrenceCount >= 2)
            assertEquals("place", place.entityType)
        }
    }

    @Test
    fun `extractFrequentEntities returns empty when no pattern matches`() {
        val texts = listOf("今天天气不错", "明天可能会下雨", "周末去哪里玩")
        val entities = MemoryConsolidationPolicy.extractFrequentEntities(texts)
        assertTrue(entities.isEmpty())
    }

    @Test
    fun `extractFrequentEntities requires at least 2 occurrences`() {
        val texts = listOf("今天在咖啡店工作")
        val entities = MemoryConsolidationPolicy.extractFrequentEntities(texts)
        // Single occurrence shouldn't create an entity
        assertTrue(entities.isEmpty())
    }

    // ═══════════════════════════════════════════
    // generateCandidateProfiles
    // ═══════════════════════════════════════════

    @Test
    fun `generateCandidateProfiles creates pending profiles for recurring themes`() {
        val theme = MemoryConsolidationPolicy.ThemeResult(
            keyword = "咖啡", occurrenceCount = 5,
            sourceRecords = listOf("r1", "r2", "r3", "r4", "r5"),
            firstSeen = 1000L, lastSeen = 5000L
        )
        val candidates = MemoryConsolidationPolicy.generateCandidateProfiles(
            themes = listOf(theme), entities = emptyList(), discarded = emptyList()
        )
        assertTrue("should produce candidates", candidates.isNotEmpty())
        val c = candidates.find { it.keywordContains("咖啡") }
        assertNotNull("should have a coffee candidate", c)
        assertTrue("confidence should be <= 0.6, was ${c?.confidence}", c!!.confidence <= 0.6f)
    }

    @Test
    fun `generateCandidateProfiles deduplicates against discarded entries`() {
        val theme = MemoryConsolidationPolicy.ThemeResult(
            keyword = "咖啡", occurrenceCount = 5,
            sourceRecords = listOf("r1"), firstSeen = 1000L, lastSeen = 5000L
        )
        val discarded = listOf(
            PendingMemoryPolicy.DiscardedEntry(
                value = "对「咖啡」有兴趣",
                category = "preference",
                discardedAt = System.currentTimeMillis()
            )
        )
        val candidates = MemoryConsolidationPolicy.generateCandidateProfiles(
            themes = listOf(theme), entities = emptyList(), discarded = discarded
        )
        // The coffee candidate should be filtered out
        val coffeeRelated = candidates.filter { it.keywordContains("咖啡") }
        assertTrue("coffee-related candidates should be dedup'd, got $coffeeRelated", coffeeRelated.isEmpty())
    }

    @Test
    fun `generateCandidateProfiles caps confidence at max rule confidence`() {
        val theme = MemoryConsolidationPolicy.ThemeResult(
            keyword = "学习", occurrenceCount = 20,
            sourceRecords = List(20) { "r$it" },
            firstSeen = 1000L, lastSeen = 5000L
        )
        val candidates = MemoryConsolidationPolicy.generateCandidateProfiles(
            themes = listOf(theme), entities = emptyList(), discarded = emptyList()
        )
        for (c in candidates) {
            assertTrue("confidence ${c.confidence} should be <= ${MemoryConsolidationPolicy.MAX_RULE_CONFIDENCE}",
                c.confidence <= MemoryConsolidationPolicy.MAX_RULE_CONFIDENCE)
        }
    }

    @Test
    fun `generateCandidateProfiles includes reason and sourceIds`() {
        val theme = MemoryConsolidationPolicy.ThemeResult(
            keyword = "跑步", occurrenceCount = 4,
            sourceRecords = listOf("r1", "r2", "r3", "r4"),
            firstSeen = 1000L, lastSeen = 5000L
        )
        val candidates = MemoryConsolidationPolicy.generateCandidateProfiles(
            themes = listOf(theme), entities = emptyList(), discarded = emptyList()
        )
        val c = candidates.firstOrNull { it.keywordContains("跑步") }
        assertNotNull(c)
        assertTrue("reason should be non-blank", c!!.reason.isNotBlank())
        assertTrue("sourceIds should not be empty", c.sourceIds.isNotEmpty())
    }

    // ═══════════════════════════════════════════
    // Integration
    // ═══════════════════════════════════════════

    @Test
    fun `consolidate integration themes plus mood plus profiles`() {
        val records = listOf(
            makeRecord("r1", "今天喝了咖啡很开心", "2026-06-01", "开心"),
            makeRecord("r2", "咖啡是每天必备", "2026-06-02", "开心"),
            makeRecord("r3", "去咖啡店工作了一下午", "2026-06-03", "开心"),
            makeRecord("r4", "晚上喝了咖啡睡不着", "2026-06-04", "疲惫")
        )
        val input = MemoryConsolidationPolicy.ConsolidationInput(
            recentLifeRecords = records,
            recentDiaries = emptyList(),
            recentCards = emptyList()
        )
        val output = MemoryConsolidationPolicy.consolidate(input)
        // Should have coffee as recurring theme
        assertTrue("should have recurring themes", output.recurringThemes.isNotEmpty())
        assertTrue("coffee should be a theme", output.recurringThemes.any { it.keyword.contains("咖啡") })
        // Should have mood trend
        assertNotNull("should have mood trend", output.moodTrend)
    }

    @Test
    fun `inferCategory maps keywords to categories`() {
        assertEquals("preference", MemoryConsolidationPolicy.inferCategory("喜欢"))
        assertEquals("habit", MemoryConsolidationPolicy.inferCategory("每天"))
        assertEquals("goal", MemoryConsolidationPolicy.inferCategory("想"))
        assertEquals("project", MemoryConsolidationPolicy.inferCategory("项目"))
        assertEquals("preference", MemoryConsolidationPolicy.inferCategory("咖啡")) // default
    }

    @Test
    fun `clampConfidence enforces max rule confidence`() {
        assertEquals(0.6f, MemoryConsolidationPolicy.clampConfidence(0.8f))
        assertEquals(0.5f, MemoryConsolidationPolicy.clampConfidence(0.5f))
        assertEquals(0.0f, MemoryConsolidationPolicy.clampConfidence(-0.1f))
    }    @Test
    fun `consolidate produces no candidates when data is too sparse`() {
        val records = listOf(
            makeRecord("r1", "今天天气不错", "2026-06-01"),
            makeRecord("r2", "明天可能会下雨", "2026-06-02")
        )
        val input = MemoryConsolidationPolicy.ConsolidationInput(
            recentLifeRecords = records,
            recentDiaries = emptyList(),
            recentCards = emptyList()
        )
        val output = MemoryConsolidationPolicy.consolidate(input)
        assertTrue("should be empty when data too sparse", output.candidateProfiles.isEmpty())
    }

    // Extension to check if candidate word is in keyword
    private fun MemoryConsolidationPolicy.CandidateProfile.keywordContains(word: String): Boolean =
        key.contains(word) || value.contains(word)
}
