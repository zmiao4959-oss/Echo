package com.example.myapplication.policy

/**
 * 统一检索引擎接口 — Phase I 语义检索预研与可插拔智能层。
 *
 * 核心原则：
 * - 规则检索继续兜底，语义检索只做增强，不得破坏记忆治理。
 * - confirmed/enabled/disabled/pending 过滤必须在统一层执行，任一引擎不得绕过。
 * - 默认行为不改变。
 *
 * 实现：
 * - [RuleBasedRetrievalEngine] — 规则引擎（包装 MemoryRetrievalPolicy + SearchIndex）
 * - [SemanticRetrievalEngine] — 语义检索引擎（实验层，no-op fallback）
 * - [HybridRetrievalEngine] — 混合检索引擎（规则优先兜底）
 */
interface RetrievalEngine {

    /**
     * 单条检索结果。所有引擎必须返回此统一结构。
     */
    data class RetrievalResult(
        /** 数据来源类型：profile / memory_card / memory_md / life_record / diary */
        val sourceType: String,
        /** 来源唯一标识 */
        val sourceId: String,
        /** 匹配片段（截断至 200 字符） */
        val snippet: String,
        /** 人类可读的匹配解释（不含原始分数） */
        val explain: String,
        /** 归一化分数/置信度（0.0 - N，越高越相关） */
        val score: Double,
        /** 来源时间戳（毫秒），可能为 null */
        val timestamp: Long?
    )

    /**
     * 检索请求。
     */
    data class RetrievalRequest(
        /** 用户查询文本 */
        val query: String,
        /** 最大返回条数 */
        val limit: Int = 8,
        /**
         * 限定来源类型。null 或空集表示不限。
         * 合法值：profile / memory_card / memory_md / life_record / diary
         */
        val sourceTypes: Set<String>? = null
    )

    /**
     * 执行检索。
     *
     * 契约（所有实现必须遵守）：
     * 1. 不得返回 status != "confirmed" 或 enabled == false 的内容。
     * 2. 空查询或无结果时返回空列表。
     * 3. explain 字段必须是人类可读的自然语言，不得包含原始数值分数。
     *
     * @param request 检索请求
     * @return 按 score 降序排列的结果列表
     */
    suspend fun retrieve(request: RetrievalRequest): List<RetrievalResult>

    /**
     * 引擎显示名称，用于诊断和日志。
     */
    val name: String
}
