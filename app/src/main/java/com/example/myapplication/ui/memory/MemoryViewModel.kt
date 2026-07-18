package com.example.myapplication.ui.memory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.MyApplication
import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.data.model.UserProfileMemory
import com.example.myapplication.data.model.EchoForeshadow
import com.example.myapplication.data.model.ForeshadowState
import com.example.myapplication.data.repository.DiaryRepository
import com.example.myapplication.data.repository.ForeshadowRepository
import com.example.myapplication.data.repository.LifeRecordRepository
import com.example.myapplication.data.repository.MemoryRepository
import com.example.myapplication.policy.PastEchoPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class MemoryViewModel(application: Application) : AndroidViewModel(application) {

    private val memoryRepo = MemoryRepository()
    private val diaryRepo = DiaryRepository()
    private val recordRepo = LifeRecordRepository()
    private val foreshadowRepo = ForeshadowRepository()

    private val _cards = MutableStateFlow<List<MemoryCard>>(emptyList())
    val cards: StateFlow<List<MemoryCard>> = _cards.asStateFlow()

    private val _randomCard = MutableStateFlow<MemoryCard?>(null)
    val randomCard: StateFlow<MemoryCard?> = _randomCard.asStateFlow()

    private val _profiles = MutableStateFlow<List<UserProfileMemory>>(emptyList())
    val profiles: StateFlow<List<UserProfileMemory>> = _profiles.asStateFlow()

    private val _foreshadows = MutableStateFlow<List<EchoForeshadow>>(emptyList())
    val foreshadows: StateFlow<List<EchoForeshadow>> = _foreshadows.asStateFlow()

    private val _searchResults = MutableStateFlow<MemorySearchUiResult?>(null)
    val searchResults: StateFlow<MemorySearchUiResult?> = _searchResults.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun loadMemories() {
        viewModelScope.launch {
            _cards.value = memoryRepo.getAllCards().sortedByDescending { it.createdAt }
            shuffleRandomCard()
            _profiles.value = memoryRepo.getEnabledProfiles()
            _foreshadows.value = foreshadowRepo.getAll()
                .filter { it.state != ForeshadowState.DISMISSED }
                .sortedByDescending { it.lastEvidenceAt }
        }
    }

    fun snoozeForeshadow(threadId: String) {
        viewModelScope.launch {
            foreshadowRepo.snooze(threadId, System.currentTimeMillis())
            loadMemories()
        }
    }

    fun dismissForeshadow(threadId: String) {
        viewModelScope.launch {
            foreshadowRepo.dismiss(threadId, System.currentTimeMillis())
            loadMemories()
        }
    }

    /** 从已确认的记忆中换一张，尽量避免连续看到同一张。 */
    fun shuffleRandomCard() {
        val candidates = _cards.value.filter { it.status == "confirmed" }
        val currentId = _randomCard.value?.id
        _randomCard.value = when {
            candidates.isEmpty() -> null
            candidates.size == 1 -> candidates.first()
            else -> candidates.filterNot { it.id == currentId }.random()
        }
    }

    fun togglePin(cardId: String) {
        viewModelScope.launch {
            val card = memoryRepo.getCardById(cardId) ?: return@launch
            memoryRepo.updateCard(card.copy(pinned = !card.pinned))
            loadMemories()
        }
    }

    fun updateCard(card: MemoryCard) {
        viewModelScope.launch {
            memoryRepo.updateCard(card)
            loadMemories()
        }
    }

    fun deleteCard(cardId: String) {
        viewModelScope.launch {
            memoryRepo.deleteCard(cardId)
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

    // ── 往日回声 ──

    private val _onThisDayItem = MutableStateFlow<PastEchoPolicy.PastEcho?>(null)
    val onThisDayItem: StateFlow<PastEchoPolicy.PastEcho?> = _onThisDayItem.asStateFlow()

    fun loadOnThisDay() {
        viewModelScope.launch {
            val allDiaries = diaryRepo.getAll()
            val allRecords = recordRepo.getAll()
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            _onThisDayItem.value = PastEchoPolicy.select(allDiaries, allRecords, today)
        }
    }
}

data class MemorySearchUiResult(
    val query: String,
    val lifeRecords: List<LifeRecord>,
    val diaries: List<DailyDiary>,
    val memoryCards: List<MemoryCard>,
    val profileMemories: List<UserProfileMemory>
) {
    val totalCount: Int get() = lifeRecords.size + diaries.size + memoryCards.size + profileMemories.size
}
