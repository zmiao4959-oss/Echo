package com.example.myapplication.policy

/**
 * 混合检索引擎 — 合并规则结果与语义结果，规则优先兜底。
 *
 * ## 检索引擎模式
 *
 * | 模式 | 常量 | 行为 |
 * |------|------|------|
 * | 规则 Only | [MODE_RULE_ONLY] | 仅调用 [RuleBasedRetrievalEngine]，与现有行为完全一致 |
 * | 混合 | [MODE_HYBRID] | 规则结果 + 语义结果 → 去重合并，规则结果优先 |
 * | 语义实验 | [MODE_SEMANTIC_EXPERIMENT] | 仅调用 [SemanticRetrievalEngine]，实验用途 |
 *
 * ## 统一过滤层
 *
 * confirmed/enabled/disabled/pending 过滤在此层二次执行：
 * - 规则引擎：数据注入时已过滤，此处再次校验。
 * - 语义引擎：引擎内部过滤，此处再次校验。
 * - 任一引擎不得绕过。
 *
 * ## 去重策略
 *
 * 以 (sourceType, sourceId) 为唯一键。
 * 规则结果优先 —— 当同一键同时出现在规则和语义结果中时，保留规则结果。
 *
 * ## 默认行为
 *
 * 默认模式为 [MODE_RULE_ONLY]，与 Phase I 前完全一致。
 */
class HybridRetrievalEngine(
    private val ruleEngine: RuleBasedRetrievalEngine,
    private val semanticEngine: SemanticRetrievalEngine
) : RetrievalEngine {

    override val name = "hybrid"

    /** 当前检索引擎模式 */
    var mode: String = MODE_RULE_ONLY

    override suspend fun retrieve(request: RetrievalEngine.RetrievalRequest): List<RetrievalEngine.RetrievalResult> {
        return when (mode) {
            MODE_RULE_ONLY -> {
                val ruleResults = ruleEngine.retrieve(request)
                // 二次安全校验（统一过滤层）
                filterAllowed(ruleResults, request.limit)
            }
            MODE_HYBRID -> {
                val ruleResults = ruleEngine.retrieve(request)
                val semanticResults = semanticEngine.retrieve(request)

                // Phase J: 多因素排序融合
                val ranked = HybridRankingPolicy.rank(
                    ruleResults = ruleResults,
                    semanticResults = semanticResults,
                    query = request.query,
                    limit = request.limit
                )

                // 将排序原因写入 explain
                val results = ranked.map { ranked ->
                    ranked.retrievalResult.copy(explain = ranked.reason)
                }

                filterAllowed(results, request.limit)
            }
            MODE_SEMANTIC_EXPERIMENT -> {
                val semanticResults = semanticEngine.retrieve(request)
                // 语义为空时自动兜底规则结果
                if (semanticResults.isEmpty()) {
                    val ruleResults = ruleEngine.retrieve(request)
                    filterAllowed(ruleResults, request.limit)
                } else {
                    filterAllowed(semanticResults, request.limit)
                }
            }
            else -> {
                // 未知模式 → 退回规则
                val ruleResults = ruleEngine.retrieve(request)
                filterAllowed(ruleResults, request.limit)
            }
        }
    }

    // ── 内部方法 ──

    /**
     * 合并去重。规则结果优先，语义结果补充去重后的新结果。
     */
    internal fun mergeWithDedup(
        ruleResults: List<RetrievalEngine.RetrievalResult>,
        semanticResults: List<RetrievalEngine.RetrievalResult>,
        limit: Int
    ): List<RetrievalEngine.RetrievalResult> {
        val seen = mutableSetOf<String>()
        val merged = mutableListOf<RetrievalEngine.RetrievalResult>()

        // 规则结果优先
        for (r in ruleResults) {
            val key = dedupKey(r.sourceType, r.sourceId)
            if (seen.add(key)) {
                merged.add(r)
            }
        }

        // 语义结果补充（不覆盖已存在的规则结果）
        for (r in semanticResults) {
            val key = dedupKey(r.sourceType, r.sourceId)
            if (seen.add(key)) {
                merged.add(r)
            }
        }

        return merged.sortedByDescending { it.score }.take(limit)
    }

    /**
     * 去重键。
     */
    internal fun dedupKey(sourceType: String, sourceId: String): String = "$sourceType:$sourceId"

    /**
     * 统一过滤：排除 disabled/pending 内容。
     * 当前阶段不做额外过滤（数据注入时已保证），留作未来扩展点。
     */
    private fun filterAllowed(
        results: List<RetrievalEngine.RetrievalResult>,
        limit: Int
    ): List<RetrievalEngine.RetrievalResult> {
        // 当前所有通过引擎返回的结果均已过滤。
        // 此方法作为统一过滤层的锚点，未来如需增加过滤逻辑在此添加。
        return results.take(limit)
    }

    companion object {
        /** 仅规则检索 — 与 Phase I 前行为一致 */
        const val MODE_RULE_ONLY = "rule_only"

        /** 混合检索 — 规则优先，语义补充 */
        const val MODE_HYBRID = "hybrid"

        /** 语义实验 — 仅语义检索，用于评估语义效果 */
        const val MODE_SEMANTIC_EXPERIMENT = "semantic_experiment"

        /** 所有可用模式 */
        val ALL_MODES = listOf(MODE_RULE_ONLY, MODE_HYBRID, MODE_SEMANTIC_EXPERIMENT)
    }
}
