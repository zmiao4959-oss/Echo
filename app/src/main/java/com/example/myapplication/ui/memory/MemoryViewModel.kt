package com.example.myapplication.ui.memory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.MyApplication
import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.data.model.UserProfileMemory
import com.example.myapplication.data.repository.DiaryRepository
import com.example.myapplication.data.repository.LifeRecordRepository
import com.example.myapplication.data.repository.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

class MemoryViewModel(application: Application) : AndroidViewModel(application) {

    private val memoryRepo = MemoryRepository()
    private val diaryRepo = DiaryRepository()
    private val recordRepo = LifeRecordRepository()

    private val _cards = MutableStateFlow<List<MemoryCard>>(emptyList())
    val cards: StateFlow<List<MemoryCard>> = _cards.asStateFlow()

    private val _randomCard = MutableStateFlow<MemoryCard?>(null)
    val randomCard: StateFlow<MemoryCard?> = _randomCard.asStateFlow()

    private val _profiles = MutableStateFlow<List<UserProfileMemory>>(emptyList())
    val profiles: StateFlow<List<UserProfileMemory>> = _profiles.asStateFlow()

    private val _searchResults = MutableStateFlow<MemorySearchUiResult?>(null)
    val searchResults: StateFlow<MemorySearchUiResult?> = _searchResults.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun loadMemories() {
        viewModelScope.launch {
            _cards.value = memoryRepo.getAllCards().sortedByDescending { it.createdAt }
            _randomCard.value = memoryRepo.getRandomCard()
            _profiles.value = memoryRepo.getEnabledProfiles()
        }
    }

    fun togglePin(cardId: String) {
        viewModelScope.launch {
            val card = memoryRepo.getCardById(cardId) ?: return@launch
            memoryRepo.updateCard(card.copy(pinned = !card.pinned))
            loadMemories()
        }
    }

    fun toggleProfile(profileId: String) {
        viewModelScope.launch {
            val profile = _profiles.value.find { it.id == profileId } ?: return@launch
            memoryRepo.toggleProfile(profileId, !profile.enabled)
            loadMemories()
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = null
            return
        }
        viewModelScope.launch {
            val result = memoryRepo.searchAll(query, recordRepo, diaryRepo)
            _searchResults.value = MemorySearchUiResult(
                query = query,
                lifeRecords = result.lifeRecords,
                diaries = result.diaries,
                memoryCards = result.memoryCards,
                profileMemories = result.profileMemories
            )
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = null
    }

    // ── 那天的你 ──

    private val _onThisDayItem = MutableStateFlow<OnThisDayItem?>(null)
    val onThisDayItem: StateFlow<OnThisDayItem?> = _onThisDayItem.asStateFlow()

    fun loadOnThisDay() {
        viewModelScope.launch {
            val today = Calendar.getInstance()
            val monthDay = SimpleDateFormat("MM-dd", Locale.US).format(today.time)

            // 查找历史上同月同日的日记（排除今年）
            val allDiaries = diaryRepo.getAll()
            val onThisDayDiaries = allDiaries.filter { d ->
                d.date.length >= 10 && d.date.substring(5) == monthDay && d.date.substring(0, 4) != today.get(Calendar.YEAR).toString()
            }.sortedByDescending { it.date }

            _onThisDayItem.value = if (onThisDayDiaries.isNotEmpty()) {
                val diary = onThisDayDiaries.first()
                OnThisDayItem(
                    date = diary.date,
                    title = diary.title,
                    snippet = diary.summary.ifEmpty { diary.diaryText.take(80) },
                    diaryId = diary.id
                )
            } else {
                // 也查一下 LifeRecord
                val allRecords = recordRepo.getAll()
                val onThisDayRecords = allRecords.filter { r ->
                    r.date.length >= 10 && r.date.substring(5) == monthDay && r.date.substring(0, 4) != today.get(Calendar.YEAR).toString()
                }.sortedByDescending { it.date }

                if (onThisDayRecords.isNotEmpty()) {
                    val record = onThisDayRecords.first()
                    OnThisDayItem(
                        date = record.date,
                        title = "那天的记录",
                        snippet = record.content.take(80),
                        diaryId = null
                    )
                } else null
            }
        }
    }
}

data class OnThisDayItem(
    val date: String,
    val title: String,
    val snippet: String,
    val diaryId: String?
)

data class MemorySearchUiResult(
    val query: String,
    val lifeRecords: List<LifeRecord>,
    val diaries: List<DailyDiary>,
    val memoryCards: List<MemoryCard>,
    val profileMemories: List<UserProfileMemory>
) {
    val totalCount: Int get() = lifeRecords.size + diaries.size + memoryCards.size + profileMemories.size
}
