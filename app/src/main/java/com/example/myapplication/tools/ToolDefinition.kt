package com.example.myapplication.tools

/**
 * 工具定义。
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val schema: Map<String, Any>,
    val requireApproval: Boolean = false,
    val riskLevel: String = "low",     // low | medium | high
    val tags: List<String> = emptyList(),
    val timeoutSec: Long? = null,
    val executor: suspend (Map<String, Any?>) -> String
)
