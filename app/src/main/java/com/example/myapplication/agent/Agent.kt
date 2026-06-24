package com.example.myapplication.agent

import android.util.Log
import com.example.myapplication.MyApplication
import com.example.myapplication.config.AppConfig
import com.example.myapplication.llm.LLMMessage
import com.example.myapplication.llm.LLMProvider
import com.example.myapplication.llm.LLMResponse
import com.example.myapplication.llm.OpenAICompatProvider
import com.example.myapplication.memory.*
import com.example.myapplication.tools.ToolRegistry
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * Agent 核心引擎 — 移植自 clawspeaker agent.py。
 */
class Agent(
    private val sessionManager: SessionManager,
    private val statusCompacting: String = "上下文较长，正在压缩...",
    private val statusExecutingTools: String = "正在执行工具调用...",
    private val statusMaxRounds: String = "达到工具调用上限，正在总结...",
    private val statusErrorPrefix: String = "处理消息时发生错误: "
) {
    private val gson = Gson()

    /** 构建 System Prompt（缓存 + 工具列表 + 运行时信息） */
    private fun buildSystemPrompt(): String {
        val static = FileStore.buildStaticSystemPrompt()

        // 注入工具描述
        val toolsDesc = ToolRegistry.getDescriptions()

        val runtime = """
## Runtime Info
- Current time: ${LocalDateTime.now()}
- Platform: Android

## Available Tools
$toolsDesc
        """.trimIndent()

        return "$static\n\n$runtime"
    }

    /** 记忆搜索前缀 */
    private fun memoryPrefix(userMessage: String): String {
        if (userMessage.isBlank()) return ""
        val results = MemorySearch.keywordSearch(
            FileStore.workspaceDir,
            userMessage,
            maxResults = 3
        )
        if (results.isEmpty()) return ""

        val lines = mutableListOf("[Memory Search Results]")
        for (r in results) {
            lines.add("Source: ${r.filePath} (score: ${"%.2f".format(r.score)})")
            lines.add(r.snippet)
            lines.add("")
        }
        return lines.joinToString("\n")
    }

    /** 创建或获取 LLM Provider */
    private fun createProvider(): LLMProvider {
        val config = MyApplication.instance.appConfig
        return OpenAICompatProvider(
            apiKey = config.llmApiKey,
            baseUrl = config.llmBaseUrl,
            model = config.llmModel
        )
    }

    /**
     * 核心方法：接收用户消息，运行 Agent Loop，返回 Flow 流式输出。
     */
    fun processMessageStream(
        context: AgentContext
    ): Flow<AgentStreamEvent> = flow {
        val config = MyApplication.instance.appConfig
        val provider = createProvider()
        val session = withContext(Dispatchers.IO) {
            sessionManager.resolveSession(context.chatId)
                ?: sessionManager.getOrCreate(context.chatId, context.channel, context.accountId)
        }

        // 添加用户消息
        if (context.userMessage.isNotBlank()) {
            val prefix = memoryPrefix(context.userMessage)
            val fullContent = if (prefix.isNotBlank()) "$prefix\n${context.userMessage}" else context.userMessage
            session.addMessage(LLMMessage(role = "user", content = fullContent))
        }

        val systemPrompt = buildSystemPrompt()
        val tools = ToolRegistry.listForLLM()

        val maxRounds = config.maxToolRounds
        var finalResponse = ""
        var hitMaxRounds = false

        try {
            for (roundNum in 1..maxRounds) {
                // 检查是否需要压缩
                if (session.shouldCompact(config.maxContextTokens)) {
                    emit(AgentStreamEvent.Status(statusCompacting))
                    withContext(Dispatchers.IO) {
                        sessionManager.compact(session, provider)
                    }
                }

                val messages = listOf(LLMMessage(role = "system", content = systemPrompt)) + session.messages
                val useStream = (roundNum == 1)

                val response = if (useStream) {
                    // 流式调用
                    var fullContent = ""
                    var finishedToolCalls: List<LLMMessage.ToolCall> = emptyList()
                    var finishReason = "stop"
                    var usage = emptyMap<String, Int>()

                    provider.chatStream(messages, if (tools.isEmpty()) null else tools,
                        temperature = config.llmTemperature,
                        maxTokens = config.llmMaxTokens
                    ).collect { chunk ->
                        if (chunk.deltaContent.isNotEmpty()) {
                            fullContent += chunk.deltaContent
                            emit(AgentStreamEvent.TextDelta(chunk.deltaContent))
                        }
                        if (chunk.deltaToolCalls.isNotEmpty()) {
                            finishedToolCalls = chunk.deltaToolCalls
                        }
                        if (chunk.finishReason != null) {
                            finishReason = chunk.finishReason
                        }
                        if (chunk.usage.isNotEmpty()) {
                            usage = chunk.usage
                        }
                    }

                    LLMResponse(content = fullContent, toolCalls = finishedToolCalls,
                        finishReason = finishReason, usage = usage)
                } else {
                    emit(AgentStreamEvent.Status(statusExecutingTools))
                    withContext(Dispatchers.IO) {
                        provider.chat(messages, if (tools.isEmpty()) null else tools,
                            temperature = config.llmTemperature,
                            maxTokens = config.llmMaxTokens)
                    }
                }

                if (response.content.isNotEmpty()) {
                    finalResponse = response.content
                }

                // 无工具调用 → 结束循环
                if (response.toolCalls.isEmpty()) {
                    if (response.content.isNotEmpty()) {
                        session.addMessage(LLMMessage(role = "assistant", content = response.content))
                        Log.d("TTS", "Agent emitting TTSHint: content_len=${response.content.length}")
                        emit(AgentStreamEvent.TTSHint(response.content))
                    } else {
                        Log.w("TTS", "Agent: empty content, no TTSHint")
                    }
                    break
                }

                // 有工具调用
                val assistantMsg = LLMMessage(
                    role = "assistant",
                    content = response.content,
                    toolCalls = response.toolCalls
                )
                session.addMessage(assistantMsg)
                if (response.content.isNotEmpty()) {
                    emit(AgentStreamEvent.TTSHint(response.content))
                }

                // 执行工具
                for (tc in response.toolCalls) {
                    emit(AgentStreamEvent.ToolCallStart(tc.function.name, tc.function.arguments))
                    val args: Map<String, Any?> = try {
                        gson.fromJson(tc.function.arguments, Map::class.java) as? Map<String, Any?> ?: emptyMap()
                    } catch (e: Exception) {
                        mapOf("_raw" to tc.function.arguments)
                    }

                    val toolResult = withContext(Dispatchers.IO) {
                        ToolRegistry.execute(tc.function.name, args)
                    }
                    emit(AgentStreamEvent.ToolCallResult(tc.function.name, toolResult))
                    session.addMessage(LLMMessage(
                        role = "tool",
                        content = toolResult,
                        toolCallId = tc.id,
                        name = tc.function.name
                    ))
                }

                Log.i("Agent", "Round $roundNum: executed ${response.toolCalls.size} tool(s)")
            }
            // 循环结束仍在范围内 → 达到最大轮数
            if (session.messages.isNotEmpty() && session.messages.last().role == "tool") {
                hitMaxRounds = true
            }

            if (hitMaxRounds) {
                // 请求纯文本收尾
                emit(AgentStreamEvent.Status(statusMaxRounds))
                val recoveryMessages = listOf(
                    LLMMessage(role = "system", content = systemPrompt),
                    *session.messages.toTypedArray()
                )
                val recoveryResp = withContext(Dispatchers.IO) {
                    provider.chat(recoveryMessages, tools = null,
                        temperature = config.llmTemperature,
                        maxTokens = config.llmMaxTokens)
                }
                if (recoveryResp.content.isNotEmpty()) {
                    finalResponse = recoveryResp.content
                    session.addMessage(LLMMessage(role = "assistant", content = recoveryResp.content))
                    emit(AgentStreamEvent.TextDelta(recoveryResp.content))
                    emit(AgentStreamEvent.TTSHint(recoveryResp.content))
                }
            }
        } catch (e: Exception) {
            Log.e("Agent", "Agent loop failed for chatId=${context.chatId}", e)
            emit(AgentStreamEvent.Error("$statusErrorPrefix${e.message}"))
        }

        // 保存会话
        withContext(Dispatchers.IO) {
            sessionManager.save(session)
        }

        emit(AgentStreamEvent.Done(finalResponse))
    }.flowOn(Dispatchers.Default)
}

/**
 * Agent 流式事件类型。
 */
sealed class AgentStreamEvent {
    /** 文本增量（流式输出） */
    data class TextDelta(val text: String) : AgentStreamEvent()

    /** 状态消息 */
    data class Status(val message: String) : AgentStreamEvent()

    /** 工具调用开始 */
    data class ToolCallStart(val toolName: String, val arguments: String) : AgentStreamEvent()

    /** 工具调用结果 */
    data class ToolCallResult(val toolName: String, val result: String) : AgentStreamEvent()

    /** TTS 合成提示（携带需要朗读的文本） */
    data class TTSHint(val text: String) : AgentStreamEvent()

    /** 错误 */
    data class Error(val message: String) : AgentStreamEvent()

    /** 完成 */
    data class Done(val finalResponse: String) : AgentStreamEvent()
}
