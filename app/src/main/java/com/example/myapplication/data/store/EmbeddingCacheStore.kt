package com.example.myapplication.data.store

import android.util.Log
import com.example.myapplication.policy.SemanticRetrievalEngine
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Embedding 缓存持久化 — LRU + model 隔离 + 损坏恢复。
 *
 * 文件：echo/memories/embedding_cache.json
 * 容量上限：1000 条，超限时按 lastUsedAt 淘汰。
 * 安全：不保存原始完整文本（仅 hash + 前 100 字符摘要），不含 API Key。
 * 隔离：model / provider 变更时旧缓存不可复用。
 */
object EmbeddingCacheStore {

    private const val TAG = "EmbeddingCacheStore"
    private const val MAX_ENTRIES = 1000
    private val gson = Gson()

    /** 可序列化的缓存条目 */
    private data class JsonEntry(
        val sourceType: String,
        val sourceId: String,
        val textHash: String,
        val embedding: List<Float>?,
        val cachedAt: Long,
        val model: String = "",
        val provider: String = "",
        val summary: String = "",
        val lastUsedAt: Long = 0L
    )

    private fun cacheFile(): File? {
        return try {
            val dir = EchoFileStore.memoriesDir
            if (!dir.exists()) dir.mkdirs()
            dir.resolve("embedding_cache.json")
        } catch (e: Exception) {
            Log.w(TAG, "Cannot resolve cache file", e)
            null
        }
    }

    /**
     * 保存缓存到磁盘（原子写 + LRU 裁剪）。
     * @param currentModel 当前使用的 model，用于后续加载时校验
     * @param currentProvider 当前使用的 provider 标识
     */
    fun save(
        entries: List<SemanticRetrievalEngine.EmbeddingCacheEntry>,
        currentModel: String = "",
        currentProvider: String = ""
    ) {
        val file = cacheFile() ?: return
        try {
            // LRU 裁剪：保留最近使用的 MAX_ENTRIES 条
            val trimmed = entries
                .sortedByDescending { it.lastUsedAt }
                .take(MAX_ENTRIES)

            val jsonEntries = trimmed.map { entry ->
                JsonEntry(
                    sourceType = entry.sourceType,
                    sourceId = entry.sourceId,
                    textHash = entry.textHash,
                    embedding = entry.embedding?.toList(),
                    cachedAt = entry.cachedAt,
                    model = entry.model.ifBlank { currentModel },
                    provider = entry.provider.ifBlank { currentProvider },
                    summary = entry.summary,
                    lastUsedAt = entry.lastUsedAt
                )
            }
            val json = gson.toJson(jsonEntries)
            val tmp = File(file.absolutePath + ".tmp")
            tmp.writeText(json, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }

            val dropped = entries.size - trimmed.size
            Log.d(TAG, "Cache saved: ${trimmed.size} entries${if (dropped > 0) " ($dropped evicted)" else ""}, ${json.length} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save embedding cache", e)
        }
    }

    /**
     * 从磁盘加载缓存。自动过滤与当前 model/provider 不匹配的条目。
     * 文件损坏时重建。
     *
     * @param currentModel 当前 model，为空时不过滤
     * @param currentProvider 当前 provider，为空时不过滤
     * @return 有效的缓存条目列表
     */
    fun load(
        currentModel: String = "",
        currentProvider: String = ""
    ): List<SemanticRetrievalEngine.EmbeddingCacheEntry> {
        val file = cacheFile() ?: return emptyList()
        if (!file.exists()) return emptyList()

        return try {
            val json = file.readText(Charsets.UTF_8)
            val type = object : TypeToken<List<JsonEntry>>() {}.type
            val jsonEntries: List<JsonEntry> = gson.fromJson(json, type)

            val entries = jsonEntries.mapNotNull { je ->
                try {
                    // model/provider 隔离：不匹配的旧缓存跳过
                    if (currentModel.isNotBlank() && je.model.isNotBlank() && je.model != currentModel) {
                        Log.d(TAG, "Skipping cache entry with mismatched model: ${je.sourceType}:${je.sourceId} (${je.model} != $currentModel)")
                        return@mapNotNull null
                    }
                    if (currentProvider.isNotBlank() && je.provider.isNotBlank() && je.provider != currentProvider) {
                        Log.d(TAG, "Skipping cache entry with mismatched provider: ${je.sourceType}:${je.sourceId} (${je.provider} != $currentProvider)")
                        return@mapNotNull null
                    }

                    SemanticRetrievalEngine.EmbeddingCacheEntry(
                        sourceType = je.sourceType,
                        sourceId = je.sourceId,
                        textHash = je.textHash,
                        embedding = je.embedding?.toFloatArray(),
                        cachedAt = je.cachedAt,
                        model = je.model,
                        provider = je.provider,
                        summary = je.summary,
                        lastUsedAt = je.lastUsedAt
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping corrupt cache entry: ${je.sourceType}:${je.sourceId}", e)
                    null
                }
            }

            Log.i(TAG, "Cache loaded: ${entries.size} entries (${jsonEntries.size - entries.size} filtered)")
            entries
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load embedding cache, rebuilding", e)
            // 损坏时删除旧文件
            try { file.delete() } catch (_: Exception) {}
            emptyList()
        }
    }

    /**
     * 清空所有缓存（文件 + 内存）。
     */
    fun clear() {
        try {
            val file = cacheFile()
            if (file?.exists() == true) {
                file.delete()
                Log.i(TAG, "Embedding cache cleared")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear cache", e)
        }
    }

    /**
     * 获取缓存文件大小（字节），文件不存在返回 -1。
     */
    fun fileSize(): Long {
        val file = cacheFile() ?: return -1
        return if (file.exists()) file.length() else 0
    }
}
