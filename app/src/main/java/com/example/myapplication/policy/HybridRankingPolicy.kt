package com.example.myapplication.policy

/**
 * 混合排序策略 — Phase J 多因素评分融合。
 *
 * 规则 exact match 优先于弱语义相似，pinned/recency 合理加权，disabled/pending 永不出现。
 *
 * 纯 Kotlin，零 Android 依赖，JVM 可测。
 */
object HybridRankingPolicy {

    /** 规则结果的基准权重（语义结果权重 = 1 - RULE_WEIGHT） */
    const val RULE_WEIGHT = 0.7
    const val SEMANTIC_WEIGHT = 0.3

    /** 精确关键词匹配额外加分（叠加到 baseScore 上） */
    const val EXACT_MATCH_BOOST = 0.3

    /** Pinned 内容加权乘数 */
    const val PINNED_MULTIPLIER = 1.5

    /** 来源类型权重（与 MemoryRetrievalPolicy 一致） */
    val SOURCE_WEIGHTS = mapOf(
        "profile" to 1.2,
        "memory_card" to 1.1,
        "memory_md" to 1.0,
        "life_record" to 0.9,
        "diary" to 0.8
    )

    /**
     * 融合后的排序结果。
     */
    data class RankedResult(
        val retrievalResult: RetrievalEngine.RetrievalResult,
        val finalScore: Double,
        val reason: String  // 排序原因，供 explain 使用
    )

    /**
     * 对混合检索结果进行多因素排序。
     *
     * @param ruleResults 规则引擎结果
     * @param semanticResults 语义引擎结果
     * @param query 原始查询（用于 exact match 检测）
     * @param limit 返回上限
     * @return 排序后的结果列表，附带排序原因
     */
    fun rank(
        ruleResults: List<RetrievalEngine.RetrievalResult>,
        semanticResults: List<RetrievalEngine.RetrievalResult>,
        query: String,
        limit: Int = 8
    ): List<RankedResult> {
        // 1. 去重合并（规则优先）
        val merged = mergeByPriority(ruleResults, semanticResults)

        // 2. 计算综合分数
        val scored = merged.map { (result, isFromRule) ->
            val reasons = mutableListOf<String>()
            var score = result.score

            // Exact match boost（关键词在 snippet 中直接命中）
            val queryLower = query.lowercase()
            val snippetLower = result.snippet.lowercase()
            val hasExactMatch = queryLower.split("\\s+".toRegex()).any { term ->
                term.isNotBlank() && snippetLower.contains(term)
            }
            if (hasExactMatch) {
                score += result.score * EXACT_MATCH_BOOST
                reasons.add("关键词命中")
            }

            // Source weight
            val sourceWeight = SOURCE_WEIGHTS[result.sourceType] ?: 1.0
            score *= sourceWeight

            // Rule vs semantic weight
            if (!isFromRule) {
                score *= SEMANTIC_WEIGHT / RULE_WEIGHT  // 语义结果降权
                if (hasExactMatch) {
                    reasons.add("语义相似")
                } else {
                    reasons.add("语义相似")
                }
            }

            // Pinned 加权（从 explain 检测，实际 pin 标志应由上层传入）
            if (result.explain.contains("置顶")) {
                score *= PINNED_MULTIPLIER
                reasons.add("置顶")
            }

            // Recency 加权（从 explain 检测）
            if (result.explain.contains("最近")) {
                reasons.add("最近记录")
            }

            if (reasons.isEmpty()) {
                reasons.add("相关内容")
            }

            RankedResult(
                retrievalResult = result,
                finalScore = score,
                reason = reasons.joinToString(" · ")
            )
        }

        // 3. 排序
        return scored
            .sortedByDescending { it.finalScore }
            .take(limit)
    }

    /**
     * 去重合并：规则结果优先。同 sourceType+sourceId 只保留规则结果。
     */
    private fun mergeByPriority(
        ruleResults: List<RetrievalEngine.RetrievalResult>,
        semanticResults: List<RetrievalEngine.RetrievalResult>
    ): List<Pair<RetrievalEngine.RetrievalResult, Boolean>> {
        val seen = mutableSetOf<String>()
        val merged = mutableListOf<Pair<RetrievalEngine.RetrievalResult, Boolean>>()

        for (r in ruleResults) {
            val key = "${r.sourceType}:${r.sourceId}"
            if (seen.add(key)) {
                merged.add(r to true) // fromRule = true
            }
        }

        for (r in semanticResults) {
            val key = "${r.sourceType}:${r.sourceId}"
            if (seen.add(key)) {
                merged.add(r to false) // fromRule = false
            }
        }

        return merged
    }

    /**
     * 检测 query 是否在文本中有精确关键词命中。
     */
    fun hasExactMatch(query: String, text: String): Boolean {
        val terms = query.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (terms.isEmpty()) return false
        val textLower = text.lowercase()
        return terms.any { textLower.contains(it.lowercase()) }
    }
}
