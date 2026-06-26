package com.example.myapplication.memory

import android.util.Log
import com.example.myapplication.llm.LLMMessage
import com.example.myapplication.llm.LLMProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 会话管理器 — 基于 JSON 文件持久化（和 clawspeaker 原版一致）。
 */
class SessionManager(
    private val saveDir: File,
    private val maxContextTokens: () -> Int,
    private val compactionKeepMessages: () -> Int
) {
    private val cache = mutableMapOf<String, Session>()         // sessionId → Session
    private val chatToSession = mutableMapOf<String, String>()   // chatId → sessionId

    init {
        saveDir.mkdirs()
    }

    /** 获取或创建会话 */
    suspend fun getOrCreate(chatId: String, channel: String = "android", accountId: String = "local"): Session {
        chatToSession[chatId]?.let { sid ->
            cache[sid]?.let { return it }
        }

        // 从磁盘找最新的
        val existing = loadLatestSessionForChat(chatId)
        if (existing != null) {
            cacheSession(existing)
            return existing
        }

        // 创建新会话
        val sid = "$channel:$chatId:${System.currentTimeMillis()}"
        val session = Session(
            sessionId = sid,
            chatId = chatId,
            metadata = mutableMapOf("channel" to channel, "account_id" to accountId)
        )
        cacheSession(session)
        save(session)
        return session
    }

    /** 从磁盘恢复会话 */
    suspend fun resolveSession(chatId: String): Session? {
        chatToSession[chatId]?.let { sid ->
            cache[sid]?.let { return it }
        }
        return loadLatestSessionForChat(chatId)?.also { cacheSession(it) }
    }

    /** 保存会话到磁盘（原子写入：tmp → rename，rename 失败时 copyTo + delete 兜底） */
    suspend fun save(session: Session) {
        withContext(Dispatchers.IO) {
            cacheSession(session)
            val file = sessionFile(session.sessionId)
            file.parentFile?.mkdirs()
            val tmpFile = File(file.absolutePath + ".tmp")
            try {
                tmpFile.writeText(session.toJson(), Charsets.UTF_8)
                if (tmpFile.renameTo(file)) {
                    // 原子 rename 成功（同文件系统最快路径）
                    return@withContext
                }
                // rename 失败 → copyTo + delete 兜底
                Log.w("SessionManager", "renameTo failed for ${file.name}, falling back to copy+delete")
                tmpFile.copyTo(file, overwrite = true)
                tmpFile.delete()
            } catch (e: Exception) {
                Log.e("SessionManager", "Failed to save session ${session.sessionId}", e)
                tmpFile.delete()
            }
        }
    }

    /** 获取所有会话 */
    suspend fun getAllSessions(): List<Session> {
        return withContext(Dispatchers.IO) {
            val sessions = mutableListOf<Session>()
            val files: List<File> = saveDir.listFiles()?.filter { it.extension == "json" } ?: emptyList()
            for (file in files.sortedByDescending { it.lastModified() }) {
                val session = Session.fromJsonFile(file)
                if (session != null) {
                    sessions.add(session)
                    // 恢复缓存
                    if (session.sessionId !in cache) {
                        cache[session.sessionId] = session
                        chatToSession[session.chatId] = session.sessionId
                    }
                } else {
                    backupCorrupted(file)
                }
            }
            sessions.sortedByDescending { it.lastActive }
        }
    }

    /** 删除会话 */
    suspend fun delete(sessionId: String) {
        withContext(Dispatchers.IO) {
            val session = cache[sessionId]
            if (session != null) {
                chatToSession.remove(session.chatId)
                cache.remove(sessionId)
            }
            sessionFile(sessionId).delete()
        }
    }

    /** 上下文压缩 */
    suspend fun compact(session: Session, llmProvider: LLMProvider) {
        val keep = compactionKeepMessages()
        if (session.messages.size <= keep) return

        val toCompress = session.messages.dropLast(keep)
        val recent = session.messages.takeLast(keep)

        val summaryLines = toCompress.joinToString("\n") { msg ->
            when {
                msg.role == "tool" -> "[tool:${msg.name ?: "unknown"}] ${msg.content.take(600)}"
                msg.role == "assistant" && msg.toolCalls != null -> {
                    val names = msg.toolCalls!!.joinToString(", ") { it.function.name }
                    "[assistant] called $names. ${msg.content.take(200)}"
                }
                else -> "[${msg.role}] ${msg.content.take(400)}"
            }
        }

        try {
            val summaryResp = llmProvider.chat(
                messages = listOf(
                    LLMMessage(role = "system", content = "你是一个对话摘要器。只需输出摘要，不要添加额外评论。"),
                    LLMMessage(role = "user", content = "请将以下对话历史总结为一段简洁的摘要：\n\n$summaryLines")
                ),
                temperature = 0.3f,
                maxTokens = 1000
            )
            session.messages.clear()
            session.messages.add(LLMMessage(role = "system", content = "[对话历史摘要]\n${summaryResp.content}"))
            session.messages.addAll(recent)
            Log.i("SessionManager", "Compacted session ${session.sessionId}: ${toCompress.size}→1 summary")
        } catch (e: Exception) {
            Log.w("SessionManager", "Compaction failed: ${e.message}")
        }
    }

    // ── private ──

    private fun sessionFile(sessionId: String): File {
        return File(saveDir, "${sessionId.replace(":", "_")}.json")
    }

    private fun cacheSession(session: Session) {
        cache[session.sessionId] = session
        chatToSession[session.chatId] = session.sessionId
    }

    /** 坏文件重命名为 .bak 避免重复读取损坏数据 */
    private fun backupCorrupted(file: File) {
        try {
            val bak = File(file.absolutePath + ".bak.${System.currentTimeMillis()}")
            file.renameTo(bak)
            Log.w("SessionManager", "Corrupted session file backed up: ${file.name}")
        } catch (_: Exception) {}
    }

    private fun loadLatestSessionForChat(chatId: String): Session? {
        var best: Session? = null
        var bestLA = -1L
        val files: List<File> = saveDir.listFiles()?.filter { it.extension == "json" } ?: emptyList()
        for (file in files) {
            val session = Session.fromJsonFile(file)
            if (session == null) {
                backupCorrupted(file)
                continue
            }
            if (session.chatId == chatId && session.lastActive > bestLA) {
                best = session
                bestLA = session.lastActive
            }
        }
        return best
    }
}
