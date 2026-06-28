package com.example.myapplication.data.store

import android.util.Log
import com.example.myapplication.policy.SemanticRetrievalEngine
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Embedding 缓存持久化 — 将 [SemanticRetrievalEngine] 的内存缓存写入磁盘。
 *
 * 文件：echo/memories/embedding_cache.json
 * 格式：JSON 数组，每条记录含 sourceType/sourceId/textHash/embedding(List<Float>)/cachedAt
 *
 * 写入策略：每次检索后保存（[save] 原子写 tmp → rename）。
 */
object EmbeddingCacheStore {

    private const val TAG = "EmbeddingCacheStore"
    private val gson = Gson()

    /** 可序列化的缓存条目（FloatArray → List<Float>） */
    private data class JsonEntry(
        val sourceType: String,
        val sourceId: String,
        val textHash: String,
        val embedding: List<Float>?,
        val cachedAt: Long
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
     * 保存内存缓存到磁盘（原子写）。
     */
    fun save(entries: List<SemanticRetrievalEngine.EmbeddingCacheEntry>) {
        val file = cacheFile() ?: return
        try {
            val jsonEntries = entries.map { entry ->
                JsonEntry(
                    sourceType = entry.sourceType,
                    sourceId = entry.sourceId,
                    textHash = entry.textHash,
                    embedding = entry.embedding?.toList(),
                    cachedAt = entry.cachedAt
                )
            }
            val json = gson.toJson(jsonEntries)
            val tmp = File(file.absolutePath + ".tmp")
            tmp.writeText(json, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                // rename 失败时 copyTo 兜底
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            Log.d(TAG, "Cache saved: ${entries.size} entries, ${json.length} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save embedding cache", e)
        }
    }

    /**
     * 从磁盘加载缓存。
     * @return 缓存条目列表，文件不存在或损坏时返回空列表。
     */
    fun load(): List<SemanticRetrievalEngine.EmbeddingCacheEntry> {
        val file = cacheFile() ?: return emptyList()
        if (!file.exists()) return emptyList()

        return try {
            val json = file.readText(Charsets.UTF_8)
            val type = object : TypeToken<List<JsonEntry>>() {}.type
            val jsonEntries: List<JsonEntry> = gson.fromJson(json, type)

            val entries = jsonEntries.mapNotNull { je ->
                try {
                    SemanticRetrievalEngine.EmbeddingCacheEntry(
                        sourceType = je.sourceType,
                        sourceId = je.sourceId,
                        textHash = je.textHash,
                        embedding = je.embedding?.toFloatArray(),
                        cachedAt = je.cachedAt
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping corrupt cache entry: ${je.sourceType}:${je.sourceId}", e)
                    null
                }
            }

            Log.i(TAG, "Cache loaded: ${entries.size} entries")
            entries
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load embedding cache, starting fresh", e)
            emptyList()
        }
    }
}
