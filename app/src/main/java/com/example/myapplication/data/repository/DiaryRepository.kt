package com.example.myapplication.data.repository

import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.store.EchoFileStore
import com.example.myapplication.data.store.JsonAtomicWriter
import com.example.myapplication.search.SearchIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DailyDiary 仓库 —— 管理每日日记 JSON 持久化。
 */
class DiaryRepository {

    private val file = EchoFileStore.dailyDiariesFile

    /** 读取所有日记 */
    suspend fun getAll(): List<DailyDiary> = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems(file)
    }

    /** 获取某一天的日记 */
    suspend fun getByDate(date: String): DailyDiary? = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems<DailyDiary>(file).find { it.date == date }
    }

    /** 根据 id 获取日记 */
    suspend fun getById(id: String): DailyDiary? = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems<DailyDiary>(file).find { it.id == id }
    }

    /** 新增日记 */
    suspend fun add(diary: DailyDiary) = withContext(Dispatchers.IO) {
        val items = JsonAtomicWriter.readItems<DailyDiary>(file).toMutableList()
        items.removeAll { it.date == diary.date }
        items.add(diary)
        JsonAtomicWriter.writeItems(file, items)
        val text = listOfNotNull(diary.title, diary.summary, diary.diaryText).joinToString(" ")
        SearchIndex.upsert("diary", diary.id, text, diary.updatedAt)
    }

    /** 更新日记 */
    suspend fun update(diary: DailyDiary) = withContext(Dispatchers.IO) {
        val items = JsonAtomicWriter.readItems<DailyDiary>(file).toMutableList()
        val idx = items.indexOfFirst { it.id == diary.id }
        if (idx >= 0) {
            items[idx] = diary
            JsonAtomicWriter.writeItems(file, items)
            val text = listOfNotNull(diary.title, diary.summary, diary.diaryText).joinToString(" ")
            SearchIndex.upsert("diary", diary.id, text, diary.updatedAt)
        }
    }

    /** 删除日记 */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val items = JsonAtomicWriter.readItems<DailyDiary>(file).filter { it.id != id }
        JsonAtomicWriter.writeItems(file, items)
        SearchIndex.remove("diary", id)
    }

    /** 获取某日期范围内的日记 */
    suspend fun getByDateRange(fromDate: String, toDate: String): List<DailyDiary> = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems<DailyDiary>(file).filter { it.date in fromDate..toDate }
    }

    /** 按标签搜索日记 */
    suspend fun getByTag(tag: String): List<DailyDiary> = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems<DailyDiary>(file).filter { tag in it.tags }
    }

    /** 按情绪搜索日记 */
    suspend fun getByMood(mood: String): List<DailyDiary> = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems<DailyDiary>(file).filter { it.mood.contains(mood, ignoreCase = true) }
    }

    /** 检查某天是否已有日记 */
    suspend fun hasDiary(date: String): Boolean = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems<DailyDiary>(file).any { it.date == date }
    }
}
