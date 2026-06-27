package com.example.myapplication.data.repository

import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.data.store.EchoFileStore
import com.example.myapplication.data.store.JsonAtomicWriter
import com.example.myapplication.search.SearchIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LifeRecord 仓库 —— 管理生活片段 JSON 持久化。
 */
class LifeRecordRepository {

    private val file = EchoFileStore.lifeRecordsFile

    /** 读取所有 LifeRecord */
    suspend fun getAll(): List<LifeRecord> = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems(file)
    }

    /** 获取某一天的所有 LifeRecord */
    suspend fun getByDate(date: String): List<LifeRecord> = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems<LifeRecord>(file).filter { it.date == date }
    }

    /** 根据 id 获取单条记录 */
    suspend fun getById(id: String): LifeRecord? = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems<LifeRecord>(file).find { it.id == id }
    }

    /** 新增一条 LifeRecord */
    suspend fun add(record: LifeRecord) = withContext(Dispatchers.IO) {
        val items = JsonAtomicWriter.readItems<LifeRecord>(file).toMutableList()
        items.add(record)
        JsonAtomicWriter.writeItems(file, items)
        SearchIndex.upsert("life_record", record.id, record.content, record.createdAt)
    }

    /** 更新一条 LifeRecord */
    suspend fun update(record: LifeRecord) = withContext(Dispatchers.IO) {
        val items = JsonAtomicWriter.readItems<LifeRecord>(file).toMutableList()
        val idx = items.indexOfFirst { it.id == record.id }
        if (idx >= 0) {
            items[idx] = record
            JsonAtomicWriter.writeItems(file, items)
            SearchIndex.upsert("life_record", record.id, record.content, record.createdAt)
        }
    }

    /** 删除一条 LifeRecord */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val items = JsonAtomicWriter.readItems<LifeRecord>(file).filter { it.id != id }
        JsonAtomicWriter.writeItems(file, items)
        SearchIndex.remove("life_record", id)
    }

    /** 获取某日期范围内的 LifeRecord */
    suspend fun getByDateRange(fromDate: String, toDate: String): List<LifeRecord> = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems<LifeRecord>(file).filter { it.date in fromDate..toDate }
    }

    /** 按标签搜索 */
    suspend fun getByTag(tag: String): List<LifeRecord> = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems<LifeRecord>(file).filter { tag in it.tags }
    }
}
