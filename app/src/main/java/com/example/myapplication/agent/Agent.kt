package com.example.myapplication.agent

import android.util.Log
import com.example.myapplication.MyApplication
import com.example.myapplication.config.AppConfig
import com.example.myapplication.llm.LLMMessage
import com.example.myapplication.llm.LLMProvider
import com.example.myapplication.llm.LLMResponse
import com.example.myapplication.llm.ProviderFactory
import com.example.myapplication.data.store.EchoFileStore
import com.example.myapplication.memory.*
import com.example.myapplication.tools.ToolRegistry
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    /** 构建 System Prompt（Echo 人格 + 用户画像摘要 + 工具 + 运行时） */
    private fun buildSystemPrompt(memoryCtx: MemoryContextBuilder.MemoryContext): String {
        // 1. Echo 人格：workspace 文件（SOUL.md / IDENTITY.md / AGENTS.md）
        // echo_profile.md 已废弃，内容由 SOUL.md + AGENTS.md 覆盖
        // MEMORY.md 由 MemoryContextBuilder 处理（仅 ✅ 区），此处不加载
        val personalityPrompt = try {
            val fs = com.example.myapplication.memory.FileStore
            listOf("SOUL.md", "IDENTITY.md", "AGENTS.md", "USER.md")
                .map { fs.readWorkspaceFile(it) }
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
        } catch (_: Exception) { "" }

        // 2. 工具描述
        val toolsDesc = ToolRegistry.getDescriptions()

        // 3. 运行时信息
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy年M月d日", Locale.CHINESE)
        val todayStr = sdf.format(Date(now))
        val sdf2 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())
        val timeStr = sdf2.format(Date(now))
        // 自然语言时间提示
        val dayOfWeek = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
        val weekday = dayOfWeek[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val timeOfDay = when {
            hour in 0..5 -> "凌晨"
            hour in 6..8 -> "早上"
            hour in 9..11 -> "上午"
            hour in 12..13 -> "中午"
            hour in 14..17 -> "下午"
            hour in 18..21 -> "晚上"
            else -> "深夜"
        }
        val naturalTime = "${timeOfDay}${hour}点${cal.get(java.util.Calendar.MINUTE)}分"
        val weatherCity = MyApplication.instance.appConfig.weatherCity
        val weatherLine = if (weatherCity.isNotBlank()) "\n- 用户天气城市: $weatherCity" else ""
        val runtime = """
## Runtime Info
- 今天是 $todayStr $weekday $naturalTime
- 完整时间: $timeStr
- Unix ms: $now
- Platform: Android$weatherLine

## Available Tools
$toolsDesc
        """.trimIndent()

        val parts = mutableListOf<String>()
        if (personalityPrompt.isNotBlank()) parts.add(personalityPrompt)
        if (memoryCtx.profileSummary.isNotBlank()) parts.add("## 用户画像\n${memoryCtx.profileSummary}")
        if (memoryCtx.relevantMemorySection.isNotBlank()) parts.add("## 相关记忆\n${memoryCtx.relevantMemorySection}")
        parts.add(runtime)

        return parts.joinToString("\n\n")
    }

    private fun createProvider(): LLMProvider = ProviderFactory.createLLMProvider()

    // ── 私有：Agent Loop 子步骤 ──

    /** 解析或创建会话 */
    private suspend fun resolveOrCreateSession(context: AgentContext): Session {
        return withContext(Dispatchers.IO) {
            sessionManager.resolveSession(context.chatId)
                ?: sessionManager.getOrCreate(context.chatId, context.channel, context.accountId)
        }
    }

    /** 添加用户消息。检索结果已在 System Prompt 的 ## 相关记忆 中，此处不再重复注入。 */
    private fun appendUserMessage(session: Session, userMessage: String, memoryCtx: MemoryContextBuilder.MemoryContext) {
        if (userMessage.isBlank()) return
        // 记忆前缀不再注入用户消息，避免与 System Prompt 中的 ## 相关记忆 重复，
        // 也防止历史消息被检索结果污染
        session.addMessage(LLMMessage(role = "user", content = userMessage))
    }

    /** 执行单轮 LLM 调用（流式首轮，非流式后续轮） */
    private suspend fun executeSingleRound(
        provider: LLMProvider,
        messages: List<LLMMessage>,
        tools: List<Map<String, Any>>?,
        config: AppConfig,
        useStream: Boolean,
        onTextDelta: suspend (String) -> Unit,
        onStatus: suspend (String) -> Unit
    ): LLMResponse {
        return if (useStream) {
            var fullContent = ""
            var finishedToolCalls: List<LLMMessage.ToolCall> = emptyList()
            var finishReason = "stop"
            var usage = emptyMap<String, Int>()

            provider.chatStream(messages, tools,
                temperature = config.llmTemperature,
                maxTokens = config.llmMaxTokens
            ).collect { chunk ->
                if (chunk.deltaContent.isNotEmpty()) {
                    fullContent += chunk.deltaContent
                    onTextDelta(chunk.deltaContent)
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
            onStatus(statusExecutingTools)
            withContext(Dispatchers.IO) {
                provider.chat(messages, tools,
                    temperature = config.llmTemperature,
                    maxTokens = config.llmMaxTokens)
            }
        }
    }

    /** 执行工具调用列表，返回每个工具的执行结果 */
    private suspend fun executeTools(
        toolCalls: List<LLMMessage.ToolCall>,
        session: Session,
        onToolStart: suspend (String, String) -> Unit,
        onToolResult: suspend (String, String) -> Unit
    ) {
        for (tc in toolCalls) {
            onToolStart(tc.function.name, tc.function.arguments)
            @Suppress("UNCHECKED_CAST")
            val args: Map<String, Any?> = try {
                gson.fromJson(tc.function.arguments, Map::class.java) as? Map<String, Any?> ?: emptyMap()
            } catch (_: Exception) {
                mapOf("_raw" to tc.function.arguments)
            }

            val result = withContext(Dispatchers.IO) {
                ToolRegistry.execute(tc.function.name, args)
            }
            onToolResult(tc.function.name, result)
            session.addMessage(LLMMessage(
                role = "tool",
                content = result,
                toolCallId = tc.id,
                name = tc.function.name
            ))
        }
    }

    /** 达到最大轮数时做一次性纯文本兜底回复 */
    private suspend fun handleMaxRoundsRecovery(
        provider: LLMProvider,
        session: Session,
        systemPrompt: String,
        config: AppConfig,
        onTextDelta: suspend (String) -> Unit,
        onTtsHint: suspend (String) -> Unit
    ): String? {
        val messages = listOf(
            LLMMessage(role = "system", content = systemPrompt),
            *session.messages.toTypedArray()
        )
        val recoveryResp = withContext(Dispatchers.IO) {
            provider.chat(messages, tools = null,
                temperature = config.llmTemperature,
                maxTokens = config.llmMaxTokens)
        }
        if (recoveryResp.content.isNotEmpty()) {
            session.addMessage(LLMMessage(role = "assistant", content = recoveryResp.content))
            onTextDelta(recoveryResp.content)
            onTtsHint(recoveryResp.content)
            return recoveryResp.content
        }
        return null
    }

    /**
     * 核心方法：接收用户消息，运行 Agent Loop，返回 Flow 流式输出。
     */
    fun processMessageStream(
        context: AgentContext
    ): Flow<AgentStreamEvent> = flow {
        val config = MyApplication.instance.appConfig
        val provider = createProvider()
        val session = resolveOrCreateSession(context)

        val memoryCtx = MemoryContextBuilder.build(context.userMessage)

        if (memoryCtx.sources.isNotEmpty()) {
            emit(AgentStreamEvent.MemoryRef(memoryCtx.sources))
        }

        appendUserMessage(session, context.userMessage, memoryCtx)

        val systemPrompt = buildSystemPrompt(memoryCtx) +
            memoryCtx.structuredProfileInjection

        // Debug: 保存完整对话上下文到 workspace，可直接在 App 内「工作区文件」查看
        try {
            val debugFile = com.example.myapplication.memory.FileStore.workspaceDir.resolve("_last_prompt.md")
            debugFile.writeText(buildString {
                appendLine("# 完整对话上下文（AI 实际收到的）")
                appendLine()
                appendLine("## System Prompt")
                appendLine("```")
                appendLine(systemPrompt)
                appendLine("```")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## 消息历史（共 ${session.messages.size} 条）")
                appendLine()
                for ((i, msg) in session.messages.withIndex()) {
                    val roleLabel = when (msg.role) {
                        "user" -> "👤 用户"
                        "assistant" -> "🤖 Echo"
                        "tool" -> "🔧 工具结果 (${msg.name ?: ""})"
                        "system" -> "⚙️ 系统"
                        else -> msg.role
                    }
                    appendLine("### [$i] $roleLabel")
                    if (msg.role == "assistant" && !msg.toolCalls.isNullOrEmpty()) {
                        appendLine("_调用了工具: ${msg.toolCalls.joinToString { it.function.name }}_")
                    }
                    appendLine(msg.content)
                    appendLine()
                }
            }, Charsets.UTF_8)
        } catch (_: Exception) {}

        val tools = ToolRegistry.listForLLM()
        val toolList = tools.ifEmpty { null }
        val maxRounds = config.maxToolRounds
        var finalResponse = ""

        try {
            for (roundNum in 1..maxRounds) {
                if (session.shouldCompact(config.maxContextTokens)) {
                    emit(AgentStreamEvent.Status(statusCompacting))
                    withContext(Dispatchers.IO) {
                        sessionManager.compact(session, provider)
                    }
                }

                val messages = listOf(LLMMessage(role = "system", content = systemPrompt)) + session.messages
                val response = executeSingleRound(
                    provider, messages, toolList, config, useStream = (roundNum == 1),
                    onTextDelta = { emit(AgentStreamEvent.TextDelta(it)) },
                    onStatus = { emit(AgentStreamEvent.Status(it)) }
                )

                if (response.content.isNotEmpty()) finalResponse = response.content

                if (response.toolCalls.isEmpty()) {
                    if (response.content.isNotEmpty()) {
                        session.addMessage(LLMMessage(role = "assistant", content = response.content))
                        emit(AgentStreamEvent.TTSHint(response.content))
                    }
                    break
                }

                val assistantMsg = LLMMessage(
                    role = "assistant",
                    content = response.content,
                    toolCalls = response.toolCalls
                )
                session.addMessage(assistantMsg)
                if (response.content.isNotEmpty()) {
                    emit(AgentStreamEvent.TTSHint(response.content))
                }

                executeTools(
                    response.toolCalls, session,
                    onToolStart = { name, args -> emit(AgentStreamEvent.ToolCallStart(name, args)) },
                    onToolResult = { name, result -> emit(AgentStreamEvent.ToolCallResult(name, result)) }
                )

                Log.i("Agent", "Round $roundNum: executed ${response.toolCalls.size} tool(s)")
            }

            // 达到最大轮数 → 纯文本兜底
            if (session.messages.isNotEmpty() && session.messages.last().role == "tool") {
                emit(AgentStreamEvent.Status(statusMaxRounds))
                val recovery = handleMaxRoundsRecovery(
                    provider, session, systemPrompt, config,
                    onTextDelta = { emit(AgentStreamEvent.TextDelta(it)) },
                    onTtsHint = { emit(AgentStreamEvent.TTSHint(it)) }
                )
                if (recovery != null) finalResponse = recovery
            }
        } catch (e: Exception) {
            Log.e("Agent", "Agent loop failed for chatId=${context.chatId}", e)
            emit(AgentStreamEvent.Error("$statusErrorPrefix${e.message}"))
        }

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

    /** 本轮使用的记忆来源（供 UI 透明展示） */
    data class MemoryRef(val sources: List<MemoryContextBuilder.MemorySource>) : AgentStreamEvent()
}
