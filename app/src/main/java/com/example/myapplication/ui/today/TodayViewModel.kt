package com.example.myapplication.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.MyApplication
import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.data.repository.LifeRecordRepository
import com.example.myapplication.data.repository.DiaryRepository
import com.example.myapplication.llm.LLMMessage
import com.example.myapplication.llm.ProviderFactory
import com.example.myapplication.memory.Session
import com.example.myapplication.memory.SessionManager
import com.example.myapplication.net.HttpClient
import com.example.myapplication.policy.MicroEchoFeedbackPolicy
import com.example.myapplication.policy.MicroEchoGenerator
import com.example.myapplication.policy.WeeklyFootprintPolicy
import com.example.myapplication.policy.TodayDiaryClosurePolicy
import com.example.myapplication.policy.ReturnWelcomePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class TodayViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MyApplication
    private val recordRepo = LifeRecordRepository()
    private val diaryRepo = DiaryRepository()

    // ── 今日 LifeRecord 列表 ──
    private val _records = MutableStateFlow<List<LifeRecord>>(emptyList())
    val records: StateFlow<List<LifeRecord>> = _records.asStateFlow()

    // ── 最近 7 天的非连续记录足迹 ──
    private val _weeklyFootprint = MutableStateFlow(
        WeeklyFootprintPolicy.build(emptyList(), today())
    )
    val weeklyFootprint: StateFlow<WeeklyFootprintPolicy.WeeklyFootprint> =
        _weeklyFootprint.asStateFlow()

    // ── 今日片段到今日日记的闭环状态；null 表示仍在加载 ──
    private val _diaryClosure = MutableStateFlow<TodayDiaryClosurePolicy.Closure?>(null)
    val diaryClosure: StateFlow<TodayDiaryClosurePolicy.Closure?> = _diaryClosure.asStateFlow()

    // ── 记录间隔后的无压力回归提示 ──
    private val _returnWelcome = MutableStateFlow<ReturnWelcomePolicy.Welcome>(
        ReturnWelcomePolicy.Welcome.Hidden
    )
    val returnWelcome: StateFlow<ReturnWelcomePolicy.Welcome> = _returnWelcome.asStateFlow()

    // ── 最近一次记录后的微回声 ──
    private val _microEcho = MutableStateFlow<MicroEchoState>(MicroEchoState.Hidden)
    val microEcho: StateFlow<MicroEchoState> = _microEcho.asStateFlow()
    private var microEchoJob: Job? = null

    private val microEchoClient by lazy {
        HttpClient.instance.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(18, TimeUnit.SECONDS)
            .build()
    }

    private val microEchoGenerator = MicroEchoGenerator { systemPrompt, userPrompt ->
        val config = app.appConfig
        if (!config.isLLMConfigured) {
            null
        } else {
            val provider = ProviderFactory.createLLMProvider(microEchoClient)
            provider.chat(
                messages = listOf(
                    LLMMessage(role = "system", content = systemPrompt),
                    LLMMessage(role = "user", content = userPrompt)
                ),
                tools = null,
                temperature = 0.65f,
                maxTokens = 120
            ).content
        }
    }

    // ── 最近一次会话预览（小爪对话卡片用） ──
    private val _latestSession = MutableStateFlow<Session?>(null)
    val latestSession: StateFlow<Session?> = _latestSession.asStateFlow()

    // ── 日期 ──
    private val _todayDate = MutableStateFlow(today())
    val todayDate: StateFlow<String> = _todayDate.asStateFlow()

    /** 加载今日数据 */
    fun loadToday() {
        _todayDate.value = today()
        viewModelScope.launch {
            val loaded = refreshRecordsAndFootprint()
            if (_microEcho.value !is MicroEchoState.Generating) {
                restoreMicroEcho(loaded)
            }
        }
        loadLatestSession()
    }

    /** 新增一条 LifeRecord */
    fun addRecord(content: String, source: String = "text", audioPath: String? = null) {
        if (content.isBlank()) return
        val record = LifeRecord(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            date = today(),
            content = content.trim(),
            source = source,
            audioPath = audioPath
        )

        microEchoJob?.cancel()
        _microEcho.value = MicroEchoState.Generating(record.id)
        microEchoJob = viewModelScope.launch {
            try {
                recordRepo.add(record)
                refreshRecordsAndFootprint()
                val context = buildEchoContext(record)
                val echo = microEchoGenerator.generate(
                    content = record.content,
                    recentContents = context.recentContents,
                    likedEchoes = context.preferences.liked,
                    rejectedEchoes = context.preferences.rejected
                )
                recordRepo.update(record.copy(microEcho = echo))

                refreshRecordsAndFootprint()
                _microEcho.value = MicroEchoState.Ready(record.id, echo, liked = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                val fallback = MicroEchoGenerator.localFallback(record.content)
                try {
                    recordRepo.update(record.copy(microEcho = fallback))
                } catch (_: Exception) {
                    // 记录本身已经优先保存；回声写回失败不影响主流程。
                }
                _microEcho.value = MicroEchoState.Ready(record.id, fallback, liked = false)
            }
        }
    }

    /** 记住用户认可的回声语气，后续生成仅作为本地示例使用。 */
    fun likeMicroEcho(recordId: String) {
        viewModelScope.launch {
            try {
                val record = recordRepo.getById(recordId) ?: return@launch
                val echo = record.microEcho?.takeIf { it.isNotBlank() } ?: return@launch
                if (record.microEchoLiked) return@launch
                recordRepo.update(record.copy(microEchoLiked = true))
                refreshRecordsAndFootprint()
                val current = _microEcho.value as? MicroEchoState.Ready
                if (current?.recordId == recordId && current.text == echo) {
                    _microEcho.value = current.copy(liked = true)
                }
            } catch (_: Exception) {
                // 反馈保存失败不影响已经保存的生活片段和回声。
            }
        }
    }

    /** 拒绝当前表达并基于已积累的喜好重新生成一句。 */
    fun regenerateMicroEcho(recordId: String) {
        microEchoJob?.cancel()
        _microEcho.value = MicroEchoState.Generating(recordId)
        microEchoJob = viewModelScope.launch {
            var fallbackSnapshot: MicroEchoState.Ready? = null
            try {
                val record = recordRepo.getById(recordId)
                    ?: return@launch hideGeneratingEcho(recordId)
                val previousEcho = record.microEcho?.takeIf { it.isNotBlank() }
                    ?: return@launch hideGeneratingEcho(recordId)
                fallbackSnapshot = MicroEchoState.Ready(
                    recordId = record.id,
                    text = previousEcho,
                    liked = record.microEchoLiked
                )
                val rejectedForRecord = MicroEchoFeedbackPolicy.addRejected(
                    record.rejectedMicroEchoes,
                    previousEcho
                )
                val pendingRecord = record.copy(
                    microEchoLiked = false,
                    rejectedMicroEchoes = rejectedForRecord
                )
                val context = buildEchoContext(pendingRecord, replaceExisting = true)
                val echo = microEchoGenerator.generate(
                    content = record.content,
                    recentContents = context.recentContents,
                    likedEchoes = context.preferences.liked,
                    rejectedEchoes = context.preferences.rejected
                )
                recordRepo.update(pendingRecord.copy(microEcho = echo))
                refreshRecordsAndFootprint()
                _microEcho.value = MicroEchoState.Ready(recordId, echo, liked = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                fallbackSnapshot?.let(::restoreEchoSnapshot) ?: hideGeneratingEcho(recordId)
            }
        }
    }

    /** 修改片段正文，并为新内容重新生成与之匹配的微回声。 */
    fun updateRecordContent(recordId: String, content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return

        microEchoJob?.cancel()
        _microEcho.value = MicroEchoState.Generating(recordId)
        microEchoJob = viewModelScope.launch {
            try {
                val original = recordRepo.getById(recordId)
                    ?: return@launch hideGeneratingEcho(recordId)
                val updated = original.copy(
                    content = trimmed,
                    microEcho = null,
                    microEchoLiked = false
                )
                recordRepo.update(updated)
                refreshRecordsAndFootprint()

                val context = buildEchoContext(updated, replaceExisting = true)
                val echo = microEchoGenerator.generate(
                    content = updated.content,
                    recentContents = context.recentContents,
                    likedEchoes = context.preferences.liked,
                    rejectedEchoes = context.preferences.rejected
                )
                recordRepo.update(updated.copy(microEcho = echo))
                refreshRecordsAndFootprint()
                _microEcho.value = MicroEchoState.Ready(recordId, echo, liked = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                val current = recordRepo.getById(recordId)
                if (current != null) {
                    val fallback = MicroEchoGenerator.localFallback(current.content)
                    recordRepo.update(current.copy(microEcho = fallback, microEchoLiked = false))
                    refreshRecordsAndFootprint()
                    _microEcho.value = MicroEchoState.Ready(recordId, fallback, liked = false)
                } else {
                    hideGeneratingEcho(recordId)
                }
            }
        }
    }

    /** 删除一条 LifeRecord */
    fun deleteRecord(id: String) {
        val echoRecordId = when (val state = _microEcho.value) {
            is MicroEchoState.Generating -> state.recordId
            is MicroEchoState.Ready -> state.recordId
            MicroEchoState.Hidden -> null
        }
        if (echoRecordId == id) microEchoJob?.cancel()

        viewModelScope.launch {
            recordRepo.delete(id)
            val loaded = refreshRecordsAndFootprint()
            if (echoRecordId == id) restoreMicroEcho(loaded)
        }
    }

    private suspend fun refreshRecordsAndFootprint(): List<LifeRecord> {
        val currentDate = today()
        // JsonAtomicWriter-backed date range queries already scan the full file;
        // load once so the same snapshot can also determine the last valid record.
        val allRecords = recordRepo.getAll()
        val earliestDate = WeeklyFootprintPolicy.earliestRequiredDate(currentDate)
        val recentRecords = allRecords.filter { it.date in earliestDate..currentDate }
        val todayRecords = recentRecords
            .filter { it.date == currentDate }
            .sortedByDescending { it.createdAt }
        _records.value = todayRecords
        _weeklyFootprint.value = WeeklyFootprintPolicy.build(recentRecords, currentDate)
        _returnWelcome.value = ReturnWelcomePolicy.build(allRecords, currentDate)
        _diaryClosure.value = TodayDiaryClosurePolicy.build(
            todayRecords,
            diaryRepo.getByDate(currentDate)
        )
        return todayRecords
    }

    private fun restoreMicroEcho(records: List<LifeRecord>) {
        val latest = records.firstOrNull()
        val echo = latest?.microEcho?.takeIf { it.isNotBlank() }
        _microEcho.value = if (latest != null && echo != null) {
            MicroEchoState.Ready(latest.id, echo, liked = latest.microEchoLiked)
        } else {
            MicroEchoState.Hidden
        }
    }

    private suspend fun buildEchoContext(
        target: LifeRecord,
        replaceExisting: Boolean = false
    ): EchoContext {
        val stored = recordRepo.getAll()
        val records = if (replaceExisting) {
            stored.map { if (it.id == target.id) target else it }
        } else {
            stored
        }
        val recentContents = records
            .asSequence()
            .filter { it.date == target.date && it.id != target.id }
            .sortedByDescending { it.createdAt }
            .map { it.content }
            .take(4)
            .toList()
        return EchoContext(
            recentContents = recentContents,
            preferences = MicroEchoFeedbackPolicy.collect(records, target)
        )
    }

    private fun restoreEchoSnapshot(snapshot: MicroEchoState.Ready) {
        if ((_microEcho.value as? MicroEchoState.Generating)?.recordId == snapshot.recordId) {
            _microEcho.value = snapshot
        }
    }

    private fun hideGeneratingEcho(recordId: String) {
        if ((_microEcho.value as? MicroEchoState.Generating)?.recordId == recordId) {
            _microEcho.value = MicroEchoState.Hidden
        }
    }

    private data class EchoContext(
        val recentContents: List<String>,
        val preferences: MicroEchoFeedbackPolicy.Preferences
    )

    /** 加载最近会话 */
    private fun loadLatestSession() {
        viewModelScope.launch {
            val sessionsDir = File(app.filesDir, "sessions")
            val sessionManager = SessionManager(
                saveDir = sessionsDir,
                maxContextTokens = { app.appConfig.maxContextTokens },
                compactionKeepMessages = { app.appConfig.compactionKeepMessages }
            )
            val sessions = withContext(Dispatchers.IO) {
                sessionManager.getAllSessions()
            }
            _latestSession.value = sessions.firstOrNull()
        }
    }

    companion object {
        fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}

sealed class MicroEchoState {
    object Hidden : MicroEchoState()
    data class Generating(val recordId: String) : MicroEchoState()
    data class Ready(val recordId: String, val text: String, val liked: Boolean = false) : MicroEchoState()
}
