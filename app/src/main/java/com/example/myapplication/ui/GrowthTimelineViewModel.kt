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

    private val _items = MutableStateFlow<List<TimelineItem>>(emptyList())
    val items: StateFlow<List<TimelineItem>> = _items.asStateFlow()

    private val _typeFilter = MutableStateFlow<Set<String>>(ALL_TYPES)
    val typeFilter: StateFlow<Set<String>> = _typeFilter.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isEmpty = MutableStateFlow(false)
    val isEmpty: StateFlow<Boolean> = _isEmpty.asStateFlow()

    private var allItems: List<TimelineItem> = emptyList()

    /** All supported filter type labels. */
    companion object {
        val ALL_TYPES = setOf("生活记录", "日记", "记忆卡片", "周回顾", "计划", "画像变更")
        const val MAX_TIMELINE_ITEMS = 200
    }

    fun loadTimeline() {
        viewModelScope.launch {
            _isLoading.value = true
            val items = withContext(Dispatchers.IO) { buildTimeline() }
            allItems = items
            _items.value = applyFilter(items, _typeFilter.value)
            _isEmpty.value = _items.value.isEmpty()
            _isLoading.value = false
        }
    }

    fun setTypeFilter(types: Set<String>) {
        _typeFilter.value = types
        _items.value = applyFilter(allItems, types)
        _isEmpty.value = _items.value.isEmpty()
    }

    private fun applyFilter(items: List<TimelineItem>, types: Set<String>): List<TimelineItem> {
        if (types.containsAll(ALL_TYPES)) return items
        return items.filter { it.typeLabel in types }
    }

    private suspend fun buildTimeline(): List<TimelineItem> {
        val result = mutableListOf<TimelineItem>()

        // LifeRecords — all, excluded: none (LifeRecords don't have status)
        val records = recordRepo.getAll()
        for (r in records) {
            result.add(TimelineItem.LifeRecordItem(
                id = r.id,
                timestamp = r.createdAt,
                content = r.content,
                mood = r.mood,
                tags = r.tags,
                source = r.source,
                audioPath = r.audioPath
            ))
        }

        // Diaries — all
        val diaries = diaryRepo.getAll()
        for (d in diaries) {
            result.add(TimelineItem.DiaryItem(
                id = d.id,
                timestamp = d.createdAt,
                title = d.title,
                summary = d.summary,
                mood = d.mood,
                date = d.date
            ))
        }

        // MemoryCards — only confirmed, exclude pending/disabled
        val cards = memoryRepo.getAllCards()
        for (c in cards) {
            if (c.status != "confirmed") continue
            result.add(TimelineItem.MemoryCardItem(
                id = c.id,
                timestamp = c.createdAt,
                quote = c.quote,
                note = c.note,
                mood = c.mood,
                tags = c.tags,
                memoryDate = c.memoryDate,
                isPinned = c.pinned
            ))
        }

        // Plans — all
        val plans = planRepo.getAll()
        for (p in plans) {
            result.add(TimelineItem.PlanItem(
                id = p.id,
                timestamp = p.createdAt,
                planType = p.type,
                title = p.title,
                message = p.message,
                completed = p.lastTriggeredAt != null
            ))
        }

        // WeeklyReview — current week entry
        result.add(TimelineItem.WeeklyReviewItem(
            id = "weekly_review_current",
            timestamp = System.currentTimeMillis(),
            dateRange = "本周",
            summary = "查看本周回顾",
            lifeRecordCount = 0,
            diaryCount = 0,
            memoryCardCount = 0
        ))

        // Profile changes — from audit log entries with memoryType="profile"
        // (loaded lazily to avoid circular dependency; empty list for now as
        //  AuditLogStore is separate from repo layer)

        // Sort by timestamp descending, cap at MAX
        return result.sortedByDescending { it.timestamp }.take(MAX_TIMELINE_ITEMS)
    }
}
