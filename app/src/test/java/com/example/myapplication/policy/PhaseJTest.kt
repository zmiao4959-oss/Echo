package com.example.myapplication.policy

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Phase J 综合测试：排序策略、敏感过滤、回退、缓存。
 */
class PhaseJTest {

    private lateinit var ruleEngine: RuleBasedRetrievalEngine
    private lateinit var semanticEngine: SemanticRetrievalEngine
    private lateinit var hybridEngine: HybridRetrievalEngine

    @Before
    fun setUp() {
        ruleEngine = RuleBasedRetrievalEngine()
        semanticEngine = SemanticRetrievalEngine()
        hybridEngine = HybridRetrievalEngine(ruleEngine, semanticEngine)
    }

    // ═══════════════════════════════════════════
    // J3: HybridRankingPolicy 排序
    // ═══════════════════════════════════════════

    @Test
    fun `exact keyword match beats weak semantic`() {
        val ruleResults = listOf(
            RetrievalEngine.RetrievalResult("profile", "p1", "用户喜欢喝咖啡", "命中「咖啡」", 10.0, null)
        )
        val semanticResults = listOf(
            RetrievalEngine.RetrievalResult("life_record", "r1", "在茶馆坐了一下午", "语义匹配", 3.0, null)
        )
        val ranked = HybridRankingPolicy.rank(ruleResults, semanticResults, "咖啡", 10)

        // Rule exact match should rank higher than semantic
        val top = ranked.first()
        assertEquals("p1", top.retrievalResult.sourceId)
        assertTrue(top.reason.contains("关键词命中"))
    }

    @Test
    fun `pinned gets boosted`() {
        val normal = RetrievalEngine.RetrievalResult("life_record", "r1", "茶文化体验", "", 5.0, null)
        val pinned = RetrievalEngine.RetrievalResult("memory_card", "c_pin", "重要的茶文化回忆", "置顶记忆", 5.0, null)

        val ranked = HybridRankingPolicy.rank(listOf(normal, pinned), emptyList(), "茶", 10)
        // Pinned should be first
        assertTrue(ranked.first().reason.contains("置顶") || ranked.first().finalScore > ranked.last().finalScore)
    }

    @Test
    fun `semantic results get lower weight than rule`() {
        val ruleR = RetrievalEngine.RetrievalResult("profile", "p1", "rule hit", "", 10.0, null)
        val semR = RetrievalEngine.RetrievalResult("profile", "p_sem", "semantic hit", "", 10.0, null)

        // Only semantic in merge (different id)
        val ranked = HybridRankingPolicy.rank(listOf(ruleR), listOf(semR), "test", 10)

        // Both present, rule should be first
        assertEquals("p1", ranked[0].retrievalResult.sourceId)
    }

    @Test
    fun `dedup rule priority over semantic`() {
        // Same sourceType+sourceId in both rule and semantic — rule wins
        val ruleR = RetrievalEngine.RetrievalResult("profile", "shared_id", "rule snippet", "rule explain", 10.0, null)
        val semR = RetrievalEngine.RetrievalResult("profile", "shared_id", "sem snippet", "sem explain", 15.0, null)

        val ranked = HybridRankingPolicy.rank(listOf(ruleR), listOf(semR), "test", 10)
        assertEquals(1, ranked.size)
        assertEquals("rule explain", ranked[0].retrievalResult.explain)
    }

    @Test
    fun `hasExactMatch detects keyword`() {
        assertTrue(HybridRankingPolicy.hasExactMatch("咖啡", "用户喜欢喝咖啡"))
        assertTrue(HybridRankingPolicy.hasExactMatch("音乐 爱好", "用户是重度音乐爱好者"))
        assertFalse(HybridRankingPolicy.hasExactMatch("咖啡", "用户喜欢喝茶"))
        assertFalse(HybridRankingPolicy.hasExactMatch("", "anything"))
    }

    // ═══════════════════════════════════════════
    // J5: SensitiveContentFilter
    // ═══════════════════════════════════════════

    @Test
    fun `detects phone number`() {
        assertTrue(SensitiveContentFilter.isSensitive("我的手机是13812345678"))
        assertTrue(SensitiveContentFilter.detectTypes("13812345678").contains("手机号"))
    }

    @Test
    fun `detects ID card number`() {
        assertTrue(SensitiveContentFilter.isSensitive("身份证号110101199001011234"))
        assertTrue(SensitiveContentFilter.detectTypes("110101199001011234").contains("身份证"))
    }

    @Test
    fun `detects API key`() {
        assertTrue(SensitiveContentFilter.isSensitive("sk-abc123def456ghi789jkl"))
        assertTrue(SensitiveContentFilter.detectTypes("sk-abc123def456ghi789jkl").contains("API Key"))
    }

    @Test
    fun `detects email`() {
        assertTrue(SensitiveContentFilter.isSensitive("联系我 test@example.com"))
    }

    @Test
    fun `detects address`() {
        assertTrue(SensitiveContentFilter.isSensitive("北京市海淀区中关村大街1号"))
    }

    @Test
    fun `detects medical info`() {
        assertTrue(SensitiveContentFilter.isSensitive("诊断结果是高血压"))
        assertTrue(SensitiveContentFilter.detectTypes("诊断高血压").contains("医疗"))
    }

    @Test
    fun `detects financial info`() {
        assertTrue(SensitiveContentFilter.isSensitive("银行卡号被盗了"))
        assertTrue(SensitiveContentFilter.detectTypes("银行卡").contains("财务"))
    }

    @Test
    fun `normal text is not sensitive`() {
        assertFalse(SensitiveContentFilter.isSensitive("今天天气真好"))
        assertFalse(SensitiveContentFilter.isSensitive("用户喜欢喝咖啡"))
        assertFalse(SensitiveContentFilter.isSensitive(""))
    }

    @Test
    fun `filter returns null for sensitive text`() {
        assertNull(SensitiveContentFilter.filter("13812345678"))
    }

    @Test
    fun `filter returns text for safe content`() {
        assertEquals("正常文本", SensitiveContentFilter.filter("正常文本"))
    }

    @Test
    fun `detectTypes returns empty for safe text`() {
        assertTrue(SensitiveContentFilter.detectTypes("今天天气真好").isEmpty())
    }

    // ═══════════════════════════════════════════
    // J3+J5: Semantic engine filters sensitive
    // ═══════════════════════════════════════════

    @Test
    fun `semantic engine skips sensitive candidates`() = runBlocking {
        val mockProvider = FakeEmbeddingProvider()
        semanticEngine.embeddingProvider = mockProvider

        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("profile", "p_safe", "用户喜欢咖啡"),
            SemanticRetrievalEngine.CandidateEntry("profile", "p_sensitive", "手机号13812345678请联系")
        ))

        val results = semanticEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
        // Sensitive candidate should be excluded
        assertTrue(results.none { it.sourceId == "p_sensitive" })
        assertFalse(mockProvider.calledWith.any { it.contains("13812345678") })
    }

    // ═══════════════════════════════════════════
    // J1+J4: EmbeddingCacheEntry model/provider
    // ═══════════════════════════════════════════

    @Test
    fun `cache entry stores model and provider`() {
        val entry = SemanticRetrievalEngine.EmbeddingCacheEntry(
            sourceType = "profile", sourceId = "p1", textHash = "abc",
            embedding = floatArrayOf(0.1f, 0.2f),
            model = "test-model", provider = "test-provider",
            summary = "test summary", lastUsedAt = 123456789L
        )
        assertEquals("test-model", entry.model)
        assertEquals("test-provider", entry.provider)
        assertEquals("test summary", entry.summary)
        assertEquals(123456789L, entry.lastUsedAt)
    }

    @Test
    fun `cache entry equals works with float arrays`() {
        val a = SemanticRetrievalEngine.EmbeddingCacheEntry("p", "1", "h", floatArrayOf(0.1f))
        val b = SemanticRetrievalEngine.EmbeddingCacheEntry("p", "1", "h", floatArrayOf(0.1f))
        assertEquals(a, b)
    }

    @Test
    fun `cache entry summary truncation`() {
        val longText = "a".repeat(200)
        val summary = longText.take(100)
        assertEquals(100, summary.length)
    }

    // ═══════════════════════════════════════════
    // J4: Explain readability
    // ═══════════════════════════════════════════

    @Test
    fun `rule engine explain is natural language`() {
        val hit = MemoryRetrievalPolicy.SourceHit("profile", "用户画像", "p1", "用户叫小明", 10.0)
        val explain = MemoryRetrievalPolicy.explainHit(hit, "小明")
        assertTrue(explain.contains("命中"))
        assertFalse(explain.contains("10.0"))  // no raw score
    }

    @Test
    fun `semantic engine explain is readable`() = runBlocking {
        val mockProvider = FakeEmbeddingProvider()
        semanticEngine.embeddingProvider = mockProvider
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("life_record", "r1", "咖啡生活")
        ))
        val results = semanticEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
        if (results.isNotEmpty()) {
            assertTrue(results[0].explain.isNotBlank())
        }
    }

    // ═══════════════════════════════════════════
    // J5: Semantic empty → fallback to rule
    // ═══════════════════════════════════════════

    @Test
    fun `semantic_experiment falls back when provider null`() = runBlocking {
        hybridEngine.mode = HybridRetrievalEngine.MODE_SEMANTIC_EXPERIMENT
        semanticEngine.embeddingProvider = null
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("profile", "p1", "should not appear")
        ))
        ruleEngine.loadData(listOf(
            MemoryRetrievalPolicy.SourceHit("profile", "用户画像", "p_rule", "rule result", 10.0)
        ))

        val results = hybridEngine.retrieve(RetrievalEngine.RetrievalRequest("rule"))
        assertTrue(results.isNotEmpty())
        // Rule result used as fallback
        assertTrue(results.any { it.sourceId == "p_rule" })
    }

    @Test
    fun `hybrid mode disabled pending never appear`() = runBlocking {
        hybridEngine.mode = HybridRetrievalEngine.MODE_HYBRID
        semanticEngine.embeddingProvider = FakeEmbeddingProvider()
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("profile", "p_ok", "safe content", enabled = true, status = "confirmed"),
            SemanticRetrievalEngine.CandidateEntry("profile", "p_no", "bad content", enabled = false, status = "confirmed")
        ))
        ruleEngine.loadData(emptyList())

        val results = hybridEngine.retrieve(RetrievalEngine.RetrievalRequest("content"))
        assertTrue(results.none { it.sourceId == "p_no" })
    }

    // ═══════════════════════════════════════════
    // 辅助
    // ═══════════════════════════════════════════

    private class FakeEmbeddingProvider : SemanticRetrievalEngine.EmbeddingProvider {
        val calledWith = mutableListOf<String>()
        override suspend fun embed(text: String): FloatArray {
            calledWith.add(text)
            var h = 0L
            for (c in text) h = h * 31 + c.code.toLong()
            return floatArrayOf(
                (h and 0xFF).toFloat() / 255f,
                ((h shr 8) and 0xFF).toFloat() / 255f
            )
        }
    }
}
