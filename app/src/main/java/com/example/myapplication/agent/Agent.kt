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
    private fun buildSystemPrompt(userMessage: String): String {
        // 1. Echo 基础人格（echo_profile.md）
        val echoProfile = readEchoProfile()

        // 2. 用户画像摘要：取 MEMORY.md 前 600 字符 + Echo 记住的事实
        val fullMemory = FileStore.readWorkspaceFile("MEMORY.md")
        val profileSummary = if (fullMemory.isNotBlank()) {
            val lines = fullMemory.split("\n").filter { it.isNotBlank() }
            val summary = lines.take(10).joinToString("\n")
            if (summary.length > 600) summary.take(600) + "…" else summary
        } else ""

        // 3. 与本轮消息相关的记忆片段（通过 MemorySearch）
        val relevantMemory = buildString {
            if (userMessage.isNotBlank()) {
                val results = MemorySearch.keywordSearch(
                    FileStore.workspaceDir,
                    userMessage,
                    maxResults = 2
                )
                for (r in results) {
                    val shortSnippet = r.snippet.take(300)
                    append("\n[记忆: ${r.filePath}]\n$shortSnippet\n")
                }
            }
            // 最近记忆卡片
            val memoryDir = java.io.File(FileStore.workspaceDir, "memory")
            if (memoryDir.exists()) {
                val recent = memoryDir.listFiles()
                    ?.filter { it.extension == "md" && it.name != "MEMORY.md" }
                    ?.sortedByDescending { it.lastModified() }
                    ?.take(2) ?: emptyList()
                for (f in recent) {
                    val text = try { f.readText(Charsets.UTF_8).take(300) } catch (_: Exception) { "" }
                    if (text.isNotBlank()) {
                        append("\n[最近: ${f.name}]\n$text\n")
                    }
                }
            }
        }

        // 4. 工具描述
        val toolsDesc = ToolRegistry.getDescriptions()

        // 5. 运行时信息
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy年M月d日", Locale.CHINESE)
        val todayStr = sdf.format(Date(now))
        val sdf2 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())
        val timeStr = sdf2.format(Date(now))
        val runtime = """
## Runtime Info
- 今天是 $todayStr
- Current time: $timeStr
- Current Unix ms: $now
- Platform: Android

## Available Tools
$toolsDesc
        """.trimIndent()

        val parts = mutableListOf<String>()
        if (echoProfile.isNotBlank()) parts.add(echoProfile)
        if (profileSummary.isNotBlank()) parts.add("## 用户画像\n$profileSummary")
        if (relevantMemory.isNotBlank()) parts.add("## 相关记忆\n$relevantMemory")
        parts.add(runtime)

        return parts.joinToString("\n\n")
    }

    private fun readEchoProfile(): String {
        return try {
            val file = EchoFileStore.workspaceDir.resolve("echo_profile.md")
            if (file.exists()) file.readText(Charsets.UTF_8) else ""
        } catch (_: Exception) { "" }
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

    /** 从 MemoryRepository 取启用+已确认的画像注入系统提示 */
    private suspend fun buildStructuredProfileInjection(): String {
        return try {
            val repo = com.example.myapplication.data.repository.MemoryRepository()
            val profiles = repo.getEnabledProfiles().filter { it.status == "confirmed" }
            if (profiles.isEmpty()) return ""
            val cats = linkedMapOf(
                "identity" to "身份", "preference" to "偏好", "habit" to "习惯",
                "goal" to "目标", "project" to "项目", "relationship" to "关系"
            )
            buildString {
                append("\n\n## 结构化用户画像\n")
                for ((cat, label) in cats) {
                    val items = profiles.filter { it.category == cat }
                    if (items.isNotEmpty()) {
                        append("- $label: ${items.joinToString("；") { it.value }}\n")
                    }
                }
            }
        } catch (_: Exception) { "" }
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

    /** 添加用户消息（含记忆搜索前缀） */
    private fun appendUserMessage(session: Session, userMessage: String) {
        if (userMessage.isBlank()) return
        val prefix = memoryPrefix(userMessage)
        val fullContent = if (prefix.isNotBlank()) "$prefix\n$userMessage" else userMessage
        session.addMessage(LLMMessage(role = "user", content = fullContent))
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

        appendUserMessage(session, context.userMessage)

        val systemPrompt = buildSystemPrompt(context.userMessage) +
            withContext(Dispatchers.IO) { buildStructuredProfileInjection() }

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
}
