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

/** 工具成功响应 JSON */
fun jsonOk(message: String, id: String?): String {
    val idPart = if (id != null) """, "id": "${escapeJson(id)}"""" else ""
    return """{"ok": true, "message": "${escapeJson(message)}"$idPart}"""
}

/** 工具失败响应 JSON */
fun jsonError(error: String): String {
    return """{"ok": false, "error": "${escapeJson(error)}"}"""
}

private fun escapeJson(s: String): String {
    return s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
