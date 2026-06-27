package com.example.myapplication.ui

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.*
import com.example.myapplication.data.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GrowthTimelineViewModel(application: android.app.Application) : AndroidViewModel(application) {

    private val memoryRepo = MemoryRepository()
    private val recordRepo = LifeRecordRepository()
    private val diaryRepo = DiaryRepository()
    private val planRepo = PlanRepository()

    private val _displayedItems = MutableStateFlow<List<TimelineItem>>(emptyList())
    val items: StateFlow<List<TimelineItem>> = _displayedItems.asStateFlow()

    private val _typeFilter = MutableStateFlow<Set<String>>(ALL_TYPES)
    val typeFilter: StateFlow<Set<String>> = _typeFilter.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _isEmpty = MutableStateFlow(false)
    val isEmpty: StateFlow<Boolean> = _isEmpty.asStateFlow()

    /** 全量缓存（完整排序列表，未过滤） */
    private var allItems: List<TimelineItem> = emptyList()
    private var currentPage = 0

    companion object {
        val ALL_TYPES = setOf("生活记录", "日记", "记忆卡片", "周回顾", "计划", "画像变更")
        const val PAGE_SIZE = 30
    }

    fun loadTimeline() {
        viewModelScope.launch {
            _isLoading.value = true
            allItems = withContext(Dispatchers.IO) { buildTimeline() }
            resetAndEmitPage()
            _isLoading.value = false
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value) return
        _isLoadingMore.value = true
        viewModelScope.launch {
            val eligible = getFilteredItems()
            val start = currentPage * PAGE_SIZE + PAGE_SIZE
            val next = eligible.drop(start).take(PAGE_SIZE)
            if (next.isNotEmpty()) {
                currentPage++
                _displayedItems.value = _displayedItems.value + next
                _hasMore.value = (start + PAGE_SIZE) < eligible.size
            }
            _isLoadingMore.value = false
        }
    }

    fun setTypeFilter(types: Set<String>) {
        _typeFilter.value = types
        resetAndEmitPage()
    }

    private fun resetAndEmitPage() {
        currentPage = 0
        val eligible = getFilteredItems()
        val page = eligible.take(PAGE_SIZE)
        _displayedItems.value = page
        _hasMore.value = eligible.size > PAGE_SIZE
        _isEmpty.value = page.isEmpty()
    }

    private fun getFilteredItems(): List<TimelineItem> {
        val types = _typeFilter.value
        if (types.containsAll(ALL_TYPES)) return allItems
        return allItems.filter { it.typeLabel in types }
    }

    private suspend fun buildTimeline(): List<TimelineItem> {
        val result = mutableListOf<TimelineItem>()

        val records = recordRepo.getAll()
        for (r in records) {
            result.add(TimelineItem.LifeRecordItem(
                id = r.id, timestamp = r.createdAt, content = r.content,
                mood = r.mood, tags = r.tags, source = r.source, audioPath = r.audioPath
            ))
        }

        val diaries = diaryRepo.getAll()
        for (d in diaries) {
            result.add(TimelineItem.DiaryItem(
                id = d.id, timestamp = d.createdAt, title = d.title,
                summary = d.summary, mood = d.mood, date = d.date
            ))
        }

        val cards = memoryRepo.getAllCards()
        for (c in cards) {
            if (c.status != "confirmed") continue
            result.add(TimelineItem.MemoryCardItem(
                id = c.id, timestamp = c.createdAt, quote = c.quote, note = c.note,
                mood = c.mood, tags = c.tags, memoryDate = c.memoryDate, isPinned = c.pinned
            ))
        }

        val plans = planRepo.getAll()
        for (p in plans) {
            result.add(TimelineItem.PlanItem(
                id = p.id, timestamp = p.createdAt, planType = p.type,
                title = p.title, message = p.message, completed = p.lastTriggeredAt != null
            ))
        }

        result.add(TimelineItem.WeeklyReviewItem(
            id = "weekly_review_current", timestamp = System.currentTimeMillis(),
            dateRange = "本周", summary = "查看本周回顾",
            lifeRecordCount = 0, diaryCount = 0, memoryCardCount = 0
        ))

        return result.sortedByDescending { it.timestamp }
    }
}
