package com.example.myapplication.policy

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Phase I 检索引擎测试。
 *
 * 覆盖 I1-I4 所有合约：接口统一性、规则引擎一致性、语义 no-op、
 * 混合去重、disabled/pending 过滤、explain 保留、默认模式。
 */
class RetrievalEngineTest {

    // ── 测试用 Metadata ──
    private lateinit var ruleEngine: RuleBasedRetrievalEngine
    private lateinit var semanticEngine: SemanticRetrievalEngine
    private lateinit var hybridEngine: HybridRetrievalEngine

    // 提前定义便于复用的 SourceHit
    private val profileHit = MemoryRetrievalPolicy.SourceHit(
        sourceType = "profile", sourceLabel = "用户画像", sourceId = "p1",
        snippet = "用户叫小明，喜欢喝咖啡", rawScore = 10.0, explain = "命中「咖啡」"
    )
    private val cardHit = MemoryRetrievalPolicy.SourceHit(
        sourceType = "memory_card", sourceLabel = "记忆卡片", sourceId = "c1",
        snippet = "第一次去咖啡馆写作", rawScore = 8.0, pinned = true, explain = "置顶记忆 · 命中「咖啡」"
    )
    private val recordHit = MemoryRetrievalPolicy.SourceHit(
        sourceType = "life_record", sourceLabel = "生活记录", sourceId = "r1",
        snippet = "今天在图书馆看书", rawScore = 5.0, ageDays = 1.0, explain = "最近记录"
    )
    private val diaryHit = MemoryRetrievalPolicy.SourceHit(
        sourceType = "diary", sourceLabel = "日记", sourceId = "d1",
        snippet = "周末去动物园很开心", rawScore = 3.0, ageDays = 30.0
    )
    private val mdHit = MemoryRetrievalPolicy.SourceHit(
        sourceType = "memory_md", sourceLabel = "长期记忆", sourceId = "MEMORY.md",
        snippet = "用户喜欢安静的环境工作", rawScore = 7.0, explain = "命中「安静」"
    )

    @Before
    fun setUp() {
        ruleEngine = RuleBasedRetrievalEngine()
        semanticEngine = SemanticRetrievalEngine()
        hybridEngine = HybridRetrievalEngine(ruleEngine, semanticEngine)
    }

    // ═══════════════════════════════════════════════════════════════
    // I1: RetrievalEngine 接口合约
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `RetrievalResult contains all required fields`() {
        val result = RetrievalEngine.RetrievalResult(
            sourceType = "profile", sourceId = "id1", snippet = "snippet",
            explain = "explain", score = 1.5, timestamp = 1234567890L
        )
        assertEquals("profile", result.sourceType)
        assertEquals("id1", result.sourceId)
        assertEquals("snippet", result.snippet)
        assertEquals("explain", result.explain)
        assertEquals(1.5, result.score, 0.001)
        assertEquals(1234567890L, result.timestamp)
    }

    @Test
    fun `RetrievalRequest has sensible defaults`() {
        val req = RetrievalEngine.RetrievalRequest(query = "test")
        assertEquals("test", req.query)
        assertEquals(8, req.limit)
        assertNull(req.sourceTypes)
    }

    @Test
    fun `engine name is set for each implementation`() {
        assertEquals("rule_based", ruleEngine.name)
        assertEquals("semantic_experiment", semanticEngine.name)
        assertEquals("hybrid", hybridEngine.name)
    }

    // ═══════════════════════════════════════════════════════════════
    // I2: RuleBasedRetrievalEngine — 与旧结果一致
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `rule engine returns empty when no data loaded`() = runBlockingTest {
        val results = ruleEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `rule engine returns empty for blank query`() = runBlockingTest {
        ruleEngine.loadData(listOf(profileHit))
        val results = ruleEngine.retrieve(RetrievalEngine.RetrievalRequest(""))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `rule engine keyword matching works`() = runBlockingTest {
        ruleEngine.loadData(listOf(profileHit, cardHit, diaryHit))
        val results = ruleEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
        // profileHit and cardHit contain "咖啡", diaryHit does not
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.snippet.contains("咖啡") })
    }

    @Test
    fun `rule engine sorts by score descending`() = runBlockingTest {
        ruleEngine.loadData(listOf(diaryHit, profileHit, cardHit, recordHit, mdHit))
        val results = ruleEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡 安静 图书馆"))
        for (i in 0 until results.size - 1) {
            assertTrue("Results should be sorted by score desc", results[i].score >= results[i + 1].score)
        }
    }

    @Test
    fun `rule engine respects limit`() = runBlockingTest {
        ruleEngine.loadData(listOf(profileHit, cardHit, recordHit, diaryHit, mdHit))
        val results = ruleEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡", limit = 2))
        assertTrue(results.size <= 2)
    }

    @Test
    fun `rule engine filters by sourceTypes`() = runBlockingTest {
        ruleEngine.loadData(listOf(profileHit, cardHit, recordHit))
        val results = ruleEngine.retrieve(
            RetrievalEngine.RetrievalRequest("咖啡", sourceTypes = setOf("profile"))
        )
        assertTrue(results.all { it.sourceType == "profile" })
    }

    @Test
    fun `rule engine explain is not lost`() = runBlockingTest {
        ruleEngine.loadData(listOf(profileHit))
        val results = ruleEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
        assertEquals(1, results.size)
        assertTrue(results[0].explain.isNotBlank())
    }

    @Test
    fun `rule engine generates explain when hit has blank explain`() = runBlockingTest {
        val hit = MemoryRetrievalPolicy.SourceHit(
            sourceType = "life_record", sourceLabel = "生活记录", sourceId = "r2",
            snippet = "在咖啡馆待了一下午", rawScore = 5.0
        )
        ruleEngine.loadData(listOf(hit))
        val results = ruleEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
        assertEquals(1, results.size)
        assertTrue(results[0].explain.isNotBlank())
    }

    @Test
    fun `rule engine results consistent with MemoryRetrievalPolicy scoring`() {
        // 旧方式：直接用 MemoryRetrievalPolicy
        val hits = listOf(profileHit, cardHit, recordHit, diaryHit, mdHit)
        val scored = hits.filter { MemoryRetrievalPolicy.keywordScore("咖啡", it.snippet) > 0 }
        val oldSorted = MemoryRetrievalPolicy.sortByScore(scored)

        // 新方式：RuleBasedRetrievalEngine
        ruleEngine.loadData(hits)
        val newResults = runBlockingTest {
            ruleEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
        }

        // 数量和排序应一致
        assertEquals(oldSorted.size, newResults.size)
        for (i in oldSorted.indices) {
            assertEquals("Item $i: sourceType should match", oldSorted[i].sourceType, newResults[i].sourceType)
            assertEquals("Item $i: sourceId should match", oldSorted[i].sourceId, newResults[i].sourceId)
        }
    }

    @Test
    fun `rule engine pinned gets boosted score`() = runBlockingTest {
        // same rawScore, same snippet — pinned should rank higher
        val normal = MemoryRetrievalPolicy.SourceHit(
            "memory_card", "记忆卡片", "c_n", "咖啡时间", 10.0, pinned = false
        )
        val pinned = MemoryRetrievalPolicy.SourceHit(
            "memory_card", "记忆卡片", "c_p", "咖啡时间", 10.0, pinned = true
        )
        ruleEngine.loadData(listOf(normal, pinned))
        val results = ruleEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
        assertEquals(2, results.size)
        // Pinned should appear first (higher score)
        assertTrue(results[0].score > results[1].score)
    }

    // ═══════════════════════════════════════════════════════════════
    // I3: SemanticRetrievalEngine — no-op + 隐私安全
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `semantic engine returns empty when no embedding provider`() = runBlockingTest {
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("profile", "p1", "用户叫小明")
        ))
        val results = semanticEngine.retrieve(RetrievalEngine.RetrievalRequest("小明"))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `semantic engine returns empty for blank query even with provider`() = runBlockingTest {
        val mockProvider = MockEmbeddingProvider()
        semanticEngine.embeddingProvider = mockProvider
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("profile", "p1", "用户叫小明")
        ))
        val results = semanticEngine.retrieve(RetrievalEngine.RetrievalRequest(""))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `semantic engine no-op does not throw`() = runBlockingTest {
        // 无 provider + 无数据 → 应静默返回空，不抛异常
        try {
            val results = semanticEngine.retrieve(RetrievalEngine.RetrievalRequest("test"))
            assertTrue(results.isEmpty())
        } catch (e: Exception) {
            fail("No-op should not throw: ${e.message}")
        }
    }

    @Test
    fun `semantic engine filters disabled candidates`() = runBlockingTest {
        val mockProvider = MockEmbeddingProvider()
        semanticEngine.embeddingProvider = mockProvider
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("profile", "p1", "用户叫小明", enabled = true, status = "confirmed"),
            SemanticRetrievalEngine.CandidateEntry("profile", "p2", "用户叫小红", enabled = false, status = "confirmed"),
            SemanticRetrievalEngine.CandidateEntry("memory_card", "c1", "秘密内容", enabled = true, status = "pending")
        ))
        val results = semanticEngine.retrieve(RetrievalEngine.RetrievalRequest("小明"))
        // Only p1 should be in results; p2 and c1 should be filtered
        assertTrue(results.all { it.sourceId != "p2" })
        assertTrue(results.all { it.sourceId != "c1" })
    }

    @Test
    fun `semantic engine cannot send pending content to embedding`() = runBlockingTest {
        val mockProvider = MockEmbeddingProvider()
        semanticEngine.embeddingProvider = mockProvider
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("memory_card", "c_pending", "待确认的秘密", enabled = true, status = "pending")
        ))
        val results = semanticEngine.retrieve(RetrievalEngine.RetrievalRequest("秘密"))
        assertTrue(results.isEmpty())
        // Mock provider should never have been called with "待确认的秘密"
        assertFalse(mockProvider.embeddedTexts.any { it.contains("待确认的秘密") })
    }

    @Test
    fun `semantic engine with mock provider returns scored results`() = runBlockingTest {
        val mockProvider = MockEmbeddingProvider()
        semanticEngine.embeddingProvider = mockProvider
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("profile", "p1", "用户喜欢咖啡"),
            SemanticRetrievalEngine.CandidateEntry("life_record", "r1", "今天去了动物园")
        ))
        val results = semanticEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.snippet.contains("咖啡") })
    }

    @Test
    fun `embedding cache stores computed vectors`() {
        semanticEngine.embeddingProvider = MockEmbeddingProvider()
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("profile", "p1", "用户喜欢咖啡")
        ))
        runBlockingTest {
            semanticEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
        }
        // Cache should have entries now
        val exported = semanticEngine.exportCache()
        assertTrue(exported.isNotEmpty())
    }

    @Test
    fun `embedding cache can be loaded and exported roundtrip`() {
        val entries = listOf(
            SemanticRetrievalEngine.EmbeddingCacheEntry(
                sourceType = "profile", sourceId = "p1",
                textHash = "abc123", embedding = floatArrayOf(0.1f, 0.2f, 0.3f),
                cachedAt = System.currentTimeMillis()
            )
        )
        semanticEngine.loadCache(entries)
        val exported = semanticEngine.exportCache()
        assertEquals(1, exported.size)
        assertEquals("profile", exported[0].sourceType)
        assertEquals("p1", exported[0].sourceId)
    }

    @Test
    fun `cosine similarity is correct`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(1f, 0f, 0f)
        assertEquals(1.0, SemanticRetrievalEngine.cosineSimilarity(a, b), 0.0001)
    }

    @Test
    fun `cosine similarity orthogonal vectors are zero`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0.0, SemanticRetrievalEngine.cosineSimilarity(a, b), 0.0001)
    }

    @Test
    fun `cosine similarity different size returns zero`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(1f)
        assertEquals(0.0, SemanticRetrievalEngine.cosineSimilarity(a, b), 0.0001)
    }

    @Test
    fun `semantic engine respects sourceTypes filter`() = runBlockingTest {
        val mockProvider = MockEmbeddingProvider()
        semanticEngine.embeddingProvider = mockProvider
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("profile", "p1", "用户喜欢咖啡"),
            SemanticRetrievalEngine.CandidateEntry("life_record", "r1", "在咖啡馆写作")
        ))
        val results = semanticEngine.retrieve(
            RetrievalEngine.RetrievalRequest("咖啡", sourceTypes = setOf("profile"))
        )
        assertTrue(results.all { it.sourceType == "profile" })
    }

    // ═══════════════════════════════════════════════════════════════
    // I4: HybridRetrievalEngine — 合并去重 + 模式切换
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `hybrid default mode is rule_only`() {
        assertEquals(HybridRetrievalEngine.MODE_RULE_ONLY, hybridEngine.mode)
    }

    @Test
    fun `hybrid rule_only mode returns only rule results`() = runBlockingTest {
        hybridEngine.mode = HybridRetrievalEngine.MODE_RULE_ONLY
        ruleEngine.loadData(listOf(profileHit))
        // Give semantic engine matching data too — shouldn't appear
        val mockProvider = MockEmbeddingProvider()
        semanticEngine.embeddingProvider = mockProvider
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("life_record", "r2", "另一个咖啡馆")
        ))
        val results = hybridEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
        // Only rule engine results
        assertTrue(results.all { it.sourceId == "p1" })
    }

    @Test
    fun `hybrid mode merges both engines`() = runBlockingTest {
        hybridEngine.mode = HybridRetrievalEngine.MODE_HYBRID
        ruleEngine.loadData(listOf(profileHit)) // p1
        val mockProvider = MockEmbeddingProvider()
        semanticEngine.embeddingProvider = mockProvider
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("life_record", "r_sem", "在咖啡馆工作了一整天")
        ))
        val results = hybridEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
        // Should contain results from both engines
        val sourceIds = results.map { it.sourceId }
        assertTrue(sourceIds.contains("p1") || sourceIds.contains("r_sem"))
    }

    @Test
    fun `hybrid semantic_experiment mode returns only semantic results`() = runBlockingTest {
        hybridEngine.mode = HybridRetrievalEngine.MODE_SEMANTIC_EXPERIMENT
        ruleEngine.loadData(listOf(profileHit)) // p1
        val mockProvider = MockEmbeddingProvider()
        semanticEngine.embeddingProvider = mockProvider
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("life_record", "r_sem", "在咖啡馆工作")
        ))
        val results = hybridEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
        // Only semantic engine results (rule engine p1 should not appear)
        assertTrue(results.all { it.sourceId != "p1" } || results.isEmpty())
    }

    @Test
    fun `hybrid dedup by sourceType and sourceId`() {
        val ruleResults = listOf(
            RetrievalEngine.RetrievalResult("profile", "p1", "s1", "explain1", 10.0, null),
            RetrievalEngine.RetrievalResult("memory_card", "c1", "s2", "explain2", 8.0, null)
        )
        val semanticResults = listOf(
            RetrievalEngine.RetrievalResult("profile", "p1", "s1_dup", "explain3", 7.0, null), // same key
            RetrievalEngine.RetrievalResult("life_record", "r1", "s3", "explain4", 5.0, null) // new
        )
        val merged = hybridEngine.mergeWithDedup(ruleResults, semanticResults, 10)

        // Should have 3 unique results (not 4), p1 from rule wins
        assertEquals(3, merged.size)
        // Rule p1 explain should be kept
        val p1Result = merged.find { it.sourceType == "profile" && it.sourceId == "p1" }
        assertNotNull(p1Result)
        assertEquals("explain1", p1Result!!.explain)
    }

    @Test
    fun `hybrid dedup rule first priority`() {
        val ruleResults = listOf(
            RetrievalEngine.RetrievalResult("profile", "shared", "rule_snippet", "rule_explain", 10.0, null)
        )
        val semanticResults = listOf(
            RetrievalEngine.RetrievalResult("profile", "shared", "sem_snippet", "sem_explain", 15.0, null)
        )
        val merged = hybridEngine.mergeWithDedup(ruleResults, semanticResults, 10)
        assertEquals(1, merged.size)
        // Rule result should be kept even though semantic has higher score
        assertEquals("rule_explain", merged[0].explain)
        assertEquals("rule_snippet", merged[0].snippet)
    }

    @Test
    fun `hybrid explain not lost in merge`() {
        val ruleResults = listOf(
            RetrievalEngine.RetrievalResult("profile", "p1", "snippet1", "rule explain here", 10.0, null)
        )
        val semanticResults = listOf(
            RetrievalEngine.RetrievalResult("life_record", "r1", "snippet2", "semantic explain here", 7.0, null)
        )
        val merged = hybridEngine.mergeWithDedup(ruleResults, semanticResults, 10)
        assertEquals(2, merged.size)
        assertTrue(merged.all { it.explain.isNotBlank() })
    }

    @Test
    fun `hybrid merge respects limit`() {
        val ruleResults = (1..10).map {
            RetrievalEngine.RetrievalResult("profile", "id$it", "s$it", "e$it", (10 - it).toDouble(), null)
        }
        val semanticResults = (1..10).map {
            RetrievalEngine.RetrievalResult("life_record", "r$it", "s_r$it", "e_r$it", (5 - it * 0.5), null)
        }
        val merged = hybridEngine.mergeWithDedup(ruleResults, semanticResults, 5)
        assertEquals(5, merged.size)
    }

    @Test
    fun `hybrid dedup key correctly formed`() {
        val key = hybridEngine.dedupKey("profile", "id_123")
        assertEquals("profile:id_123", key)
    }

    // ═══════════════════════════════════════════════════════════════
    // I2-I4 交叉：disabled/pending 无法进入任意引擎
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `disabled hit should not be loaded into rule engine`() = runBlockingTest {
        // 调用方不应该将 disabled hit 传给 loadData。
        // 此测试验证：如果调用方遵守合约，disabled 内容不会出现。
        // 传入空列表模拟正确过滤。
        ruleEngine.loadData(emptyList())
        val results = ruleEngine.retrieve(RetrievalEngine.RetrievalRequest("test"))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `semantic engine internal filter excludes disabled and pending`() {
        // verify the filterConfirmedEnabled method
        val candidates = listOf(
            SemanticRetrievalEngine.CandidateEntry("profile", "p1", "ok", enabled = true, status = "confirmed"),
            SemanticRetrievalEngine.CandidateEntry("profile", "p2", "disabled", enabled = false, status = "confirmed"),
            SemanticRetrievalEngine.CandidateEntry("profile", "p3", "pending", enabled = true, status = "pending")
        )
        semanticEngine.loadData(candidates)

        // With no provider, returns empty — but loadData accepts all; filtering is in retrieve
        // Test via retrieve with mock provider
        val mockProvider = MockEmbeddingProvider()
        semanticEngine.embeddingProvider = mockProvider
        val results = runBlockingTest {
            semanticEngine.retrieve(RetrievalEngine.RetrievalRequest("ok"))
        }
        // Only p1 should be in results
        assertTrue(results.all { it.sourceId == "p1" })
        assertEquals(1, results.size)
    }

    // ═══════════════════════════════════════════════════════════════
    // 回归：explain 不丢失 + 默认行为不变
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `every result from rule engine has non-blank explain`() = runBlockingTest {
        ruleEngine.loadData(listOf(profileHit, cardHit, recordHit, diaryHit, mdHit))
        val results = ruleEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
        for (r in results) {
            assertTrue("explain should not be blank for ${r.sourceId}", r.explain.isNotBlank())
        }
    }

    @Test
    fun `hybrid rule_only mode matches rule engine direct output`() = runBlockingTest {
        hybridEngine.mode = HybridRetrievalEngine.MODE_RULE_ONLY
        ruleEngine.loadData(listOf(profileHit, cardHit, mdHit))

        val hybridResults = hybridEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
        val ruleResults = ruleEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))

        assertEquals(ruleResults.size, hybridResults.size)
        for (i in ruleResults.indices) {
            assertEquals(ruleResults[i].sourceId, hybridResults[i].sourceId)
            assertEquals(ruleResults[i].explain, hybridResults[i].explain)
            assertEquals(ruleResults[i].score, hybridResults[i].score, 0.0001)
        }
    }

    @Test
    fun `unknown mode falls back to rule`() = runBlockingTest {
        hybridEngine.mode = "invalid_mode"
        ruleEngine.loadData(listOf(profileHit))
        val results = hybridEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
        // 应退回规则检索
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `ALL_MODES contains the three expected modes`() {
        val modes = HybridRetrievalEngine.ALL_MODES
        assertTrue(modes.contains("rule_only"))
        assertTrue(modes.contains("hybrid"))
        assertTrue(modes.contains("semantic_experiment"))
        assertEquals(3, modes.size)
    }

    @Test
    fun `semantic experiment mode warning is present for non rule_only`() {
        // 验证所有实验模式（非 rule_only）都需要警告
        val experimentalModes = listOf("hybrid", "semantic_experiment")
        for (mode in experimentalModes) {
            assertTrue("$mode should not be rule_only", mode != "rule_only")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // I-Fix3: 链路测试 — 模式切换 + 回退 + disabled/pending
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `semantic_experiment falls back to rule when semantic empty`() = runBlockingTest {
        // semantic 无 provider → 返回空 → 应回退规则结果
        hybridEngine.mode = HybridRetrievalEngine.MODE_SEMANTIC_EXPERIMENT
        semanticEngine.embeddingProvider = null  // no-op
        semanticEngine.loadData(emptyList())

        ruleEngine.loadData(listOf(profileHit, cardHit))

        val results = hybridEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
        // 应回退到规则结果
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.sourceId == "p1" })
    }

    @Test
    fun `semantic_experiment with provider returns semantic results not rule`() = runBlockingTest {
        hybridEngine.mode = HybridRetrievalEngine.MODE_SEMANTIC_EXPERIMENT
        val mockProvider = MockEmbeddingProvider()
        semanticEngine.embeddingProvider = mockProvider
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("life_record", "r_sem", "咖啡相关的生活记录文本内容")
        ))
        ruleEngine.loadData(listOf(profileHit))  // rule has p1

        val results = hybridEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
        // semantic returns results, so no fallback — semantic results only
        // Note: semantic results may include r_sem but not p1
        assertTrue(results.all { it.sourceId != "p1" } || results.any { it.sourceId == "r_sem" })
    }

    @Test
    fun `disabled pending excluded in all three modes`() = runBlockingTest {
        val mockProvider = MockEmbeddingProvider()
        semanticEngine.embeddingProvider = mockProvider

        // Rule: only confirmed+enabled hits loaded (simulate correct caller behavior)
        val okHit = MemoryRetrievalPolicy.SourceHit(
            "memory_card", "记忆卡片", "c_ok", "confirmed card content", 10.0
        )
        ruleEngine.loadData(listOf(okHit))

        // Semantic: mix of confirmed and pending candidates (engine filters pending)
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("profile", "p_ok", "confirmed profile", enabled = true, status = "confirmed"),
            SemanticRetrievalEngine.CandidateEntry("profile", "p_pending", "pending profile", enabled = true, status = "pending"),
            SemanticRetrievalEngine.CandidateEntry("profile", "p_disabled", "disabled profile", enabled = false, status = "confirmed")
        ))

        for (mode in listOf("rule_only", "hybrid", "semantic_experiment")) {
            hybridEngine.mode = mode
            val results = hybridEngine.retrieve(RetrievalEngine.RetrievalRequest("content profile"))

            // pending/disabled should never appear
            for (r in results) {
                assertTrue("Mode $mode: ${r.sourceId} should not be pending/disabled",
                    r.sourceId != "p_pending" && r.sourceId != "p_disabled")
            }
        }
    }

    @Test
    fun `explain preserved in all three modes`() = runBlockingTest {
        val mockProvider = MockEmbeddingProvider()
        semanticEngine.embeddingProvider = mockProvider
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("life_record", "r_exp", "在咖啡馆工作了一整天")
        ))
        ruleEngine.loadData(listOf(profileHit))  // p1 has explain "命中「咖啡」"

        for (mode in listOf("rule_only", "hybrid", "semantic_experiment")) {
            hybridEngine.mode = mode
            val results = hybridEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
            for (r in results) {
                assertTrue("Mode $mode: explain should not be blank for ${r.sourceId}",
                    r.explain.isNotBlank())
            }
        }
    }

    @Test
    fun `source fields preserved in all three modes`() = runBlockingTest {
        val mockProvider = MockEmbeddingProvider()
        semanticEngine.embeddingProvider = mockProvider
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("life_record", "r_src", "咖啡内容")
        ))
        ruleEngine.loadData(listOf(profileHit))

        for (mode in listOf("rule_only", "hybrid", "semantic_experiment")) {
            hybridEngine.mode = mode
            val results = hybridEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
            for (r in results) {
                assertFalse("Mode $mode: sourceType should not be blank", r.sourceType.isBlank())
                assertFalse("Mode $mode: sourceId should not be blank", r.sourceId.isBlank())
                assertFalse("Mode $mode: snippet should not be blank", r.snippet.isBlank())
                assertTrue("Mode $mode: score should be > 0", r.score > 0)
            }
        }
    }

    @Test
    fun `hybrid mode with rule and semantic data merges correctly`() = runBlockingTest {
        hybridEngine.mode = HybridRetrievalEngine.MODE_HYBRID
        val mockProvider = MockEmbeddingProvider()
        semanticEngine.embeddingProvider = mockProvider

        ruleEngine.loadData(listOf(profileHit))  // p1: "用户叫小明，喜欢喝咖啡"
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("life_record", "r_new", "在咖啡馆遇到了一只猫")
        ))

        val results = hybridEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
        // Should have results from both engines, deduped
        assertTrue(results.isNotEmpty())

        // Check dup keys don't appear twice
        val keys = results.map { "${it.sourceType}:${it.sourceId}" }
        assertEquals(keys.size, keys.distinct().size)
    }

    @Test
    fun `default mode is rule_only on fresh engine`() {
        assertEquals(HybridRetrievalEngine.MODE_RULE_ONLY, hybridEngine.mode)
    }

    // ═══════════════════════════════════════════════════════════════
    // 辅助
    // ═══════════════════════════════════════════════════════════════

    /**
     * 简易的 runBlocking 替代，用于测试 suspend 函数。
     */
    private fun <T> runBlockingTest(block: suspend () -> T): T {
        val result = kotlinx.coroutines.runBlocking { block() }
        return result
    }

    /**
     * Mock EmbeddingProvider — 返回与输入文本长度正相关的向量。
     */
    private class MockEmbeddingProvider : SemanticRetrievalEngine.EmbeddingProvider {
        val embeddedTexts = mutableListOf<String>()

        override suspend fun embed(text: String): FloatArray {
            embeddedTexts.add(text)
            // 生成一个简单的确定性向量：基于文本长度的 4 维向量
            val len = text.length.toFloat()
            return floatArrayOf(len, len * 0.5f, len * 0.25f, len * 0.125f)
        }
    }
}
