package com.example.myapplication.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Web 工具：web_fetch（浏览器快照）。
 */
object WebTools {

    private val client = com.example.myapplication.net.HttpClient.instance.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun registerAll() {
        ToolRegistry.register(webFetchTool)
    }

    private val webFetchTool = ToolDefinition(
        name = "web_fetch",
        description = "Fetch the content of a public http(s) URL and return text summary.",
        schema = mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "web_fetch",
                "description" to "Fetch a public http(s) URL and return body text (truncated).",
                "parameters" to mapOf(
                    "type" to "object",
                    "required" to listOf("url"),
                    "properties" to mapOf(
                        "url" to mapOf("type" to "string", "description" to "http(s) URL to fetch"),
                        "maxChars" to mapOf("type" to "integer", "description" to "Max characters to return (default 10000)")
                    )
                )
            )
        ),
        requireApproval = false,
        riskLevel = "medium",
        tags = listOf("web"),
        timeoutSec = 30
    ) { args ->
        val url = args["url"] as? String ?: return@ToolDefinition "Error: url is required"
        val maxChars = (args["maxChars"] as? Number)?.toInt()?.coerceIn(500, 50000) ?: 10000

        // 安全校验
        val lowerUrl = url.lowercase().trim()
        if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) {
            return@ToolDefinition "Error: only http/https URLs are allowed"
        }

        // 禁止内网地址
        val blockedHosts = listOf("localhost", "127.", "10.", "192.168.", "172.16.", "172.17.", "172.18.",
            "172.19.", "172.20.", "172.21.", "172.22.", "172.23.", "172.24.", "172.25.", "172.26.",
            "172.27.", "172.28.", "172.29.", "172.30.", "172.31.", "0.0.0.0")
        val hostname = java.net.URI(url).host ?: ""
        if (blockedHosts.any { hostname.startsWith(it) || hostname == it.trimEnd('.') }) {
            return@ToolDefinition "Error: blocked host '$hostname' (local/private addresses not allowed)"
        }

        try {
            val request = Request.Builder().url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (compatible; ClawSpeaker-Android/1.0)")
                .build()

            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            val body = response.body?.string() ?: return@ToolDefinition "Error: empty response"

            // 简单提取文本（去掉 HTML 标签）
            val text = body.replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            val truncated = if (text.length > maxChars) text.take(maxChars) + "\n... (truncated at $maxChars chars)" else text
            val title = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE).find(body)?.groupValues?.getOrNull(1)?.trim() ?: ""
            "[Page: ${response.request.url}]\n[Title: $title]\n\n$truncated"
        } catch (e: Exception) {
            "Error fetching $url: ${e.message}"
        }
    }
}
