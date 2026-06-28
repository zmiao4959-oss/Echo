package com.example.myapplication.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.MyApplication
import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.data.repository.LifeRecordRepository
import com.example.myapplication.memory.Session
import com.example.myapplication.memory.SessionManager
import kotlinx.coroutines.Dispatchers
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

class TodayViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MyApplication
    private val recordRepo = LifeRecordRepository()

    // ── 今日 LifeRecord 列表 ──
    private val _records = MutableStateFlow<List<LifeRecord>>(emptyList())
    val records: StateFlow<List<LifeRecord>> = _records.asStateFlow()

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
            _records.value = recordRepo.getByDate(today()).sortedByDescending { it.createdAt }
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
        viewModelScope.launch {
            recordRepo.add(record)
            _records.value = recordRepo.getByDate(today()).sortedByDescending { it.createdAt }
        }
    }

    /** 删除一条 LifeRecord */
    fun deleteRecord(id: String) {
        viewModelScope.launch {
            recordRepo.delete(id)
            _records.value = recordRepo.getByDate(today()).sortedByDescending { it.createdAt }
        }
    }

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

    /** 根据今天记录生成日记标题预览 */
    fun getDiaryPreviewText(): String {
        val count = _records.value.size
        return if (count == 0) "今天还没有记录"
        else "已记录 $count 个片段"
    }

    companion object {
        fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}
