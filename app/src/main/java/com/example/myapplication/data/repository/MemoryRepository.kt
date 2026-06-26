package com.example.myapplication.data.repository

import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.data.model.UserProfileMemory
import com.example.myapplication.data.store.EchoFileStore
import com.example.myapplication.data.store.JsonAtomicWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MemoryRepository —— 管理回忆卡片与用户画像 JSON 持久化。
 */
class MemoryRepository {

    private val cardsFile = EchoFileStore.memoryCardsFile
    private val profileFile = EchoFileStore.userProfileFile

    // ── MemoryCard 操作 ──

    /** 读取所有回忆卡片 */
    suspend fun getAllCards(): List<MemoryCard> = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems(cardsFile)
    }

    /** 根据 id 获取卡片 */
    suspend fun getCardById(id: String): MemoryCard? = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems<MemoryCard>(cardsFile).find { it.id == id }
    }

    /** 新增卡片 */
    suspend fun addCard(card: MemoryCard) = withContext(Dispatchers.IO) {
        val items = JsonAtomicWriter.readItems<MemoryCard>(cardsFile).toMutableList()
        items.add(card)
        JsonAtomicWriter.writeItems(cardsFile, items)
    }

    /** 更新卡片 */
    suspend fun updateCard(card: MemoryCard) = withContext(Dispatchers.IO) {
        val items = JsonAtomicWriter.readItems<MemoryCard>(cardsFile).toMutableList()
        val idx = items.indexOfFirst { it.id == card.id }
        if (idx >= 0) {
            items[idx] = card
            JsonAtomicWriter.writeItems(cardsFile, items)
        }
    }

    /** 删除卡片 */
    suspend fun deleteCard(id: String) = withContext(Dispatchers.IO) {
        val items = JsonAtomicWriter.readItems<MemoryCard>(cardsFile).filter { it.id != id }
        JsonAtomicWriter.writeItems(cardsFile, items)
    }

    /** 随机获取一张卡片 */
    suspend fun getRandomCard(): MemoryCard? = withContext(Dispatchers.IO) {
        val items = JsonAtomicWriter.readItems<MemoryCard>(cardsFile)
        if (items.isEmpty()) null else items.random()
    }

    /** 获取所有置顶卡片 */
    suspend fun getPinnedCards(): List<MemoryCard> = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems<MemoryCard>(cardsFile).filter { it.pinned }
    }

    /** 按标签搜索卡片 */
    suspend fun getCardsByTag(tag: String): List<MemoryCard> = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems<MemoryCard>(cardsFile).filter { tag in it.tags }
    }

    /** 按来源搜索卡片 */
    suspend fun getCardsBySource(sourceId: String): List<MemoryCard> = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems<MemoryCard>(cardsFile).filter { it.sourceId == sourceId }
    }

    // ── UserProfileMemory 操作 ──

    /** 读取所有用户画像 */
    suspend fun getAllProfiles(): List<UserProfileMemory> = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems(profileFile)
    }

    /** 获取所有启用的画像 */
    suspend fun getEnabledProfiles(): List<UserProfileMemory> = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems<UserProfileMemory>(profileFile).filter { it.enabled }
    }

    /** 根据分类获取画像 */
    suspend fun getProfilesByCategory(category: String): List<UserProfileMemory> = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems<UserProfileMemory>(profileFile).filter { it.category == category }
    }

    /** 新增或更新画像（按 key 去重） */
    suspend fun upsertProfile(profile: UserProfileMemory) = withContext(Dispatchers.IO) {
        val items = JsonAtomicWriter.readItems<UserProfileMemory>(profileFile).toMutableList()
        val idx = items.indexOfFirst { it.key == profile.key }
        if (idx >= 0) {
            items[idx] = profile
        } else {
            items.add(profile)
        }
        JsonAtomicWriter.writeItems(profileFile, items)
    }

    /** 删除画像 */
    suspend fun deleteProfile(id: String) = withContext(Dispatchers.IO) {
        val items = JsonAtomicWriter.readItems<UserProfileMemory>(profileFile).filter { it.id != id }
        JsonAtomicWriter.writeItems(profileFile, items)
    }

    /** 启用/禁用画像 */
    suspend fun toggleProfile(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val items = JsonAtomicWriter.readItems<UserProfileMemory>(profileFile).toMutableList()
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) {
            items[idx] = items[idx].copy(enabled = enabled)
            JsonAtomicWriter.writeItems(profileFile, items)
        }
    }

    /** 丢弃画像并加入去重列表，防止同内容再次出现 */
    suspend fun discardWithDedup(profile: UserProfileMemory) = withContext(Dispatchers.IO) {
        // Add to discarded list for dedup
        val discardedFile = java.io.File(profileFile.parentFile, "discarded_profiles.json")
        val discarded = JsonAtomicWriter.readItems<UserProfileMemory>(discardedFile).toMutableList()
        discarded.add(profile.copy(enabled = false, status = "discarded"))
        // Keep only last 100 discarded to avoid unbounded growth
        val trimmed = discarded.takeLast(100)
        JsonAtomicWriter.writeItems(discardedFile, trimmed)
        // Delete from active profiles
        deleteProfile(profile.id)
    }

    /** 检查相似的画像是否已被丢弃。true = 已丢弃，不应再创建 */
    suspend fun isSimilarDiscarded(value: String, category: String): Boolean = withContext(Dispatchers.IO) {
        val discardedFile = java.io.File(profileFile.parentFile, "discarded_profiles.json")
        if (!discardedFile.exists()) return@withContext false
        val discarded = JsonAtomicWriter.readItems<UserProfileMemory>(discardedFile)
        discarded.any {
            it.category == category && (it.value.contains(value) || value.contains(it.value))
        }
    }

    // ── 全文搜索（Phase 1 仅提供接口，具体搜索逻辑在 Phase 6 完善） ──

    /** 搜索所有记忆（LifeRecord / Diary / Card / Profile 的关键词匹配） */
    suspend fun searchAll(query: String, recordRepo: LifeRecordRepository, diaryRepo: DiaryRepository):
            MemorySearchResult = withContext(Dispatchers.IO) {
        val records = JsonAtomicWriter.readItems<com.example.myapplication.data.model.LifeRecord>(
            EchoFileStore.lifeRecordsFile
        ).filter { it.content.contains(query, ignoreCase = true) }

        val diaries = JsonAtomicWriter.readItems<com.example.myapplication.data.model.DailyDiary>(
            EchoFileStore.dailyDiariesFile
        ).filter {
            it.title.contains(query, ignoreCase = true) ||
            it.summary.contains(query, ignoreCase = true) ||
            it.diaryText.contains(query, ignoreCase = true)
        }

        val cards = JsonAtomicWriter.readItems<MemoryCard>(cardsFile).filter {
            it.status == "confirmed" && (
                it.quote.contains(query, ignoreCase = true) ||
                it.note.contains(query, ignoreCase = true))
        }

        val profiles = JsonAtomicWriter.readItems<UserProfileMemory>(profileFile).filter {
            it.enabled && it.status == "confirmed" &&
            it.value.contains(query, ignoreCase = true)
        }

        MemorySearchResult(
            query = query,
            lifeRecords = records,
            diaries = diaries,
            memoryCards = cards,
            profileMemories = profiles
        )
    }
}

/** 全文搜索结果 */
data class MemorySearchResult(
    val query: String,
    val lifeRecords: List<com.example.myapplication.data.model.LifeRecord>,
    val diaries: List<com.example.myapplication.data.model.DailyDiary>,
    val memoryCards: List<MemoryCard>,
    val profileMemories: List<UserProfileMemory>
)
