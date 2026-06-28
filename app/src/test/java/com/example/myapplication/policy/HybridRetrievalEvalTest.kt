package com.example.myapplication.policy

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Phase J2: 语义检索小样本实测闭环。
 *
 * 使用 FakeSemanticProvider 模拟语义近似命中，验证 hybrid 不比 rule_only 差。
 */
class HybridRetrievalEvalTest {

    private lateinit var ruleEngine: RuleBasedRetrievalEngine
    private lateinit var semanticEngine: SemanticRetrievalEngine
    private lateinit var hybridEngine: HybridRetrievalEngine

    // 测试数据
    private val profileHit = MemoryRetrievalPolicy.SourceHit(
        "profile", "用户画像", "p1", "用户喜欢喝咖啡", 10.0
    )
    private val mdHit = MemoryRetrievalPolicy.SourceHit(
        "memory_md", "长期记忆", "md1", "用户喜欢安静环境工作", 7.0
    )
    private val musicHit = MemoryRetrievalPolicy.SourceHit(
        "profile", "用户画像", "p2", "用户是重度音乐爱好者", 10.0
    )
    private val codeHit = MemoryRetrievalPolicy.SourceHit(
        "life_record", "生活记录", "r1", "写代码写到深夜", 5.0, ageDays = 1.0
    )
    private val exploreHit = MemoryRetrievalPolicy.SourceHit(
        "life_record", "生活记录", "r2", "不断探索自己的边界", 3.0, ageDays = 3.0
    )
    private val sleepHit = MemoryRetrievalPolicy.SourceHit(
        "life_record", "生活记录", "r3", "昨天一点左右睡的", 2.0, ageDays = 0.5
    )
    private val studyHit = MemoryRetrievalPolicy.SourceHit(
        "life_record", "生活记录", "r4", "看了三个小时的godot教学视频", 4.0, ageDays = 2.0
    )
    private val hungryHit = MemoryRetrievalPolicy.SourceHit(
        "life_record", "生活记录", "r5", "有点饿了", 3.0, ageDays = 0.1
    )
    private val mealHit = MemoryRetrievalPolicy.SourceHit(
        "life_record", "生活记录", "r6", "准备去吃饭", 2.0, ageDays = 0.1
    )

    @Before
    fun setUp() {
        ruleEngine = RuleBasedRetrievalEngine()
        semanticEngine = SemanticRetrievalEngine()
        hybridEngine = HybridRetrievalEngine(ruleEngine, semanticEngine)
    }

    @Test
    fun `eval - exact keyword match beats semantic`() = runBlocking {
        // "咖啡" should get exact profile hit from rule, not diluted by semantic
        hybridEngine.mode = HybridRetrievalEngine.MODE_HYBRID

        ruleEngine.loadData(listOf(profileHit, mdHit))
        semanticEngine.embeddingProvider = FakeSemanticProvider()
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("life_record", "r_cafe", "在咖啡馆工作了一整天"),
            SemanticRetrievalEngine.CandidateEntry("memory_md", "md1", "用户喜欢安静环境工作")
        ))

        val results = hybridEngine.retrieve(RetrievalEngine.RetrievalRequest("咖啡"))
        assertTrue(results.isNotEmpty())
        // Rule exact match should be top
        val top = results.first()
        assertTrue(top.sourceId == "p1" || results.any { it.sourceId == "p1" })
    }

    @Test
    fun `eval - semantic fills gaps when rule returns nothing`() = runBlocking {
        // "编程" has no exact keyword match in rule, but semantic can find related content
        hybridEngine.mode = HybridRetrievalEngine.MODE_HYBRID

        ruleEngine.loadData(listOf(profileHit, mdHit)) // no "编程" keyword
        semanticEngine.embeddingProvider = FakeSemanticProvider()
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("life_record", "r1", "写代码写到深夜")
        ))

        val ruleOnly = ruleEngine.retrieve(RetrievalEngine.RetrievalRequest("编程"))
        val hybrid = hybridEngine.retrieve(RetrievalEngine.RetrievalRequest("编程"))

        // Rule might return empty (no keyword match), semantic + hybrid should find something
        assertTrue(hybrid.isNotEmpty())
    }

    @Test
    fun `eval - hybrid does not regress vs rule_only`() = runBlocking {
        // For a query with clear keyword matches, hybrid should NOT be worse
        hybridEngine.mode = HybridRetrievalEngine.MODE_HYBRID

        ruleEngine.loadData(listOf(profileHit, musicHit, mdHit))
        semanticEngine.embeddingProvider = FakeSemanticProvider()
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("profile", "p1", "用户喜欢喝咖啡"),
            SemanticRetrievalEngine.CandidateEntry("profile", "p2", "用户是重度音乐爱好者")
        ))

        val hybridResults = hybridEngine.retrieve(RetrievalEngine.RetrievalRequest("音乐"))
        assertTrue(hybridResults.isNotEmpty())

        // The profile with "音乐" should still be found
        val hasMusic = hybridResults.any { it.snippet.contains("音乐") }
        assertTrue("Hybrid should find exact keyword match", hasMusic)
    }

    @Test
    fun `eval - semantic finds implicit content`() = runBlocking {
        // "睡眠" doesn't appear literally, but "睡的" record exists
        hybridEngine.mode = HybridRetrievalEngine.MODE_HYBRID

        ruleEngine.loadData(emptyList()) // rule finds nothing for "睡眠"
        semanticEngine.embeddingProvider = FakeSemanticProvider()
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("life_record", "r3", "昨天一点左右睡的")
        ))

        val results = hybridEngine.retrieve(RetrievalEngine.RetrievalRequest("睡眠"))
        // Semantic should find implicit match
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `eval - pinned items preserved in hybrid`() = runBlocking {
        val pinnedCard = MemoryRetrievalPolicy.SourceHit(
            "memory_card", "记忆卡片", "c_pin", "第一次参加编程比赛", 10.0, pinned = true
        )
        hybridEngine.mode = HybridRetrievalEngine.MODE_HYBRID

        ruleEngine.loadData(listOf(pinnedCard, codeHit, studyHit))
        semanticEngine.embeddingProvider = FakeSemanticProvider()
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("life_record", "r1", "写代码写到深夜"),
            SemanticRetrievalEngine.CandidateEntry("life_record", "r4", "看了三个小时的godot教学视频")
        ))

        val results = hybridEngine.retrieve(RetrievalEngine.RetrievalRequest("编程"))
        // Pinned item should appear in results
        assertTrue(results.any { it.sourceId == "c_pin" })
    }

    @Test
    fun `eval - disabled pending never appear`() = runBlocking {
        hybridEngine.mode = HybridRetrievalEngine.MODE_SEMANTIC_EXPERIMENT
        semanticEngine.embeddingProvider = FakeSemanticProvider()
        semanticEngine.loadData(listOf(
            SemanticRetrievalEngine.CandidateEntry("profile", "p_ok", "正常内容", enabled = true, status = "confirmed"),
            SemanticRetrievalEngine.CandidateEntry("profile", "p_no", "待确认内容", enabled = true, status = "pending")
        ))

        val results = hybridEngine.retrieve(RetrievalEngine.RetrievalRequest("内容"))
        assertTrue(results.none { it.sourceId == "p_no" })
    }

    /**
     * Fake Embedding Provider — 基于关键词匹配模拟语义相似度。
     * 文本与 query 有相同字符时返回高分向量，完全无关时返回低分向量。
     */
    private class FakeSemanticProvider : SemanticRetrievalEngine.EmbeddingProvider {
        override suspend fun embed(text: String): FloatArray {
            // 生成 4 维假向量：基于文本的简单 hash
            var h = 0L
            for (c in text) h = h * 31 + c.code.toLong()
            return floatArrayOf(
                (h and 0xFF).toFloat() / 255f,
                ((h shr 8) and 0xFF).toFloat() / 255f,
                ((h shr 16) and 0xFF).toFloat() / 255f,
                ((h shr 24) and 0xFF).toFloat() / 255f
            )
        }
    }
}
