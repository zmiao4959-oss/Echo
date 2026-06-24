package com.example.myapplication.tools

import android.util.Log

/**
 * 全局工具注册中心（单例）。
 */
object ToolRegistry {

    private const val TAG = "ToolRegistry"
    private const val MAX_TOOL_RESULT_CHARS = 32_000

    private val tools = mutableMapOf<String, ToolDefinition>()

    fun register(tool: ToolDefinition) {
        tools[tool.name] = tool
        Log.d(TAG, "Registered tool: ${tool.name} (risk=${tool.riskLevel})")
    }

    fun get(name: String): ToolDefinition? = tools[name]

    fun toolNames(): List<String> = tools.keys.sorted()

    /** 生成 OpenAI function-calling 格式的工具列表 */
    fun listForLLM(): List<Map<String, Any>> {
        return tools.values.map { it.schema }
    }

    /** 生成系统提示中的工具描述 */
    fun getDescriptions(): String {
        val sb = StringBuilder()
        for (td in tools.values) {
            var extra = ""
            if (td.riskLevel != "low") extra = " [${td.riskLevel} risk]"
            if (td.requireApproval) extra += " [requires approval]"
            sb.appendLine("  <tool>")
            sb.appendLine("    <name>${td.name}</name>")
            sb.appendLine("    <description>${td.description}$extra</description>")
            sb.appendLine("  </tool>")
        }
        return sb.toString()
    }

    /** 执行工具，返回结果字符串 */
    suspend fun execute(name: String, arguments: Map<String, Any?>): String {
        val tool = tools[name] ?: return "Error: Unknown tool '$name'"

        // 过滤掉 _context 等内部字段，并只保留 schema 声明的参数
        val params = (tool.schema["function"] as? Map<*, *>)?.get("parameters") as? Map<*, *>
        val required: List<String> = (params?.get("required") as? List<*>)?.map { it.toString() } ?: emptyList()
        val properties: Map<String, Any?> = (params?.get("properties") as? Map<*, *>)?.mapKeys { it.key.toString() } ?: emptyMap()

        // 只保留 schema 声明的参数，排除 _ 开头的内部字段
        val filteredArgs: MutableMap<String, Any?> = mutableMapOf()
        for ((key, value) in arguments) {
            if (!key.startsWith("_") && properties.containsKey(key)) {
                filteredArgs[key] = value
            }
        }

        val missing = required.filter { !filteredArgs.containsKey(it) }
        if (missing.isNotEmpty()) {
            return "Error: Missing required argument(s) for '${tool.name}': ${missing.joinToString(", ")}"
        }

        return try {
            val result = tool.executor(filteredArgs)
            truncateResult(result)
        } catch (e: Exception) {
            Log.e(TAG, "Tool '${tool.name}' execution failed", e)
            "Error executing '${tool.name}': ${e.message}"
        }
    }

    private fun truncateResult(text: String): String {
        if (text.length <= MAX_TOOL_RESULT_CHARS) return text
        val head = MAX_TOOL_RESULT_CHARS * 2 / 3
        val tail = MAX_TOOL_RESULT_CHARS - head
        val omitted = text.length - head - tail
        return "${text.take(head).trimEnd()}\n\n... [$omitted chars omitted] ...\n\n${text.takeLast(tail).trimStart()}"
    }
}
