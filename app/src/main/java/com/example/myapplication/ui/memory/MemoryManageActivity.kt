package com.example.myapplication.ui.memory

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.data.model.UserProfileMemory
import com.example.myapplication.data.repository.MemoryRepository
import com.example.myapplication.data.store.AuditLogStore
import com.example.myapplication.memory.FileStore
import com.example.myapplication.policy.MemoryGovernanceService
import com.example.myapplication.ui.ThemedActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MemoryManageActivity : ThemedActivity() {

    private lateinit var containerProfiles: LinearLayout
    private lateinit var containerPending: LinearLayout
    private lateinit var containerCards: LinearLayout
    private lateinit var containerFacts: LinearLayout
    private lateinit var containerAuditLog: LinearLayout
    private lateinit var tvEmptyProfiles: TextView
    private lateinit var tvEmptyPending: TextView
    private lateinit var tvEmptyCards: TextView
    private lateinit var tvEmptyFacts: TextView
    private lateinit var tvEmptyAuditLog: TextView
    private lateinit var searchInput: EditText
    private lateinit var chipContainer: LinearLayout

    private val memoryRepo = MemoryRepository()

    // Cached raw data for filtering
    private var allProfiles: List<UserProfileMemory> = emptyList()
    private var allCards: List<MemoryCard> = emptyList()
    private var allFacts: List<MemoryFact> = emptyList()

    // Filter state
    private val activeFilters = mutableSetOf<String>()
    private var searchQuery = ""

    private val sourceLabelMap = mapOf(
        "chat" to "对话记录", "life_record" to "生活记录",
        "diary" to "日记", "manual" to "手动添加", "" to "未知来源"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memory_manage)

        containerProfiles = findViewById(R.id.container_profiles)
        containerPending = findViewById(R.id.container_pending)
        containerCards = findViewById(R.id.container_cards)
        containerFacts = findViewById(R.id.container_memory_facts)
        containerAuditLog = findViewById(R.id.container_audit_log)
        tvEmptyProfiles = findViewById(R.id.tv_empty_profiles)
        tvEmptyPending = findViewById(R.id.tv_empty_pending)
        tvEmptyCards = findViewById(R.id.tv_empty_cards)
        tvEmptyFacts = findViewById(R.id.tv_empty_facts)
        tvEmptyAuditLog = findViewById(R.id.tv_empty_audit_log)
        searchInput = findViewById(R.id.search_memory)
        chipContainer = findViewById(R.id.chip_group_memory)

        buildFilterChips()

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { searchQuery = s?.toString() ?: ""; applyAll() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loadAll()
    }

    // ── Filter Chips ──

    private fun buildFilterChips() {
        chipContainer.removeAllViews()

        val chips = listOf(
            "all" to "全部",
            "confirmed" to "✅ 确认",
            "pending" to "⏳ 待确认",
            "disabled" to "🚫 禁用",
            "pinned" to "📌 置顶",
            "chat" to "💬 对话",
            "life_record" to "✏️ 生活",
            "diary" to "📖 日记",
            "manual" to "👤 手动"
        )

        for ((key, label) in chips) {
            chipContainer.addView(Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = false
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        if (key == "all") {
                            activeFilters.clear()
                            refreshChipStates()
                        } else {
                            activeFilters.remove("all")
                            activeFilters.add(key)
                            refreshChipStates()
                        }
                    } else {
                        activeFilters.remove(key)
                        if (activeFilters.isEmpty()) {
                            refreshChipStates()
                        }
                    }
                    applyAll()
                }
            })
        }
    }

    private fun refreshChipStates() {
        for (i in 0 until chipContainer.childCount) {
            val chip = chipContainer.getChildAt(i) as? Chip ?: continue
            val keys = listOf("all", "confirmed", "pending", "disabled", "pinned", "chat", "life_record", "diary", "manual")
            val key = keys.getOrNull(i) ?: continue
            chip.isChecked = when (key) {
                "all" -> activeFilters.isEmpty()
                else -> key in activeFilters
            }
        }
    }

    // ── Data Loading ──

    private fun loadAll() {
        lifecycleScope.launch {
            allProfiles = withContext(Dispatchers.IO) { memoryRepo.getAllProfiles() }
            allCards = withContext(Dispatchers.IO) { memoryRepo.getAllCards() }
            allFacts = withContext(Dispatchers.IO) { parseMemoryMd() }
            applyAll()
            loadAuditLog()
        }
    }

    private fun loadAuditLog() {
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) { AuditLogStore.readAll() }
            renderAuditLog(entries.take(10))
        }
    }

    private fun applyAll() {
        val filteredProfiles = filterProfiles(allProfiles)
        val filteredCards = filterCards(allCards)
        val filteredFacts = filterFacts(allFacts)

        val confirmed = filteredProfiles.filter { it.status != "pending" && it.enabled }
        val pending = filteredProfiles.filter { it.status == "pending" }
        val disabledProfiles = filteredProfiles.filter { !it.enabled }

        // Render confirmed + disabled profiles together in profiles section
        renderProfiles(confirmed + disabledProfiles)
        renderPending(pending)
        renderCards(filteredCards)
        renderFacts(filteredFacts)
    }

    // ── Filter Logic ──

    private fun filterProfiles(profiles: List<UserProfileMemory>): List<UserProfileMemory> {
        var result = profiles

        // Status filters
        if (activeFilters.contains("confirmed")) result = result.filter { it.enabled && it.status == "confirmed" }
        if (activeFilters.contains("pending")) result = result.filter { it.status == "pending" }
        if (activeFilters.contains("disabled")) result = result.filter { !it.enabled }

        // Source type filters
        if (activeFilters.any { it in sourceLabelMap.keys && it.isNotEmpty() }) {
            result = result.filter { p -> activeFilters.contains(p.source) }
        }

        // Search
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            result = result.filter { p ->
                p.value.lowercase().contains(q) || p.key.lowercase().contains(q) ||
                p.category.lowercase().contains(q)
            }
        }

        return result
    }

    private fun filterCards(cards: List<MemoryCard>): List<MemoryCard> {
        var result = cards

        if (activeFilters.contains("confirmed")) result = result.filter { it.status == "confirmed" }
        if (activeFilters.contains("pending")) result = result.filter { it.status == "pending" }
        if (activeFilters.contains("disabled")) result = result.filter { it.status == "disabled" }
        if (activeFilters.contains("pinned")) result = result.filter { it.pinned }

        if (activeFilters.any { it in sourceLabelMap.keys && it.isNotEmpty() }) {
            result = result.filter { c -> activeFilters.contains(c.sourceType) }
        }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            result = result.filter { c ->
                c.quote.lowercase().contains(q) || c.note.lowercase().contains(q) ||
                c.tags.any { it.lowercase().contains(q) }
            }
        }

        return result
    }

    private fun filterFacts(facts: List<MemoryFact>): List<MemoryFact> {
        var result = facts

        if (activeFilters.contains("confirmed")) result = result.filter { it.section == "confirmed" }
        if (activeFilters.contains("pending")) result = result.filter { it.section == "pending" }
        if (activeFilters.contains("disabled")) result = result.filter { it.section == "disabled" }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            result = result.filter { it.content.lowercase().contains(q) }
        }

        return result
    }

    // ── User Profiles ──

    private fun renderProfiles(profiles: List<UserProfileMemory>) {
        containerProfiles.removeAllViews()
        if (profiles.isEmpty()) { tvEmptyProfiles.visibility = View.VISIBLE; return }
        tvEmptyProfiles.visibility = View.GONE
        for (p in profiles) {
            containerProfiles.addView(createProfileRow(p))
        }
    }

    private fun createProfileRow(p: UserProfileMemory): View {
        val isDisabled = !p.enabled
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dpToPx() }
            radius = 12.dpToPx().toFloat()
            cardElevation = 1.dpToPx().toFloat()
            setCardBackgroundColor(if (isDisabled) 0xFFE0E0E0.toInt() else 0xFFF5F5F5.toInt())
            setContentPadding(12.dpToPx(), 10.dpToPx(), 12.dpToPx(), 10.dpToPx())
        }

        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val catEmoji = when (p.category) {
            "preference" -> "💡"; "habit" -> "🔄"; "goal" -> "🎯"
            "identity" -> "👤"; "project" -> "📋"; "relationship" -> "👥"
            else -> "📌"
        }

        val statusTag = when {
            !p.enabled -> " 🚫"
            p.status == "pending" -> " ⏳"
            else -> ""
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val titleText = TextView(this).apply {
            text = "$catEmoji ${p.value}$statusTag"
            textSize = 14f
            setTextColor(0xFF333333.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(titleText)

        val switch = SwitchMaterial(this).apply {
            isChecked = p.enabled
            setOnCheckedChangeListener { _, checked ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        memoryRepo.toggleProfile(p.id, checked)
                    }
                    loadAll()
                }
            }
        }
        header.addView(switch)
        row.addView(header)

        val meta = TextView(this).apply {
            text = "${p.category} · 置信度 ${"%.0f".format(p.confidence * 100)}%"
            textSize = 11f; setTextColor(0xFF999999.toInt())
        }
        row.addView(meta)

        // Click for detail
        row.setOnClickListener {
            showProfileDetail(p)
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 6.dpToPx() }
        }
        actions.addView(createActionBtn("编辑") { showEditProfileDialog(p) })
        actions.addView(createActionBtn("删除") {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { memoryRepo.deleteProfile(p.id) }
                loadAll()
            }
        })
        if (isDisabled) {
            actions.addView(createActionBtn("启用") {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { memoryRepo.toggleProfile(p.id, true) }
                    loadAll()
                }
            })
        }
        row.addView(actions)

        card.addView(row)
        return card
    }

    private fun showProfileDetail(p: UserProfileMemory) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        sb.appendLine("${p.key}: ${p.value}")
        sb.appendLine()
        sb.appendLine("分类: ${p.category}")
        sb.appendLine("状态: ${p.status}")
        sb.appendLine("启用: ${if (p.enabled) "是" else "否"}")
        sb.appendLine("置信度: ${"%.0f".format(p.confidence * 100)}%")
        sb.appendLine("创建: ${sdf.format(Date(p.createdAt))}")
        sb.appendLine("更新: ${sdf.format(Date(p.updatedAt))}")
        if (p.source.isNotBlank()) sb.appendLine("来源: ${sourceLabelMap[p.source] ?: p.source}")
        if (p.reason.isNotBlank()) sb.appendLine("原因: ${p.reason}")
        if (p.sourceIds.isNotEmpty()) sb.appendLine("来源ID: ${p.sourceIds.joinToString(", ")}")

        AlertDialog.Builder(this)
            .setTitle("💡 画像记忆详情")
            .setMessage(sb.toString().trim())
            .setPositiveButton("关闭", null)
            .show()
    }

    // ── Pending ──

    private fun renderPending(pending: List<UserProfileMemory>) {
        containerPending.removeAllViews()
        if (pending.isEmpty()) { tvEmptyPending.visibility = View.VISIBLE; return }
        tvEmptyPending.visibility = View.GONE
        for (p in pending) {
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 8.dpToPx() }
                radius = 12.dpToPx().toFloat()
                cardElevation = 1.dpToPx().toFloat()
                setCardBackgroundColor(0xFFFFF8E1.toInt())
                setContentPadding(12.dpToPx(), 10.dpToPx(), 12.dpToPx(), 10.dpToPx())
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val title = TextView(this).apply {
                text = "⏳ ${p.value}"
                textSize = 14f; setTextColor(0xFF333333.toInt())
            }
            row.addView(title)
            val meta = TextView(this).apply {
                text = "${p.category} · 置信度 ${"%.0f".format(p.confidence * 100)}%"
                textSize = 11f; setTextColor(0xFF999999.toInt())
            }
            row.addView(meta)
            if (p.source.isNotBlank()) {
                row.addView(TextView(this).apply {
                    text = "来源：${sourceLabelMap[p.source] ?: p.source}"
                    textSize = 11f; setTextColor(0xFF888888.toInt())
                })
            }
            if (p.reason.isNotBlank()) {
                row.addView(TextView(this).apply {
                    text = "原因：${p.reason}"
                    textSize = 11f; setTextColor(0xFF888888.toInt())
                })
            }

            // Click for detail
            row.setOnClickListener { showProfileDetail(p) }

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 6.dpToPx() }
            }
            actions.addView(createActionBtn("编辑") { showEditProfileDialog(p) })
            actions.addView(createActionBtn("确认") {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { memoryRepo.upsertProfile(p.copy(status = "confirmed")) }
                    loadAll()
                }
            })
            actions.addView(createActionBtn("丢弃") {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { memoryRepo.discardWithDedup(p) }
                    loadAll()
                }
            })
            row.addView(actions)
            card.addView(row)
            containerPending.addView(card)
        }
    }

    // ── Memory Cards ──

    private fun renderCards(cards: List<MemoryCard>) {
        containerCards.removeAllViews()
        if (cards.isEmpty()) { tvEmptyCards.visibility = View.VISIBLE; return }
        tvEmptyCards.visibility = View.GONE
        for (c in cards) {
            containerCards.addView(createCardView(c))
        }
    }

    private fun createCardView(c: MemoryCard): View {
        val isDisabled = c.status == "disabled"
        val isPending = c.status == "pending"
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dpToPx() }
            radius = 12.dpToPx().toFloat()
            cardElevation = 1.dpToPx().toFloat()
            setCardBackgroundColor(when {
                isDisabled -> 0xFFE0E0E0.toInt()
                isPending -> 0xFFFFF8E1.toInt()
                else -> 0xFFF5F5F5.toInt()
            })
            setContentPadding(12.dpToPx(), 10.dpToPx(), 12.dpToPx(), 10.dpToPx())
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val pinTag = if (c.pinned) " 📌" else ""
        val statusTag = when {
            isDisabled -> " 🚫"
            isPending -> " ⏳"
            else -> ""
        }
        val quote = TextView(this).apply {
            text = "💬 ${c.quote}$pinTag$statusTag"
            textSize = 14f; setTextColor(0xFF333333.toInt())
        }
        row.addView(quote)

        if (c.note.isNotBlank()) {
            row.addView(TextView(this).apply {
                text = c.note
                textSize = 12f; setTextColor(0xFF666666.toInt())
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 4.dpToPx() }
            })
        }

        val meta = TextView(this).apply {
            text = "${c.memoryDate} · ${c.mood ?: ""} · ${c.tags.joinToString(", ")}"
            textSize = 11f; setTextColor(0xFF999999.toInt())
        }
        row.addView(meta)

        // Click for detail
        row.setOnClickListener { showCardDetail(c) }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 6.dpToPx() }
        }
        actions.addView(createActionBtn(if (c.pinned) "取消置顶" else "置顶") {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { memoryRepo.updateCard(c.copy(pinned = !c.pinned)) }
                loadAll()
            }
        })
        actions.addView(createActionBtn("编辑") { showEditCardDialog(c) })

        if (isDisabled) {
            // Restore from disabled
            actions.addView(createActionBtn("启用") {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { memoryRepo.updateCard(c.copy(status = "confirmed")) }
                    loadAll()
                }
            })
        }

        actions.addView(createActionBtn("删除") {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { memoryRepo.deleteCard(c.id) }
                loadAll()
            }
        })
        row.addView(actions)
        card.addView(row)
        return card
    }

    private fun showCardDetail(c: MemoryCard) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        if (c.quote.isNotBlank()) sb.appendLine("\"${c.quote}\"")
        if (c.note.isNotBlank()) sb.appendLine(c.note)
        sb.appendLine()
        sb.appendLine("卡片日期: ${c.memoryDate}")
        sb.appendLine("创建时间: ${sdf.format(Date(c.createdAt))}")
        sb.appendLine("来源类型: ${sourceLabelMap[c.sourceType] ?: c.sourceType}")
        sb.appendLine("来源ID: ${c.sourceId}")
        sb.appendLine("状态: ${c.status}")
        sb.appendLine("置信度: ${"%.0f".format(c.confidence * 100)}%")
        sb.appendLine("是否置顶: ${if (c.pinned) "是" else "否"}")
        c.mood?.let { sb.appendLine("心情: $it") }
        c.tags.takeIf { it.isNotEmpty() }?.let { sb.appendLine("标签: ${it.joinToString(", ")}") }

        AlertDialog.Builder(this)
            .setTitle("💬 记忆卡片详情")
            .setMessage(sb.toString().trim())
            .setPositiveButton("关闭", null)
            .show()
    }

    // ── Audit Log ──

    private fun renderAuditLog(entries: List<MemoryGovernanceService.AuditEntry>) {
        containerAuditLog.removeAllViews()
        if (entries.isEmpty()) { tvEmptyAuditLog.visibility = View.VISIBLE; return }
        tvEmptyAuditLog.visibility = View.GONE

        for (entry in entries) {
            val actionEmoji = when (entry.action) {
                "confirm" -> "✅"
                "discard" -> "🗑"
                "disable" -> "🚫"
                "enable" -> "🔄"
                "pin" -> "📌"
                "unpin" -> "📍"
                "edit" -> "✏️"
                else -> "📝"
            }
            val typeLabel = when (entry.memoryType) {
                "profile" -> "画像"
                "memory_card" -> "卡片"
                "memory_md" -> "记忆片段"
                else -> entry.memoryType
            }

            val relativeTime = formatRelativeTime(entry.timestamp)

            val tv = TextView(this).apply {
                text = "$actionEmoji ${actionLabel(entry.action)}$typeLabel: ${entry.summary} · $relativeTime"
                textSize = 12f
                setTextColor(0xFF666666.toInt())
                setPadding(0, 4.dpToPx(), 0, 4.dpToPx())
            }
            containerAuditLog.addView(tv)
        }
    }

    private fun actionLabel(action: String): String = when (action) {
        "confirm" -> "确认"
        "discard" -> "丢弃"
        "disable" -> "禁用"
        "enable" -> "启用"
        "pin" -> "置顶"
        "unpin" -> "取消置顶"
        "edit" -> "编辑"
        else -> "操作"
    }

    private fun formatRelativeTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3_600_000 -> "${diff / 60_000}分钟前"
            diff < 86_400_000 -> "${diff / 3_600_000}小时前"
            else -> "${diff / 86_400_000}天前"
        }
    }

    // ── Memory Facts (MEMORY.md) ──

    data class MemoryFact(val section: String, val content: String)

    private fun parseMemoryMd(): List<MemoryFact> {
        val md = FileStore.readWorkspaceFile("MEMORY.md")
        val parsed = com.example.myapplication.memory.MemoryMdParser.parse(md)

        fun parseSection(text: String, section: String): List<MemoryFact> {
            return text.split("\n")
                .map { it.trim() }
                .filter { it.startsWith("- [") && it.length > 10 }
                .map { MemoryFact(section, it.removePrefix("- ")) }
        }

        val allFacts = mutableListOf<MemoryFact>()
        if (parsed.prelude.isNotBlank()) {
            allFacts.addAll(parseSection(parsed.prelude, "confirmed"))
        }
        allFacts.addAll(parseSection(parsed.confirmed, "confirmed"))
        allFacts.addAll(parseSection(parsed.pending, "pending"))
        allFacts.addAll(parseSection(parsed.disabled, "disabled"))
        return allFacts
    }

    private fun renderFacts(facts: List<MemoryFact>) {
        containerFacts.removeAllViews()
        if (facts.isEmpty()) { tvEmptyFacts.visibility = View.VISIBLE; return }
        tvEmptyFacts.visibility = View.GONE

        val confirmedFacts = facts.filter { it.section == "confirmed" }
        val pendingFacts = facts.filter { it.section == "pending" }
        val disabledFacts = facts.filter { it.section == "disabled" }

        if (confirmedFacts.isNotEmpty()) {
            containerFacts.addView(createSectionHeader("✅ 确认的记忆"))
            for (f in confirmedFacts) {
                containerFacts.addView(createFactCard(f, isConfirmed = true, isDisabled = false))
            }
        }
        if (pendingFacts.isNotEmpty()) {
            containerFacts.addView(createSectionHeader("⏳ 待确认"))
            for (f in pendingFacts) {
                containerFacts.addView(createFactCard(f, isConfirmed = false, isDisabled = false))
            }
        }
        if (disabledFacts.isNotEmpty()) {
            containerFacts.addView(createSectionHeader("🚫 已禁用"))
            for (f in disabledFacts) {
                containerFacts.addView(createFactCard(f, isConfirmed = false, isDisabled = true))
            }
        }
    }

    private fun createSectionHeader(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(0xFF666666.toInt())
            setPadding(0, 16.dpToPx(), 0, 8.dpToPx())
        }
    }

    private fun createFactCard(f: MemoryFact, isConfirmed: Boolean, isDisabled: Boolean): View {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dpToPx() }
            radius = 12.dpToPx().toFloat()
            cardElevation = 1.dpToPx().toFloat()
            setCardBackgroundColor(if (isDisabled) 0xFFE0E0E0.toInt() else 0xFFF5F5F5.toInt())
            setContentPadding(12.dpToPx(), 10.dpToPx(), 12.dpToPx(), 10.dpToPx())
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        row.addView(TextView(this).apply {
            text = f.content
            textSize = 13f; setTextColor(0xFF333333.toInt())
        })
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 6.dpToPx() }
        }

        if (isConfirmed) {
            actions.addView(createActionBtn("禁用") {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { moveMemoryMdFact(f.content, "confirmed", "disabled") }
                    loadAll()
                }
            })
        } else if (isDisabled) {
            actions.addView(createActionBtn("启用") {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { moveMemoryMdFact(f.content, "disabled", "confirmed") }
                    loadAll()
                }
            })
        } else {
            actions.addView(createActionBtn("确认") {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { moveMemoryMdFact(f.content, "pending", "confirmed") }
                    loadAll()
                }
            })
            actions.addView(createActionBtn("禁用") {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { moveMemoryMdFact(f.content, "pending", "disabled") }
                    loadAll()
                }
            })
        }

        actions.addView(createActionBtn("删除") {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { removeMemoryMdFact(f.content) }
                loadAll()
            }
        })
        row.addView(actions)
        card.addView(row)
        return card
    }

    private fun moveMemoryMdFact(factContent: String, fromStatus: String, toStatus: String) {
        val md = FileStore.readWorkspaceFile("MEMORY.md")
        val factLine = "- $factContent"
        val fromSection = when (fromStatus) {
            "confirmed" -> com.example.myapplication.memory.MemoryMdParser.SECTION_CONFIRMED
            "pending" -> com.example.myapplication.memory.MemoryMdParser.SECTION_PENDING
            "disabled" -> com.example.myapplication.memory.MemoryMdParser.SECTION_DISABLED
            else -> return
        }
        val toSection = when (toStatus) {
            "confirmed" -> com.example.myapplication.memory.MemoryMdParser.SECTION_CONFIRMED
            "pending" -> com.example.myapplication.memory.MemoryMdParser.SECTION_PENDING
            "disabled" -> com.example.myapplication.memory.MemoryMdParser.SECTION_DISABLED
            else -> return
        }
        val updated = com.example.myapplication.memory.MemoryMdParser.moveFact(md, factLine, fromSection, toSection)
        FileStore.writeWorkspaceFile("MEMORY.md", updated)
    }

    private fun removeMemoryMdFact(factContent: String) {
        val md = FileStore.readWorkspaceFile("MEMORY.md")
        val factLine = "- $factContent"
        val lines = md.split("\n").toMutableList()
        val toRemove = lines.indexOfFirst { it.trim() == factLine.trim() }
        if (toRemove >= 0) {
            lines.removeAt(toRemove)
            FileStore.writeWorkspaceFile("MEMORY.md", lines.joinToString("\n"))
        }
    }

    // ── Edit Dialogs ──

    private fun showEditProfileDialog(p: UserProfileMemory) {
        val input = EditText(this).apply { setText(p.value); setSingleLine(false) }
        AlertDialog.Builder(this)
            .setTitle("编辑画像记忆")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        memoryRepo.upsertProfile(p.copy(value = input.text.toString().trim()))
                    }
                    loadAll()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEditCardDialog(c: MemoryCard) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(40, 20, 40, 0)
        }
        val quoteInput = EditText(this).apply { setText(c.quote); hint = "引用" }
        val noteInput = EditText(this).apply { setText(c.note); hint = "备注" }
        layout.addView(quoteInput)
        layout.addView(noteInput)
        AlertDialog.Builder(this)
            .setTitle("编辑记忆卡片")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        memoryRepo.updateCard(c.copy(
                            quote = quoteInput.text.toString().trim(),
                            note = noteInput.text.toString().trim()
                        ))
                    }
                    loadAll()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ── Utility ──

    private fun createActionBtn(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, 36.dpToPx()
            ).apply { marginEnd = 8.dpToPx() }
            setOnClickListener { onClick() }
        }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
