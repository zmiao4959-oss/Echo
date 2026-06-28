package com.example.myapplication.policy

/**
 * 规则检索引擎 — 包装 MemoryRetrievalPolicy + SearchIndex 的原有逻辑。
 *
 * 纯 Kotlin，零 Android 依赖，JVM 可测。
 *
 * 使用方式：
 * - 生产：Android 层通过 [loadData] 注入 MemoryRetrievalPolicy.SourceHit 列表后调用 [retrieve]。
 * - 测试：直接构造 SourceHit 列表注入后测试。
 *
 * 行为保证：
 * - 与现有 MemoryRetrievalPolicy 检索结果一致。
 * - confirmed+enabled 过滤在数据注入时保证（调用方负责只注入合规 hit）。
 * - explain 保留。
 * - SearchIndex stale/rebuild 机制由 Android 层在注入前处理。
 */
class RuleBasedRetrievalEngine : RetrievalEngine {

    override val name = "rule_based"

    /** 内存中的检索数据集（由 Android 层注入） */
    private var hits: List<MemoryRetrievalPolicy.SourceHit> = emptyList()

    /**
     * 加载/刷新检索数据集。
     * Android 层应在每次检索前调用此方法，传入从各数据源收集的 SourceHit 列表。
     * 调用方负责确保传入的 hit 均为 confirmed + enabled。
     */
    fun loadData(newHits: List<MemoryRetrievalPolicy.SourceHit>) {
        this.hits = newHits.toList() // defensive copy
    }

    override suspend fun retrieve(request: RetrievalEngine.RetrievalRequest): List<RetrievalEngine.RetrievalResult> {
        if (request.query.isBlank()) return emptyList()
        if (hits.isEmpty()) return emptyList()

        // 1. 按 sourceTypes 筛选
        val typeFiltered = if (request.sourceTypes.isNullOrEmpty()) {
            hits
        } else {
            hits.filter { it.sourceType in request.sourceTypes }
        }

        if (typeFiltered.isEmpty()) return emptyList()

        // 2. 按关键词评分过滤（rawScore > 0）
        val scored = typeFiltered.filter { hit ->
            MemoryRetrievalPolicy.keywordScore(request.query, hit.snippet) > 0
        }

        // 3. 排序（MemoryRetrievalPolicy 统一评分）
        val sorted = MemoryRetrievalPolicy.sortByScore(scored)

        // 4. 截断
        val capped = sorted.take(request.limit)

        // 5. 转换为统一结果格式
        return capped.map { hit ->
            RetrievalEngine.RetrievalResult(
                sourceType = hit.sourceType,
                sourceId = hit.sourceId,
                snippet = hit.snippet,
                explain = hit.explain.ifBlank {
                    MemoryRetrievalPolicy.explainHit(hit, request.query)
                },
                score = MemoryRetrievalPolicy.score(hit),
                timestamp = null // SourceHit 无 timestamp 字段；可从 ageDays 推算但不精确
            )
        }
    }
}
