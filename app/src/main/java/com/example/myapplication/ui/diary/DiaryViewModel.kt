package com.example.myapplication.ui.diary

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.MyApplication
import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.data.repository.DiaryRepository
import com.example.myapplication.data.repository.LifeRecordRepository
import com.example.myapplication.llm.LLMMessage
import com.example.myapplication.llm.OpenAICompatProvider
import com.example.myapplication.ui.ThemeColors
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

@Suppress("SpellCheckingInspection")
class DiaryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MyApplication
    private val diaryRepo = DiaryRepository()
    private val recordRepo = LifeRecordRepository()

    private val _diaries = MutableStateFlow<List<DailyDiary>>(emptyList())
    val diaries: StateFlow<List<DailyDiary>> = _diaries.asStateFlow()

    private val _todayDiary = MutableStateFlow<DailyDiary?>(null)
    val todayDiary: StateFlow<DailyDiary?> = _todayDiary.asStateFlow()

    private val _todayRecords = MutableStateFlow<List<LifeRecord>>(emptyList())
    val todayRecords: StateFlow<List<LifeRecord>> = _todayRecords.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // ── 筛选状态 ──
    private val _filterMonth = MutableStateFlow<String?>(null)
    val filterMonth: StateFlow<String?> = _filterMonth.asStateFlow()

    private val _filterMood = MutableStateFlow<String?>(null)

    private val _filterTag = MutableStateFlow<String?>(null)

    /** 所有可用月份（从已有日记中提取） */
    private val _availableMonths = MutableStateFlow<List<String>>(emptyList())
    val availableMonths: StateFlow<List<String>> = _availableMonths.asStateFlow()

    /** 所有可用标签 */
    private val _availableTags = MutableStateFlow<List<String>>(emptyList())
    val availableTags: StateFlow<List<String>> = _availableTags.asStateFlow()

    /** 所有可用情绪 */
    private val _availableMoods = MutableStateFlow<List<String>>(emptyList())
    val availableMoods: StateFlow<List<String>> = _availableMoods.asStateFlow()

    /** 筛选后的日记列表 */
    private val _filteredDiaries = MutableStateFlow<List<DailyDiary>>(emptyList())
    val filteredDiaries: StateFlow<List<DailyDiary>> = _filteredDiaries.asStateFlow()

    fun loadDiaries() {
        viewModelScope.launch {
            val all = diaryRepo.getAll()
            _diaries.value = all

            // 提取可用月份、标签、情绪
            _availableMonths.value = all
                .map { it.date.take(7) }  // "yyyy-MM"
                .distinct()
                .sortedDescending()

            _availableTags.value = all
                .flatMap { it.tags }
                .distinct()
                .sorted()

            _availableMoods.value = all
                .map { it.mood }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()

            applyFilters(all)
            _todayDiary.value = diaryRepo.getByDate(today())
            _todayRecords.value = recordRepo.getByDate(today())
            computeMoodStats(_moodStatsDays.value)
        }
    }

    fun setMonthFilter(month: String?) {
        _filterMonth.value = month
        applyFilters(_diaries.value)
    }

    fun setMoodFilter(mood: String?) {
        _filterMood.value = mood
        applyFilters(_diaries.value)
    }

    fun setTagFilter(tag: String?) {
        _filterTag.value = tag
        applyFilters(_diaries.value)
    }

    fun clearFilters() {
        _filterMonth.value = null
        _filterMood.value = null
        _filterTag.value = null
        applyFilters(_diaries.value)
    }

    private fun applyFilters(all: List<DailyDiary>) {
        var result = all
        _filterMonth.value?.let { month ->
            result = result.filter { it.date.startsWith(month) }
        }
        _filterMood.value?.let { mood ->
            result = result.filter { it.mood == mood }
        }
        _filterTag.value?.let { tag ->
            result = result.filter { it.tags.contains(tag) }
        }
        _filteredDiaries.value = result.sortedByDescending { it.date }
    }

    // ── 情绪趋势 ──

    private val _moodStats = MutableStateFlow<List<MoodStat>>(emptyList())
    val moodStats: StateFlow<List<MoodStat>> = _moodStats.asStateFlow()

    private val _moodStatsDays = MutableStateFlow(7)
    val moodStatsDays: StateFlow<Int> = _moodStatsDays.asStateFlow()

    fun computeMoodStats(days: Int = 7) {
        _moodStatsDays.value = days
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -days)
            val cutoff = dateFormat.format(cal.time)
            val allDiaries = _diaries.value.filter { it.date >= cutoff }
            val allRecords = recordRepo.getAll().filter { it.date >= cutoff }

            // 汇总所有 mood
            val moodCounts = mutableMapOf<String, Int>()
            for (d in allDiaries) {
                if (d.mood.isNotBlank()) {
                    moodCounts[d.mood] = (moodCounts[d.mood] ?: 0) + 1
                }
            }
            for (r in allRecords) {
                val mood = r.mood
                if (!mood.isNullOrBlank()) {
                    moodCounts[mood] = (moodCounts[mood] ?: 0) + 1
                }
            }

            val palette = ThemeColors.moodPalette(getApplication()).toList()

            _moodStats.value = moodCounts.entries
                .sortedByDescending { it.value }
                .take(8)
                .mapIndexed { i, (mood, count) ->
                    MoodStat(mood, count, palette[i % palette.size])
                }
        }
    }

    /** 手动生成今日日记 */
    fun generateTodayDiary() {
        viewModelScope.launch {
            val records = recordRepo.getByDate(today())
            if (records.isEmpty()) {
                _statusMessage.value = "今天还没有生活记录"
                return@launch
            }

            _isGenerating.value = true
            _statusMessage.value = "正在生成日记…"

            try {
                val diary = generateDiaryFromRecords(records)
                diaryRepo.add(diary)
                _todayDiary.value = diary
                _diaries.value = diaryRepo.getAll()
            } catch (e: Exception) {
                Log.e("DiaryVM", "Diary generation failed", e)
                _statusMessage.value = "日记生成失败: ${e.message}"
            } finally {
                _isGenerating.value = false
                _statusMessage.value = null
            }
        }
    }

    /** 删除日记 */
    fun deleteDiary(id: String) {
        viewModelScope.launch {
            diaryRepo.delete(id)
            _todayDiary.value = diaryRepo.getByDate(today())
            _diaries.value = diaryRepo.getAll()
        }
    }

    /** 重新生成某一天的日记 */
    fun regenerateDiary(diaryId: String) {
        viewModelScope.launch {
            val diary = diaryRepo.getById(diaryId) ?: return@launch
            val records = recordRepo.getByDate(diary.date)
            if (records.isEmpty()) {
                _statusMessage.value = "该日期没有生活记录"
                return@launch
            }

            _isGenerating.value = true
            _statusMessage.value = "正在重新生成…"
            try {
                val newDiary = generateDiaryFromRecords(records)
                newDiary.sourceRecordIds.forEach { _ ->
                    // sourceRecordIds already set in generateDiaryFromRecords
                }
                diaryRepo.add(newDiary)
                _todayDiary.value = diaryRepo.getByDate(today())
                _diaries.value = diaryRepo.getAll()
            } catch (e: Exception) {
                Log.e("DiaryVM", "Regeneration failed", e)
                _statusMessage.value = "重新生成失败: ${e.message}"
            } finally {
                _isGenerating.value = false
                _statusMessage.value = null
            }
        }
    }

    /** 调用 LLM 根据 LifeRecord 生成 DailyDiary */
    private suspend fun generateDiaryFromRecords(records: List<LifeRecord>): DailyDiary =
        withContext(Dispatchers.IO) {
            val config = app.appConfig
            if (!config.isLLMConfigured) throw IllegalStateException("LLM 未配置")

            // 整理片段
            val fragmentsText = records.joinToString("\n\n") { r ->
                val src = when (r.source) {
                    "voice" -> "语音"
                    "chat" -> "对话"
                    "checkin" -> "问候"
                    else -> "文字"
                }
                "[$src] ${r.content}" + if (r.mood != null) " (情绪: ${r.mood})" else ""
            }

            val prompt = """
请根据以下生活片段生成一篇今日生活日记。要求：
1. 不要编造事实。
2. 保留用户真实表达。
3. 语气温柔、克制、真诚。
4. diaryText 不要像总结报告，要像一篇私人日记。
5. 提取 3-6 个 tags。
6. mood 用一句短语表达。

生活片段：
$fragmentsText

请输出纯 JSON（不要 markdown 代码块标记），格式如下：
{"title":"日记标题","summary":"一句话摘要","diaryText":"完整日记正文","mood":"情绪短语","tags":["标签1","标签2"]}
            """.trimIndent()

            val provider = OpenAICompatProvider(
                apiKey = config.llmApiKey,
                baseUrl = config.llmBaseUrl,
                model = config.llmModel
            )

            val response = provider.chat(
                messages = listOf(
                    LLMMessage(role = "system", content = "你是一个温柔克制的日记写作者。只输出 JSON，不要输出其他内容。"),
                    LLMMessage(role = "user", content = prompt)
                ),
                temperature = 0.7f,
                maxTokens = 2048
            )

            val jsonText = extractJson(response.content)
            val json = JsonParser.parseString(jsonText).asJsonObject

            DailyDiary(
                id = UUID.randomUUID().toString(),
                date = today(),
                title = json.get("title")?.asString ?: "日记",
                summary = json.get("summary")?.asString ?: "",
                diaryText = json.get("diaryText")?.asString ?: response.content,
                mood = json.get("mood")?.asString ?: "",
                tags = json.getAsJsonArray("tags")?.map { it.asString } ?: emptyList(),
                sourceRecordIds = records.map { it.id },
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }

    /** 从 LLM 响应中提取 JSON（处理可能的 markdown 代码块） */
    private fun extractJson(text: String): String {
        var t = text.trim()
        // 去掉 markdown 代码块标记
        if (t.startsWith("```")) {
            t = t.removePrefix("```").trimStart()
            if (t.startsWith("json")) t = t.removePrefix("json").trimStart()
            t = t.trim()
            if (t.endsWith("```")) t = t.removeSuffix("```").trim()
        }
        // 找到第一个 { 和最后一个 }
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        if (start in 0 until end) {
            t = t.substring(start, end + 1)
        }
        return t
    }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        fun today(): String = dateFormat.format(Calendar.getInstance().time)
    }
}

data class MoodStat(
    val mood: String,
    val count: Int,
    val color: Int
)
