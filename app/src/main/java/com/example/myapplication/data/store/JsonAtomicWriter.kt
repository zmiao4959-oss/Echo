package com.example.myapplication.data.store

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * 原子化 JSON 读写工具。
 *
 * 写操作：
 *   1. 先写入 .tmp 文件
 *   2. 再 rename 到正式文件（原子操作）
 *
 * 读操作：
 *   1. 文件不存在 → 返回空列表
 *   2. JSON 解析失败 → 备份坏文件为 .bak.<timestamp>，返回空列表，绝不抛异常
 */
object JsonAtomicWriter {

    @PublishedApi internal val TAG = "JsonAtomicWriter"
    @PublishedApi internal val gson = Gson()

    /**
     * 读取 JSON 文件中的 items 列表。
     * 文件结构：{ "schemaVersion": N, "items": [...] }
     *
     * @param file   JSON 文件
     * @return items 列表，读取失败返回空列表
     */
    inline fun <reified T> readItems(file: File): List<T> {
        if (!file.exists()) {
            return emptyList()
        }
        return try {
            val json = file.readText(Charsets.UTF_8)
            val type = object : TypeToken<ItemContainer<T>>() {}.type
            val container: ItemContainer<T> = gson.fromJson(json, type)
            container.items ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read ${file.name}, backing up corrupted file", e)
            backupCorrupted(file)
            emptyList()
        }
    }

    /**
     * 将 items 列表写入 JSON 文件（原子写入）。
     *
     * @param file          JSON 文件
     * @param items         数据列表
     * @param schemaVersion schema 版本号（用于未来迁移）
     */
    fun <T> writeItems(file: File, items: List<T>, schemaVersion: Int = 1) {
        file.parentFile?.mkdirs()

        val container = ItemContainer(schemaVersion = schemaVersion, items = items)
        val json = gson.toJson(container)

        val tmpFile = File(file.absolutePath + ".tmp")
        try {
            tmpFile.writeText(json, Charsets.UTF_8)
            // 原子 rename（同一文件系统内）
            tmpFile.renameTo(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write ${file.name}", e)
            tmpFile.delete()
        }
    }

    /**
     * 只读取文件的 schemaVersion，不反序列化 items。
     * @return schemaVersion，文件不存在或损坏返回 0
     */
    fun readSchemaVersion(file: File): Int {
        if (!file.exists()) return 0
        return try {
            val json = file.readText(Charsets.UTF_8)
            val obj = gson.fromJson(json, com.google.gson.JsonObject::class.java)
            obj?.get("schemaVersion")?.asInt ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 备份已损坏的 JSON 文件。
     */
    @PublishedApi internal fun backupCorrupted(file: File) {
        try {
            val bakFile = File("${file.absolutePath}.bak.${System.currentTimeMillis()}")
            file.renameTo(bakFile)
            Log.w(TAG, "Corrupted file backed up to ${bakFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to backup corrupted file ${file.name}", e)
        }
    }

    /**
     * JSON 文件顶层容器结构。
     */
    @PublishedApi internal data class ItemContainer<T>(
        val schemaVersion: Int,
        val items: List<T>?
    )
}
