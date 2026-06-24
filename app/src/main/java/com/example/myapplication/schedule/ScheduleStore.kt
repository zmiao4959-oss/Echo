package com.example.myapplication.schedule

import com.example.myapplication.memory.FileStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 定时任务持久化存储，基于 JSON 文件。
 */
object ScheduleStore {

    private const val FILENAME = "schedules.json"
    private val gson = Gson()

    private val file: File
        get() = File(FileStore.workspaceDir, FILENAME)

    /** 列出所有定时任务 */
    suspend fun listAll(): List<ScheduledTask> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        try {
            val json = file.readText(Charsets.UTF_8)
            val type = object : TypeToken<List<SerializableSchedule>>() {}.type
            val list: List<SerializableSchedule> = gson.fromJson(json, type)
            list.map { s ->
                ScheduledTask(
                    id = s.id,
                    hour = s.hour,
                    minute = s.minute,
                    daysOfWeek = s.daysOfWeek.toSet(),
                    prompt = s.prompt,
                    enabled = s.enabled,
                    createdAt = s.createdAt
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 保存所有定时任务 */
    suspend fun saveAll(tasks: List<ScheduledTask>) = withContext(Dispatchers.IO) {
        try {
            FileStore.ensureWorkspaceDir()
            val list = tasks.map { task ->
                SerializableSchedule(
                    id = task.id,
                    hour = task.hour,
                    minute = task.minute,
                    daysOfWeek = task.daysOfWeek.toList(),
                    prompt = task.prompt,
                    enabled = task.enabled,
                    createdAt = task.createdAt
                )
            }
            file.writeText(gson.toJson(list), Charsets.UTF_8)
        } catch (e: Exception) {
            // silently fail
        }
    }

    /** 添加任务 */
    suspend fun add(task: ScheduledTask) {
        val all = listAll().toMutableList()
        all.add(task)
        saveAll(all)
    }

    /** 更新任务 */
    suspend fun update(task: ScheduledTask) {
        val all = listAll().toMutableList()
        val idx = all.indexOfFirst { it.id == task.id }
        if (idx >= 0) {
            all[idx] = task
            saveAll(all)
        }
    }

    /** 删除任务 */
    suspend fun delete(id: String) {
        val all = listAll().toMutableList()
        all.removeAll { it.id == id }
        saveAll(all)
    }
}
