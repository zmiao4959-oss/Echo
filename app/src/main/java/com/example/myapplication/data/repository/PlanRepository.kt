package com.example.myapplication.data.repository

import com.example.myapplication.data.model.EchoPlan
import com.example.myapplication.data.store.EchoFileStore
import com.example.myapplication.data.store.JsonAtomicWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * EchoPlan 仓库 —— 管理规划项 JSON 持久化。
 */
class PlanRepository {

    private val file = EchoFileStore.plansFile

    /** 读取所有规划 */
    suspend fun getAll(): List<EchoPlan> = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems(file)
    }

    /** 获取所有启用的规划 */
    suspend fun getAllEnabled(): List<EchoPlan> = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems<EchoPlan>(file).filter { it.enabled }
    }

    /** 根据 id 获取规划 */
    suspend fun getById(id: String): EchoPlan? = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems<EchoPlan>(file).find { it.id == id }
    }

    /** 按类型获取规划 */
    suspend fun getByType(type: String): List<EchoPlan> = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems<EchoPlan>(file).filter { it.type == type }
    }

    /** 新增规划 */
    suspend fun add(plan: EchoPlan) = withContext(Dispatchers.IO) {
        val items = JsonAtomicWriter.readItems<EchoPlan>(file).toMutableList()
        items.add(plan)
        JsonAtomicWriter.writeItems(file, items)
    }

    /** 更新规划 */
    suspend fun update(plan: EchoPlan) = withContext(Dispatchers.IO) {
        val items = JsonAtomicWriter.readItems<EchoPlan>(file).toMutableList()
        val idx = items.indexOfFirst { it.id == plan.id }
        if (idx >= 0) {
            items[idx] = plan
            JsonAtomicWriter.writeItems(file, items)
        }
    }

    /** 删除规划 */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val items = JsonAtomicWriter.readItems<EchoPlan>(file).filter { it.id != id }
        JsonAtomicWriter.writeItems(file, items)
    }

    /** 更新规划的最后触发时间 */
    suspend fun updateLastTriggered(id: String, timestamp: Long) = withContext(Dispatchers.IO) {
        val items = JsonAtomicWriter.readItems<EchoPlan>(file).toMutableList()
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) {
            items[idx] = items[idx].copy(lastTriggeredAt = timestamp)
            JsonAtomicWriter.writeItems(file, items)
        }
    }

    /** 批量获取待触发的规划（triggerAt <= now 且 enabled 且未触发过或距离上次触发已过重复周期） */
    suspend fun getPendingPlans(now: Long): List<EchoPlan> = withContext(Dispatchers.IO) {
        val all = JsonAtomicWriter.readItems<EchoPlan>(file).filter { it.enabled }
        all.filter { plan ->
            if (plan.triggerAt > now) return@filter false
            if (plan.lastTriggeredAt == null) return@filter true
            // 如果有重复规则且上次已触发，检查是否已过重复间隔
            plan.triggerAt > (plan.lastTriggeredAt ?: 0L)
        }
    }
}
