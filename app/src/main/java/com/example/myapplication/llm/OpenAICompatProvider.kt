package com.example.myapplication.llm

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容 API Provider（OkHttp 实现，支持流式 SSE）。
 */
class OpenAICompatProvider(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String
) : LLMProvider {

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun chat(
        messages: List<LLMMessage>,
        tools: List<Map<String, Any>>?,
        temperature: Float,
        maxTokens: Int
    ): LLMResponse = withContext(Dispatchers.IO) {
        val body = buildRequestBody(messages, tools, temperature, maxTokens, stream = false)
        val request = buildRequest(body)

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response body")

        if (!response.isSuccessful) {
            throw Exception("API error ${response.code}: $responseBody")
        }

        parseNonStreamResponse(responseBody)
    }

    override fun chatStream(
        messages: List<LLMMessage>,
        tools: List<Map<String, Any>>?,
        temperature: Float,
        maxTokens: Int
    ): Flow<LLMStreamChunk> = callbackFlow {
        val body = buildRequestBody(messages, tools, temperature, maxTokens, stream = true)
        val request = buildRequest(body)

        val call = client.newCall(request)
        val response = call.execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw Exception("API error ${response.code}: $errorBody")
        }

        val reader = BufferedReader(InputStreamReader(response.body?.byteStream() ?: throw Exception("No response stream")))

        // 累积 tool_calls delta（OpenAI 流式 tool calls 是增量返回的）
        val accumulatedToolCalls = mutableMapOf<Int, MutableMap<String, Any>>()
        var linesRead = 0
        var dataLines = 0
        var contentChunks = 0

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                linesRead++

                if (l.isEmpty()) continue
                // 兼容 "data:" 和 "data: " 两种 SSE 格式
                if (!l.startsWith("data:")) continue

                dataLines++
                val data = l.removePrefix("data:").trimStart()
                Log.d("LLMStream", "SSE[$dataLines]: ${data.take(200)}")

                if (data == "[DONE]") {
                    Log.d("LLMStream", "Stream done: read=$linesRead dataLines=$dataLines contentChunks=$contentChunks")
                    // 流式结束，发送累积的 tool_calls
                    if (accumulatedToolCalls.isNotEmpty()) {
                        val finalToolCalls = accumulatedToolCalls.values.map { raw ->
                            val func = raw["function"] as? Map<*, *> ?: emptyMap<String, Any>()
                            LLMMessage.ToolCall(
                                id = raw["id"] as? String ?: "",
                                function = LLMMessage.FunctionCall(
                                    name = func["name"] as? String ?: "",
                                    arguments = func["arguments"] as? String ?: ""
                                )
                            )
                        }
                        send(LLMStreamChunk(deltaToolCalls = finalToolCalls, finishReason = "tool_calls"))
                    }
                    break
                }

                try {
                    val json = JsonParser.parseString(data).asJsonObject
                    val choices = json.getAsJsonArray("choices")
                    if (choices == null || choices.size() == 0) {
                        // usage-only chunk
                        val usageObj = json.getAsJsonObject("usage")
                        if (usageObj != null) {
                            val usage = mapOf(
                                "prompt_tokens" to (usageObj.get("prompt_tokens")?.takeIf { !it.isJsonNull }?.asInt ?: 0),
                                "completion_tokens" to (usageObj.get("completion_tokens")?.takeIf { !it.isJsonNull }?.asInt ?: 0)
                            )
                            send(LLMStreamChunk(usage = usage))
                        }
                        continue
                    }

                    val choice = choices[0].asJsonObject
                    val delta = choice.getAsJsonObject("delta") ?: continue
                    val finishReason = choice.get("finish_reason")?.takeIf { !it.isJsonNull }?.asString

                    // 文本内容（兼容 JSON null）
                    val contentEl = delta.get("content")
                    val deltaContent = if (contentEl != null && !contentEl.isJsonNull) contentEl.asString else ""
                    if (deltaContent.isNotEmpty()) {
                        contentChunks++
                    }

                    // tool_calls delta（兼容 JSON null 值）
                    val toolCallsDelta = delta.getAsJsonArray("tool_calls")
                    if (toolCallsDelta != null) {
                        for (tcElement in toolCallsDelta) {
                            val tc = tcElement.asJsonObject
                            val idx = tc.get("index")?.takeIf { !it.isJsonNull }?.asInt ?: continue
                            val entry = accumulatedToolCalls.getOrPut(idx) {
                                mutableMapOf(
                                    "id" to "",
                                    "function" to mutableMapOf("name" to "", "arguments" to "")
                                )
                            }
                            val tcId = tc.get("id")?.takeIf { !it.isJsonNull }?.asString
                            if (!tcId.isNullOrEmpty()) entry["id"] = tcId

                            val func = tc.getAsJsonObject("function")
                            if (func != null) {
                                @Suppress("UNCHECKED_CAST")
                                val funcMap = entry["function"] as MutableMap<String, String>
                                val fName = func.get("name")?.takeIf { !it.isJsonNull }?.asString
                                if (!fName.isNullOrEmpty()) funcMap["name"] = funcMap["name"]!! + fName
                                val fArgs = func.get("arguments")?.takeIf { !it.isJsonNull }?.asString
                                if (!fArgs.isNullOrEmpty()) funcMap["arguments"] = funcMap["arguments"]!! + fArgs
                            }
                        }
                    }

                    send(LLMStreamChunk(deltaContent = deltaContent, finishReason = finishReason))

                    if (finishReason == "stop") break
                } catch (e: Exception) {
                    // JSON 解析失败：记录并跳过
                    Log.w("LLMStream", "Parse error for SSE line: ${e.message}", e)
                    continue
                }
            }
        } finally {
            reader.close()
            response.close()
        }

        Log.d("LLMStream", "Stream finished: read=$linesRead dataLines=$dataLines contentChunks=$contentChunks")

        close()

        // 协程取消时取消 HTTP 请求
        awaitClose {
            call.cancel()
        }
    }

    override fun supportsTools(): Boolean = true

    // ── private helpers ──

    private fun normalizeBaseUrl(): String {
        val url = baseUrl.trim()
        if (url.isBlank()) return url
        return if (url.startsWith("http://") || url.startsWith("https://")) url
        else "https://$url"
    }

    private fun buildRequest(body: String): Request {
        val base = normalizeBaseUrl()
        if (base.isBlank()) {
            throw IllegalStateException("LLM API Base URL 未配置，请先在设置中填写")
        }
        val url = if (base.endsWith("/")) "${base}chat/completions" else "$base/chat/completions"
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()
    }

    private fun buildRequestBody(
        messages: List<LLMMessage>,
        tools: List<Map<String, Any>>?,
        temperature: Float,
        maxTokens: Int,
        stream: Boolean
    ): String {
        val root = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to messages.map { msg ->
                val m = mutableMapOf<String, Any>("role" to msg.role, "content" to msg.content)
                msg.toolCallId?.let { m["tool_call_id"] = it }
                msg.toolCalls?.let { m["tool_calls"] = it.map { tc ->
                    mapOf(
                        "id" to tc.id,
                        "type" to tc.type,
                        "function" to mapOf("name" to tc.function.name, "arguments" to tc.function.arguments)
                    )
                }}
                msg.name?.let { m["name"] = it }
                m
            },
            "temperature" to temperature,
            "max_tokens" to maxTokens,
            "stream" to stream
        )
        if (stream) {
            root["stream_options"] = mapOf("include_usage" to true)
        }
        if (!tools.isNullOrEmpty()) {
            root["tools"] = tools
        }
        return gson.toJson(root)
    }

    private fun parseNonStreamResponse(jsonStr: String): LLMResponse {
        val json = JsonParser.parseString(jsonStr).asJsonObject
        val choices = json.getAsJsonArray("choices") ?: return LLMResponse()
        val choice = choices[0].asJsonObject
        val message = choice.getAsJsonObject("message") ?: return LLMResponse()

        val content = message.get("content")?.takeIf { !it.isJsonNull }?.asString ?: ""
        val toolCallsRaw = message.getAsJsonArray("tool_calls")
        val toolCalls = toolCallsRaw?.map { tc ->
            val obj = tc.asJsonObject
            val func = obj.getAsJsonObject("function") ?: return@map LLMMessage.ToolCall(
                id = "", function = LLMMessage.FunctionCall("", "")
            )
            LLMMessage.ToolCall(
                id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString ?: "",
                function = LLMMessage.FunctionCall(
                    name = func.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    arguments = func.get("arguments")?.takeIf { !it.isJsonNull }?.asString ?: ""
                )
            )
        } ?: emptyList()

        val usageObj = json.getAsJsonObject("usage")
        val usage = if (usageObj != null) mapOf(
            "prompt_tokens" to (usageObj.get("prompt_tokens")?.takeIf { !it.isJsonNull }?.asInt ?: 0),
            "completion_tokens" to (usageObj.get("completion_tokens")?.takeIf { !it.isJsonNull }?.asInt ?: 0)
        ) else emptyMap()

        val finishReason = choice.get("finish_reason")?.takeIf { !it.isJsonNull }?.asString ?: "stop"

        return LLMResponse(
            content = content,
            toolCalls = toolCalls,
            finishReason = finishReason,
            usage = usage
        )
    }
}
