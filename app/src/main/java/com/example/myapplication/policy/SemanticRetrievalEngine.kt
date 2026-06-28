package com.example.myapplication.policy

/**
 * 语义检索引擎 — Phase I 实验层。
 *
 * ## 当前状态：预研接口，未接入真实 Embedding API
 *
 * 本引擎目前是 **no-op 实现**：
 * - 无 EmbeddingProvider 配置时，[retrieve] 自动返回空结果，不报错。
 * - 缓存数据结构已定义（[EmbeddingCacheEntry]），未来接入 API 时可直接使用。
 *
 * ## 隐私风险与成本（未来接入 Embedding API 时务必关注）
 *
 * ### 隐私风险
 * 1. **文本外泄** — 用户记忆、日记、画像等敏感内容会被发送到远程 Embedding 服务。
 *    必须确保 API 提供商不存储/不训练用户数据。
 * 2. **关联风险** — 多个文本片段同时发送可能被服务端关联，推断出用户画像。
 * 3. **传输安全** — 必须使用 HTTPS，且 API Key 不得在日志中明文出现。
 * 4. **合规** — 如涉及 GDPR/个人信息保护法，需提供数据删除机制。
 *
 * ### 成本
 * 1. **API 调用费用** — 每次检索需对 N 条候选项做 embedding，费用 = N × 单次 embedding 价格。
 *    示例：1000 条记忆 × ¥0.002/1K tokens ≈ ¥0.02/次检索（以 text-embedding-3-small 计）。
 * 2. **缓存收益** — [EmbeddingCacheEntry] 基于 textHash 缓存，文本未变时无需重新计算。
 * 3. **网络延迟** — 远程 API 调用增加检索延迟（~50-300ms），需异步处理。
 *
 * ### 未来接入步骤
 * 1. 实现 [EmbeddingProvider] 接口（OkHttp → OpenAI/其他兼容 API）。
 * 2. 在 [EmbeddingCacheEntry] 中存储真实 embedding 向量。
 * 3. 设置页增加 Embedding API 配置（URL + Key + Model）。
 * 4. 实现余弦相似度检索逻辑（替换当前 no-op）。
 * 5. 增加缓存持久化到 embedding_cache.json。
 * 6. 增加速率限制与重试机制。
 *
 * ## 安全保证
 * - [retrieve] 在调用 EmbeddingProvider 前会过滤掉 pending/disabled 内容。
 * - 当前 no-op 实现不会发送任何数据到远程服务。
 */
class SemanticRetrievalEngine : RetrievalEngine {

    override val name = "semantic_experiment"

    /** Embedding 服务提供者。null 时自动退回 no-op。 */
    var embeddingProvider: EmbeddingProvider? = null

    /** 内存中的候选数据（由 Android 层注入，已过滤 confirmed+enabled） */
    private var candidates: List<CandidateEntry> = emptyList()

    /** 内存中的 embedding 缓存 */
    private val cache = mutableMapOf<String, EmbeddingCacheEntry>()

    // ── 公共 API ──

    /**
     * 加载/刷新候选数据集。
     * Android 层应在每次检索前调用此方法。
     * **调用方负责确保传入的 candidate 均为 confirmed + enabled。**
     */
    fun loadData(newCandidates: List<CandidateEntry>) {
        this.candidates = newCandidates.toList()
    }

    /**
     * 加载缓存的 embedding 向量（如从磁盘恢复）。
     */
    fun loadCache(entries: List<EmbeddingCacheEntry>) {
        cache.clear()
        entries.forEach { cache[keyFor(it.sourceType, it.sourceId)] = it }
    }

    /**
     * 导出当前缓存（用于持久化）。
     */
    fun exportCache(): List<EmbeddingCacheEntry> = cache.values.toList()

    override suspend fun retrieve(request: RetrievalEngine.RetrievalRequest): List<RetrievalEngine.RetrievalResult> {
        // No-op fallback: 无 provider 时返回空
        val provider = embeddingProvider ?: return emptyList()

        if (request.query.isBlank()) return emptyList()
        if (candidates.isEmpty()) return emptyList()

        // 安全过滤：再次确认不包含 pending/disabled
        val safe = filterConfirmedEnabled(candidates)

        // 按 sourceTypes 筛选
        val typeFiltered = if (request.sourceTypes.isNullOrEmpty()) {
            safe
        } else {
            safe.filter { it.sourceType in request.sourceTypes }
        }

        if (typeFiltered.isEmpty()) return emptyList()

        // 获取或计算 query embedding
        val queryEmbedding = getOrComputeEmbedding(
            sourceType = "__query__",
            sourceId = request.query,
            text = request.query,
            provider = provider
        ) ?: return emptyList()

        // 获取或计算候选 embedding，计算余弦相似度
        val scored = typeFiltered.mapNotNull { candidate ->
            val emb = getOrComputeEmbedding(
                sourceType = candidate.sourceType,
                sourceId = candidate.sourceId,
                text = candidate.text,
                provider = provider
            )
            if (emb != null) {
                val similarity = cosineSimilarity(queryEmbedding, emb)
                candidate to similarity
            } else {
                null
            }
        }

        return scored
            .filter { (_, score) -> score > 0.0 }
            .sortedByDescending { (_, score) -> score }
            .take(request.limit)
            .map { (candidate, score) ->
                RetrievalEngine.RetrievalResult(
                    sourceType = candidate.sourceType,
                    sourceId = candidate.sourceId,
                    snippet = candidate.text.take(200),
                    explain = "语义匹配（实验）",
                    score = score,
                    timestamp = candidate.timestamp
                )
            }
    }

    // ── 内部方法 ──

    private fun filterConfirmedEnabled(candidates: List<CandidateEntry>): List<CandidateEntry> {
        return candidates.filter { it.enabled && it.status == "confirmed" }
    }

    private suspend fun getOrComputeEmbedding(
        sourceType: String,
        sourceId: String,
        text: String,
        provider: EmbeddingProvider
    ): FloatArray? {
        val key = keyFor(sourceType, sourceId)
        val textHash = hashText(text)

        // 命中缓存（文本未变）
        val cached = cache[key]
        if (cached != null && cached.textHash == textHash && cached.embedding != null) {
            return cached.embedding
        }

        // 调用远程 API
        val embedding = try {
            provider.embed(text)
        } catch (_: Exception) {
            null
        }

        // 更新缓存
        if (embedding != null) {
            cache[key] = EmbeddingCacheEntry(
                sourceType = sourceType,
                sourceId = sourceId,
                textHash = textHash,
                embedding = embedding,
                cachedAt = System.currentTimeMillis()
            )
        }

        return embedding
    }

    private fun keyFor(sourceType: String, sourceId: String) = "$sourceType:$sourceId"

    private fun hashText(text: String): String {
        // 简单哈希（不依赖 java.security，保持轻量）
        var h = 0L
        for (c in text) {
            h = h * 31 + c.code.toLong()
        }
        return h.toString(36)
    }

    companion object {
        /**
         * 余弦相似度。
         */
        fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
            if (a.size != b.size) return 0.0
            var dot = 0.0
            var normA = 0.0
            var normB = 0.0
            for (i in a.indices) {
                dot += a[i].toDouble() * b[i].toDouble()
                normA += a[i].toDouble() * a[i].toDouble()
                normB += b[i].toDouble() * b[i].toDouble()
            }
            val denom = Math.sqrt(normA) * Math.sqrt(normB)
            return if (denom == 0.0) 0.0 else dot / denom
        }
    }

    // ── 数据类型 ──

    /**
     * 候选条目（由 Android 层注入）。
     */
    data class CandidateEntry(
        val sourceType: String,
        val sourceId: String,
        val text: String,
        val enabled: Boolean = true,
        val status: String = "confirmed",
        val timestamp: Long? = null
    )

    /**
     * Embedding 缓存条目。
     *
     * - [sourceType] + [sourceId] 唯一标识一条数据。
     * - [textHash] 用于检测文本变更（变更后需重新计算 embedding）。
     * - [embedding] 为 null 表示尚未计算。
     */
    data class EmbeddingCacheEntry(
        val sourceType: String,
        val sourceId: String,
        val textHash: String,
        val embedding: FloatArray? = null,
        val cachedAt: Long = 0L
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EmbeddingCacheEntry) return false
            return sourceType == other.sourceType &&
                sourceId == other.sourceId &&
                textHash == other.textHash &&
                cachedAt == other.cachedAt &&
                embedding.contentEquals(other.embedding)
        }

        override fun hashCode(): Int {
            var result = sourceType.hashCode()
            result = 31 * result + sourceId.hashCode()
            result = 31 * result + textHash.hashCode()
            result = 31 * result + cachedAt.hashCode()
            result = 31 * result + (embedding?.contentHashCode() ?: 0)
            return result
        }
    }

    /**
     * Embedding 服务提供者接口。
     *
     * 未来实现：OkHttp → OpenAI / 兼容 Embedding API。
     * 当前阶段不接入真实 API。
     */
    interface EmbeddingProvider {
        /**
         * 将文本转换为 embedding 向量。
         * @throws Exception 网络错误、API 错误等
         */
        suspend fun embed(text: String): FloatArray
    }
}
