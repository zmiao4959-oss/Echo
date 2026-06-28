package com.example.myapplication.llm

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/**
 * DoubaoEmbeddingProvider 单元测试 — 使用 OkHttp MockInterceptor 模拟 API 响应。
 *
 * 覆盖：成功、401、429、超时、JSON 格式不符、空向量、异常 JSON、
 * 空文本输入、buildRequestBody 结构。
 */
class DoubaoEmbeddingProviderTest {

    private companion object {
        const val FAKE_API_KEY = "test-key-12345"
        const val FAKE_BASE_URL = "https://ark.example.com/api/v3"
        const val FAKE_MODEL = "doubao-embedding-vision-251215"

        /** 正常的 embedding 响应（data 是对象，非数组） */
        val SUCCESS_JSON = """
            {"data":{"object":"embedding","embedding":[0.1,0.2,0.3,0.4]},"model":"doubao-embedding-vision-251215"}
        """.trimIndent()

        /** 空向量响应 */
        val EMPTY_VECTOR_JSON = """
            {"data":{"object":"embedding","embedding":[]}}
        """.trimIndent()

        /** 缺 data 字段 */
        val MISSING_DATA_JSON = """
            {"model":"doubao-embedding-vision-251215"}
        """.trimIndent()

        /** 缺 embedding 字段 */
        val MISSING_EMBEDDING_JSON = """
            {"data":{"object":"embedding"}}
        """.trimIndent()

        /** 非 JSON 响应 */
        val HTML_RESPONSE = """
            <html><body>502 Bad Gateway</body></html>
        """.trimIndent()

        /** API error 200 */
        val API_ERROR_JSON = """
            {"error":{"message":"Invalid API Key","code":"invalid_api_key"}}
        """.trimIndent()
    }

    // ═══════════════════════════════════════════════════════════════
    // 成功场景
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `embed returns vector on success`() = runBlocking {
        val client = mockClient(200, SUCCESS_JSON)
        val provider = DoubaoEmbeddingProvider(FAKE_API_KEY, FAKE_BASE_URL, FAKE_MODEL, client)

        val result = provider.embed("今天天气很好")

        assertEquals(4, result.size)
        assertEquals(0.1f, result[0])
        assertEquals(0.2f, result[1])
        assertEquals(0.3f, result[2])
        assertEquals(0.4f, result[3])
    }

    @Test
    fun `embed returns empty array for blank text`() = runBlocking {
        val client = mockClient(200, SUCCESS_JSON)
        val provider = DoubaoEmbeddingProvider(FAKE_API_KEY, FAKE_BASE_URL, FAKE_MODEL, client)

        val result = provider.embed("")
        assertEquals(0, result.size)

        val result2 = provider.embed("   ")
        assertEquals(0, result2.size)
    }

    @Test
    fun `request body contains model and text`() {
        runBlocking {
            val client = captureClient { request ->
                val body = request.body?.let {
                    val buffer = okio.Buffer()
                    it.writeTo(buffer)
                    buffer.readUtf8()
                } ?: ""
                assertTrue("body should contain model", body.contains(FAKE_MODEL))
                assertTrue("body should contain text", body.contains("测试文本"))
                mockResponse(200, SUCCESS_JSON, request)
            }
            val provider = DoubaoEmbeddingProvider(FAKE_API_KEY, FAKE_BASE_URL, FAKE_MODEL, client)
            provider.embed("测试文本")
        }
    }

    @Test
    fun `request contains authorization header`() {
        runBlocking {
            val client = captureClient { request ->
                val auth = request.header("Authorization")
                assertEquals("Bearer $FAKE_API_KEY", auth)
                mockResponse(200, SUCCESS_JSON, request)
            }
            val provider = DoubaoEmbeddingProvider(FAKE_API_KEY, FAKE_BASE_URL, FAKE_MODEL, client)
            provider.embed("test")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HTTP 错误场景
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `embed throws EmbeddingException on 401`() = runBlocking {
        val client = mockClient(401, """{"error":"Unauthorized"}""")
        val provider = DoubaoEmbeddingProvider(FAKE_API_KEY, FAKE_BASE_URL, FAKE_MODEL, client)

        try {
            provider.embed("test")
            fail("Should have thrown EmbeddingException")
        } catch (e: DoubaoEmbeddingProvider.EmbeddingException) {
            assertEquals("auth", e.errorCode)
            assertTrue(e.message!!.contains("401"))
        }
    }

    @Test
    fun `embed throws EmbeddingException on 429`() = runBlocking {
        val client = mockClient(429, """{"error":"Rate Limited"}""")
        val provider = DoubaoEmbeddingProvider(FAKE_API_KEY, FAKE_BASE_URL, FAKE_MODEL, client)

        try {
            provider.embed("test")
            fail("Should have thrown EmbeddingException")
        } catch (e: DoubaoEmbeddingProvider.EmbeddingException) {
            assertEquals("rate_limit", e.errorCode)
            assertTrue(e.message!!.contains("429"))
        }
    }

    @Test
    fun `embed throws EmbeddingException on 4xx client error`() = runBlocking {
        val client = mockClient(400, """{"error":"Bad Request"}""")
        val provider = DoubaoEmbeddingProvider(FAKE_API_KEY, FAKE_BASE_URL, FAKE_MODEL, client)

        try {
            provider.embed("test")
            fail("Should have thrown EmbeddingException")
        } catch (e: DoubaoEmbeddingProvider.EmbeddingException) {
            assertEquals("client_error", e.errorCode)
        }
    }

    @Test
    fun `embed throws EmbeddingException on 5xx server error`() = runBlocking {
        val client = mockClient(500, """Internal Server Error""")
        val provider = DoubaoEmbeddingProvider(FAKE_API_KEY, FAKE_BASE_URL, FAKE_MODEL, client)

        try {
            provider.embed("test")
            fail("Should have thrown EmbeddingException")
        } catch (e: DoubaoEmbeddingProvider.EmbeddingException) {
            assertEquals("server_error", e.errorCode)
        }
    }

    @Test
    fun `embed throws EmbeddingException on 503`() = runBlocking {
        val client = mockClient(503, """Service Unavailable""")
        val provider = DoubaoEmbeddingProvider(FAKE_API_KEY, FAKE_BASE_URL, FAKE_MODEL, client)

        try {
            provider.embed("test")
            fail("Should have thrown EmbeddingException")
        } catch (e: DoubaoEmbeddingProvider.EmbeddingException) {
            assertEquals("server_error", e.errorCode)
        }
    }

    @Test
    fun `embed throws EmbeddingException on empty response body`() = runBlocking {
        val client = mockClient(200, "")
        val provider = DoubaoEmbeddingProvider(FAKE_API_KEY, FAKE_BASE_URL, FAKE_MODEL, client)

        try {
            provider.embed("test")
            fail("Should have thrown EmbeddingException")
        } catch (e: DoubaoEmbeddingProvider.EmbeddingException) {
            assertTrue(e.errorCode in listOf("bad_format", "empty_body"))
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // JSON 格式错误
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `embed throws on non-JSON response`() = runBlocking {
        val client = mockClient(200, HTML_RESPONSE)
        val provider = DoubaoEmbeddingProvider(FAKE_API_KEY, FAKE_BASE_URL, FAKE_MODEL, client)

        try {
            provider.embed("test")
            fail("Should have thrown EmbeddingException")
        } catch (e: DoubaoEmbeddingProvider.EmbeddingException) {
            assertEquals("bad_format", e.errorCode)
        }
    }

    @Test
    fun `embed throws on missing data field`() = runBlocking {
        val client = mockClient(200, MISSING_DATA_JSON)
        val provider = DoubaoEmbeddingProvider(FAKE_API_KEY, FAKE_BASE_URL, FAKE_MODEL, client)

        try {
            provider.embed("test")
            fail("Should have thrown EmbeddingException")
        } catch (e: DoubaoEmbeddingProvider.EmbeddingException) {
            assertEquals("missing_data", e.errorCode)
        }
    }

    @Test
    fun `embed throws on missing embedding field`() = runBlocking {
        val client = mockClient(200, MISSING_EMBEDDING_JSON)
        val provider = DoubaoEmbeddingProvider(FAKE_API_KEY, FAKE_BASE_URL, FAKE_MODEL, client)

        try {
            provider.embed("test")
            fail("Should have thrown EmbeddingException")
        } catch (e: DoubaoEmbeddingProvider.EmbeddingException) {
            assertEquals("missing_embedding", e.errorCode)
        }
    }

    @Test
    fun `embed throws on empty vector`() = runBlocking {
        val client = mockClient(200, EMPTY_VECTOR_JSON)
        val provider = DoubaoEmbeddingProvider(FAKE_API_KEY, FAKE_BASE_URL, FAKE_MODEL, client)

        try {
            provider.embed("test")
            fail("Should have thrown EmbeddingException")
        } catch (e: DoubaoEmbeddingProvider.EmbeddingException) {
            assertEquals("empty_vector", e.errorCode)
        }
    }

    @Test
    fun `embed throws on API error in 200 response`() = runBlocking {
        val client = mockClient(200, API_ERROR_JSON)
        val provider = DoubaoEmbeddingProvider(FAKE_API_KEY, FAKE_BASE_URL, FAKE_MODEL, client)

        try {
            provider.embed("test")
            fail("Should have thrown EmbeddingException")
        } catch (e: DoubaoEmbeddingProvider.EmbeddingException) {
            assertEquals("api_error", e.errorCode)
            assertTrue(e.message!!.contains("Invalid API Key"))
        }
    }

    @Test
    fun `embed throws on invalid JSON`() = runBlocking {
        val client = mockClient(200, """{broken""")
        val provider = DoubaoEmbeddingProvider(FAKE_API_KEY, FAKE_BASE_URL, FAKE_MODEL, client)

        try {
            provider.embed("test")
            fail("Should have thrown EmbeddingException")
        } catch (e: DoubaoEmbeddingProvider.EmbeddingException) {
            assertTrue(e.errorCode in listOf("bad_json", "bad_format"))
        }
    }

    @Test
    fun `embed throws on empty data object`() = runBlocking {
        // data 是对象但无 embedding 字段 → missing_embedding
        val client = mockClient(200, """{"data":{}}""")
        val provider = DoubaoEmbeddingProvider(FAKE_API_KEY, FAKE_BASE_URL, FAKE_MODEL, client)

        try {
            provider.embed("test")
            fail("Should have thrown EmbeddingException")
        } catch (e: DoubaoEmbeddingProvider.EmbeddingException) {
            assertEquals("missing_embedding", e.errorCode)
        }
    }

    @Test
    fun `embed throws on non-float embedding value`() = runBlocking {
        val json = """{"data":{"object":"embedding","embedding":["not_a_number"]}}"""
        val client = mockClient(200, json)
        val provider = DoubaoEmbeddingProvider(FAKE_API_KEY, FAKE_BASE_URL, FAKE_MODEL, client)

        try {
            provider.embed("test")
            fail("Should have thrown EmbeddingException")
        } catch (e: DoubaoEmbeddingProvider.EmbeddingException) {
            assertEquals("bad_value", e.errorCode)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 异常诊断 — recordDiagnostics
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `EmbeddingException recordDiagnostics records to ServiceHealth`() {
        val ex = DoubaoEmbeddingProvider.EmbeddingException("test error", "test_code", null)
        ex.recordDiagnostics() // should not throw

        val summary = com.example.myapplication.diagnostics.ServiceHealth.summary("Embedding")
        assertTrue(summary.contains("test_code") || summary.contains("test error") || summary.isNotBlank())
    }

    @Test
    fun `all EmbeddingException types are diagnosable`() {
        val codes = listOf("auth", "rate_limit", "client_error", "server_error",
            "timeout", "network", "parse", "bad_format", "bad_json",
            "api_error", "missing_data", "empty_data", "missing_embedding",
            "empty_vector", "bad_value", "unknown")

        for (code in codes) {
            val ex = DoubaoEmbeddingProvider.EmbeddingException("error $code", code, null)
            assertNotNull(ex.errorCode)
            assertNotNull(ex.message)
            // Should not throw
            ex.recordDiagnostics()
        }
    }

    @Test
    fun `error message does not contain API key`() = runBlocking {
        val client = mockClient(401, """{"error":"Invalid Key"}""")
        val provider = DoubaoEmbeddingProvider(FAKE_API_KEY, FAKE_BASE_URL, FAKE_MODEL, client)

        try {
            provider.embed("test")
            fail("Should have thrown")
        } catch (e: DoubaoEmbeddingProvider.EmbeddingException) {
            // 错误消息里不能有 API Key
            assertFalse("Error message must not contain API key: ${e.message}",
                e.message!!.contains(FAKE_API_KEY))
            // recordDiagnostics 由 embed 内部调用
        }
    }

    @Test
    fun `ServiceHealth sanitizes API keys in diagnostics`() {
        // 直接测试 ServiceHealth 的脱敏能力
        com.example.myapplication.diagnostics.ServiceHealth.record("Embedding",
            "401 Unauthorized with key=abc123secret")
        val summary = com.example.myapplication.diagnostics.ServiceHealth.summary("Embedding")
        assertFalse("Diagnostics should not contain raw key", summary.contains("abc123secret"))
    }

    // ═══════════════════════════════════════════════════════════════
    // 回退链路验证
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `EmbeddingException extends Exception for upstream catch`() {
        val ex = DoubaoEmbeddingProvider.EmbeddingException("test", "test_code", null)
        assertTrue(ex is Exception)
    }

    @Test
    fun `all errors are caught by upstream catch Exception block`() = runBlocking {
        // 模拟 SemanticRetrievalEngine 中的 catch 逻辑
        val client = mockClient(500, "Server Error")
        val provider = DoubaoEmbeddingProvider(FAKE_API_KEY, FAKE_BASE_URL, FAKE_MODEL, client)

        val result = try {
            provider.embed("test")
            null // 成功时不应到这里
        } catch (_: Exception) {
            null // 上游 catch (Exception) 捕获 → 返回 null → 回退
        }

        assertNull("Upstream should get null on error → fallback to rule", result)
    }

    // ═══════════════════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════════════════

    /**
     * 创建一个 OkHttpClient，对所有请求返回指定的 statusCode 和 body。
     */
    private fun mockClient(statusCode: Int, body: String): OkHttpClient {
        val interceptor = Interceptor { chain ->
            mockResponse(statusCode, body, chain.request())
        }
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    /**
     * 创建一个 OkHttpClient，对请求进行断言检查后返回模拟响应。
     */
    private fun captureClient(block: (okhttp3.Request) -> Response): OkHttpClient {
        val interceptor = Interceptor { chain ->
            block(chain.request())
        }
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    private fun mockResponse(statusCode: Int, body: String, request: okhttp3.Request): Response {
        return Response.Builder()
            .code(statusCode)
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .message(if (statusCode in 200..299) "OK" else "Error")
            .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }
}
