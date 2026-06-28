package com.example.myapplication.llm

import com.example.myapplication.diagnostics.ServiceHealth
import com.example.myapplication.net.HttpClient
import com.example.myapplication.policy.SemanticRetrievalEngine
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * 火山豆包 Embedding Provider — 调用 multimodal_embeddings API。
 *
 * API: POST {baseUrl}/embeddings/multimodal（豆包多模态 embedding 专用端点）
 * 文档: https://www.volcengine.com/docs/82379/1523520
 * 认证: Bearer token (API Key)
 * 模型: doubao-embedding-vision-251215 (默认)
 *
 * 实现 [SemanticRetrievalEngine.EmbeddingProvider] 接口。
 *
 * ## 错误处理
 * 所有异常均转换为 [EmbeddingException] 抛出，上游 [SemanticRetrievalEngine] 自动捕获
 * 并回退 null → [HybridRetrievalEngine] 回退规则检索。不会打断聊天流程。
 *
 * - 网络错误 / 超时 → EmbeddingException → 回退
 * - 401 Unauthorized → EmbeddingException → 回退 + 诊断
 * - 429 Rate Limited → EmbeddingException → 回退 + 诊断
 * - 4xx/5xx → EmbeddingException → 回退 + 诊断
 * - JSON 格式不符 / 空向量 → EmbeddingException → 回退 + 诊断
 *
 * ## 隐私
 * - API Key 不写入日志（由 ServiceHealth.record() 自动脱敏）
 * - 请求 body（含用户文本）不写入日志
 */
class DoubaoEmbeddingProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://ark.cn-beijing.volces.com/api/v3",
    private val model: String = "doubao-embedding-vision-251215",
    httpClient: OkHttpClient = HttpClient.instance
) : SemanticRetrievalEngine.EmbeddingProvider {

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** 短超时客户端：embedding 不应长时间等待 */
    private val client: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun embed(text: String): FloatArray = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext floatArrayOf()

        val body = buildRequestBody(text)
        val request = buildRequest(body)

        try {
            val response = client.newCall(request).execute()
            val responseBody = try {
                response.body?.string() ?: ""
            } catch (e: IOException) {
                throw EmbeddingException("Empty response body", "empty_body", e)
            }

            if (!response.isSuccessful) {
                val code = response.code
                val reason = classifyHttpError(code, responseBody)
                throw reason
            }

            parseEmbeddingResponse(responseBody)
        } catch (e: EmbeddingException) {
            e.recordDiagnostics()
            throw e
        } catch (e: SocketTimeoutException) {
            val ex = EmbeddingException("Embedding request timeout", "timeout", e)
            ex.recordDiagnostics()
            throw ex
        } catch (e: IOException) {
            val ex = EmbeddingException("Embedding network error: ${e.message}", "network", e)
            ex.recordDiagnostics()
            throw ex
        } catch (e: JsonParseException) {
            val ex = EmbeddingException("Embedding response parse error: ${e.message}", "parse", e)
            ex.recordDiagnostics()
            throw ex
        } catch (e: Exception) {
            val ex = EmbeddingException("Embedding unexpected error: ${e.message}", "unknown", e)
            ex.recordDiagnostics()
            throw ex
        }
    }

    // ── HTTP 错误分类 ──

    private fun classifyHttpError(code: Int, body: String): EmbeddingException {
        return when (code) {
            401 -> EmbeddingException("Embedding API 401 Unauthorized — check API Key", "auth", null)
            429 -> EmbeddingException("Embedding API 429 Rate Limited", "rate_limit", null)
            in 400..499 -> EmbeddingException("Embedding API $code: ${body.take(200)}", "client_error", null)
            in 500..599 -> EmbeddingException("Embedding API $code server error", "server_error", null)
            else -> EmbeddingException("Embedding API unexpected $code", "unknown_http", null)
        }
    }

    // ── 请求构建 ──

    private fun buildRequest(body: String): Request {
        val url = normalizeUrl().let {
            // 豆包多模态 embedding 端点：/embeddings/multimodal
            // 文档: https://www.volcengine.com/docs/82379/1523520
            if (it.endsWith("/")) "${it}embeddings/multimodal" else "$it/embeddings/multimodal"
        }
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()
    }

    private fun buildRequestBody(text: String): String {
        // 豆包 multimodal-embeddings 格式：input 为 typed 数组
        val root = mapOf(
            "model" to model,
            "input" to listOf(
                mapOf("type" to "text", "text" to text)
            )
        )
        return gson.toJson(root)
    }

    // ── 响应解析 ──

    private fun parseEmbeddingResponse(jsonStr: String): FloatArray {
        // JSON 格式校验（空响应 / HTML / 非 JSON 均在此拦截）
        val trimmed = jsonStr.trim()
        if (!trimmed.startsWith("{")) {
            throw EmbeddingException(
                "Embedding response is not JSON: ${trimmed.take(100)}", "bad_format", null
            )
        }

        val json = try {
            JsonParser.parseString(jsonStr).asJsonObject
        } catch (e: JsonParseException) {
            throw EmbeddingException("Embedding response invalid JSON", "bad_json", e)
        }

        // 检查是否有 error 字段（某些 API 在 200 下也返回错误）
        if (json.has("error")) {
            val err = json.getAsJsonObject("error")
            val errMsg = err?.get("message")?.asString ?: "unknown"
            throw EmbeddingException("Embedding API error: $errMsg", "api_error", null)
        }

        // data 是对象（非数组）：{ "object": "embedding", "embedding": [...] }
        val data = json.getAsJsonObject("data")
            ?: throw EmbeddingException("Missing 'data' in embedding response", "missing_data", null)

        val embedding = data.getAsJsonArray("embedding")
            ?: throw EmbeddingException("Missing 'embedding' field in response", "missing_embedding", null)

        if (embedding.size() == 0) {
            throw EmbeddingException("Empty embedding vector", "empty_vector", null)
        }

        val result = FloatArray(embedding.size())
        for (i in 0 until embedding.size()) {
            try {
                result[i] = embedding.get(i).asFloat
            } catch (e: Exception) {
                throw EmbeddingException(
                    "Invalid embedding value at index $i", "bad_value", e
                )
            }
        }
        return result
    }

    private fun normalizeUrl(): String {
        val url = baseUrl.trim()
        if (url.isBlank()) return url
        return if (url.startsWith("http://") || url.startsWith("https://")) url
        else "https://$url"
    }

    // ── 异常类型 ──

    /**
     * Embedding 异常。携带错误码供诊断，不含 API Key 或用户文本。
     */
    class EmbeddingException(
        message: String,
        val errorCode: String,
        cause: Throwable? = null
    ) : Exception(message, cause) {
        /** 写入诊断记录（自动脱敏） */
        fun recordDiagnostics() {
            ServiceHealth.record("Embedding", "[$errorCode] $message")
        }
    }

    companion object {
        internal const val TAG = "DoubaoEmbedding"
        internal const val DIAG_SERVICE = "Embedding"
    }
}
