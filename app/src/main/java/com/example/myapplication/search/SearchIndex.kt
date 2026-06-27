package com.example.myapplication.search

import android.util.Log
import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.data.model.UserProfileMemory
import com.example.myapplication.data.repository.DiaryRepository
import com.example.myapplication.data.repository.LifeRecordRepository
import com.example.myapplication.data.repository.MemoryRepository
import com.example.myapplication.data.store.EchoFileStore
import com.example.myapplication.memory.FileStore
import com.example.myapplication.memory.MemoryMdParser
import com.example.myapplication.policy.MemoryRetrievalPolicy
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * 本地轻量关键词索引。
 *
 * 覆盖 UserProfileMemory / MemoryCard / LifeRecord / Diary / MEMORY.md confirmed，
 * 用 CJK bigram + ASCII 分词建 token → 源映射，支持增量更新和全量重建。
 *
 * 索引文件：echo/memories/search_index.json（原子写）
 */
object SearchIndex {

    private const val TAG = "SearchIndex"
    private val gson = Gson()

    data class IndexEntry(
        val sourceType: String,
        val sourceId: String,
        val updatedAt: Long,
        val tokens: List<String>,
        val snippet: String
    )

    private data class IndexContainer(
        val schemaVersion: Int = 1,
        val builtAt: Long,
        val entryCount: Int,
        val entries: List<IndexEntry>
    )

    data class IndexResult(
        val sourceType: String,
        val sourceId: String,
        val score: Double,
        val snippet: String,
        val updatedAt: Long
    )

    data class IndexStats(
        val entryCount: Int,
        val builtAt: Long,
        val perSource: Map<String, Int>
    )

    /** 内存中缓存的索引条目 */
    private var entries: List<IndexEntry> = emptyList()
    private var builtAt: Long = 0L

    /** 索引文件路径（首次访问时延迟计算，兼容测试环境） */
    private var testIndexFile: File? = null

    private fun indexFile(): File? {
        testIndexFile?.let { return it }
        return try {
            val dir = EchoFileStore.memoriesDir
            if (!dir.exists()) return null
            dir.resolve("search_index.json")
        } catch (e: Exception) {
            null
        }
    }

    /** 测试用：设置自定义索引文件路径。传入 null 恢复默认。 */
    fun setTestIndexFile(file: File?) {
        testIndexFile = file
    }

    /** 测试用：清空内存状态，重置文件路径。 */
    fun resetForTesting() {
        entries = emptyList()
        builtAt = 0L
        testIndexFile = null
    }

    // ── 全量重建 ──

    suspend fun rebuild() {
        val file = indexFile() ?: return
        val builder = mutableListOf<IndexEntry>()

        try {
            // LifeRecords
            try {
                LifeRecordRepository().getAll().forEach { r ->
                    builder.add(IndexEntry("life_record", r.id, r.createdAt,
                        tokenize(r.content), r.content.take(200)))
                }
            } catch (_: Exception) {}

            // Diaries
            try {
                DiaryRepository().getAll().forEach { d ->
                    val text = listOfNotNull(d.title, d.summary, d.diaryText).joinToString(" ")
                    builder.add(IndexEntry("diary", d.id, d.updatedAt,
                        tokenize(text), text.take(200)))
                }
            } catch (_: Exception) {}

            // MemoryCards — only confirmed
            try {
                MemoryRepository().getAllCards().filter { it.status == "confirmed" }.forEach { c ->
                    val text = "${c.quote} ${c.note}"
                    builder.add(IndexEntry("memory_card", c.id, c.createdAt,
                        tokenize(text), text.take(200)))
                }
            } catch (_: Exception) {}

            // UserProfileMemory — only enabled + confirmed
            try {
                MemoryRepository().getAllProfiles().filter {
                    it.enabled && it.status == "confirmed"
                }.forEach { p ->
                    val text = "${p.key} ${p.value}"
                    builder.add(IndexEntry("profile", p.id, p.updatedAt,
                        tokenize(text), text.take(200)))
                }
            } catch (_: Exception) {}

            // MEMORY.md confirmed section
            try {
                val md = FileStore.readWorkspaceFile("MEMORY.md")
                val confirmed = MemoryMdParser.readConfirmedSection(md)
                if (confirmed.isNotBlank()) {
                    builder.add(IndexEntry("memory_md", "MEMORY.md", System.currentTimeMillis(),
                        tokenize(confirmed), confirmed.take(200)))
                }
            } catch (_: Exception) {}

            val now = System.currentTimeMillis()
            entries = builder
            builtAt = now
            writeToFile(file, builder, now)

            val stats = getStats()
            Log.i(TAG, "Index rebuilt: ${stats.entryCount} entries across ${stats.perSource.size} sources")
        } catch (e: Exception) {
            Log.e(TAG, "Index rebuild failed", e)
        }
    }

    // ── 查询 ──

    fun query(queryText: String, maxResults: Int = 8): List<IndexResult> {
        if (queryText.isBlank() || entries.isEmpty()) return emptyList()

        val queryTokens = tokenize(queryText)
        if (queryTokens.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()

        return entries.mapNotNull { entry ->
            val score = matchScore(queryTokens, entry.tokens)
            if (score <= 0.0) return@mapNotNull null

            // Time decay
            val ageDays = (now - entry.updatedAt) / 86_400_000.0
            val decay = 1.0 / (1.0 + ageDays * 0.1)
            val finalScore = score * decay

            IndexResult(entry.sourceType, entry.sourceId, finalScore, entry.snippet, entry.updatedAt)
        }
            .sortedByDescending { it.score }
            .take(maxResults)
    }

    // ── 增量更新 ──

    fun upsert(sourceType: String, sourceId: String, text: String, updatedAt: Long) {
        if (sourceType == "memory_md" && text.length < 10) return
        val tokens = tokenize(text)
        val snippet = text.take(200)
        val entry = IndexEntry(sourceType, sourceId, updatedAt, tokens, snippet)

        val existing = entries.toMutableList()
        val idx = existing.indexOfFirst { it.sourceType == sourceType && it.sourceId == sourceId }
        if (idx >= 0) existing[idx] = entry else existing.add(entry)

        entries = existing
        builtAt = System.currentTimeMillis()
        val file = indexFile() ?: return
        writeToFile(file, entries, builtAt)
    }

    fun remove(sourceType: String, sourceId: String) {
        entries = entries.filter { !(it.sourceType == sourceType && it.sourceId == sourceId) }
        builtAt = System.currentTimeMillis()
        val file = indexFile() ?: return
        writeToFile(file, entries, builtAt)
    }

    // ── 状态 ──

    /** 如果索引过期或缺失，自动重建。返回 true 表示执行了重建。 */
    suspend fun rebuildIfStale(): Boolean {
        if (isStale()) {
            rebuild()
            return true
        }
        return false
    }

    fun isStale(): Boolean {
        if (entries.isEmpty()) return true
        val file = indexFile() ?: return true
        if (!file.exists()) return true
        // Check if any data file was modified after index was built
        val dataFiles = listOfNotNull(
            try { EchoFileStore.lifeRecordsFile } catch (_: Exception) { null },
            try { EchoFileStore.dailyDiariesFile } catch (_: Exception) { null },
            try { EchoFileStore.memoryCardsFile } catch (_: Exception) { null },
            try { EchoFileStore.userProfileFile } catch (_: Exception) { null }
        )
        return dataFiles.any { it.exists() && it.lastModified() > builtAt }
    }

    fun getStats(): IndexStats {
        val perSource = entries.groupBy { it.sourceType }.mapValues { it.value.size }
        return IndexStats(entries.size, builtAt, perSource)
    }

    fun entryCount(): Int = entries.size

    // ── 内部 ──

    private fun tokenize(text: String): List<String> =
        MemoryRetrievalPolicy.tokenize(text).distinct()

    private fun matchScore(queryTokens: List<String>, indexTokens: List<String>): Double {
        var score = 0.0
        for (qt in queryTokens) {
            if (qt in indexTokens) {
                score += qt.length.toDouble()
            }
        }
        return score
    }

    private fun writeToFile(file: File, entries: List<IndexEntry>, timestamp: Long) {
        try {
            file.parentFile?.mkdirs()
            val container = IndexContainer(
                schemaVersion = 1,
                builtAt = timestamp,
                entryCount = entries.size,
                entries = entries
            )
            val json = gson.toJson(container)
            val tmp = File(file.absolutePath + ".tmp")
            tmp.writeText(json, Charsets.UTF_8)
            tmp.renameTo(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write index", e)
        }
    }

    /** 从索引文件加载到内存（启动时调用） */
    fun loadFromDisk(): Boolean {
        val file = indexFile() ?: return false
        if (!file.exists()) return false
        return try {
            val json = file.readText(Charsets.UTF_8)
            val type = object : TypeToken<IndexContainer>() {}.type
            val container: IndexContainer = gson.fromJson(json, type)
            entries = container.entries
            builtAt = container.builtAt
            Log.i(TAG, "Index loaded: ${entries.size} entries, built at $builtAt")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load index, will rebuild", e)
            entries = emptyList()
            false
        }
    }
}
