package com.example.myapplication.ui.memory

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.data.model.UserProfileMemory
import com.example.myapplication.data.repository.MemoryRepository
import com.example.myapplication.memory.FileStore
import com.example.myapplication.ui.ThemedActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MemoryManageActivity : ThemedActivity() {

    private lateinit var containerProfiles: LinearLayout
    private lateinit var containerPending: LinearLayout
    private lateinit var containerCards: LinearLayout
    private lateinit var containerFacts: LinearLayout
    private lateinit var tvEmptyProfiles: TextView
    private lateinit var tvEmptyPending: TextView
    private lateinit var tvEmptyCards: TextView
    private lateinit var tvEmptyFacts: TextView

    private val memoryRepo = MemoryRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memory_manage)

        containerProfiles = findViewById(R.id.container_profiles)
        containerPending = findViewById(R.id.container_pending)
        containerCards = findViewById(R.id.container_cards)
        containerFacts = findViewById(R.id.container_memory_facts)
        tvEmptyProfiles = findViewById(R.id.tv_empty_profiles)
        tvEmptyPending = findViewById(R.id.tv_empty_pending)
        tvEmptyCards = findViewById(R.id.tv_empty_cards)
        tvEmptyFacts = findViewById(R.id.tv_empty_facts)

        loadAll()
    }

    private fun loadAll() {
        lifecycleScope.launch {
            val profiles = withContext(Dispatchers.IO) { memoryRepo.getAllProfiles() }
            val cards = withContext(Dispatchers.IO) { memoryRepo.getAllCards() }
            val memoryFacts = withContext(Dispatchers.IO) { parseMemoryMd() }

            val confirmed = profiles.filter { it.status != "pending" }
            val pending = profiles.filter { it.status == "pending" }

            renderProfiles(confirmed)
            renderPending(pending, profiles)
            renderCards(cards)
            renderFacts(memoryFacts)
        }
    }

    // ── 用户画像 ──

    private fun renderProfiles(profiles: List<UserProfileMemory>) {
        containerProfiles.removeAllViews()
        if (profiles.isEmpty()) { tvEmptyProfiles.visibility = View.VISIBLE; return }
        tvEmptyProfiles.visibility = View.GONE
        for (p in profiles) {
            containerProfiles.addView(createProfileRow(p))
        }
    }

    private fun createProfileRow(p: UserProfileMemory): View {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dpToPx() }
            radius = 12.dpToPx().toFloat()
            cardElevation = 1.dpToPx().toFloat()
            setCardBackgroundColor(0xFFF5F5F5.toInt())
            setContentPadding(12.dpToPx(), 10.dpToPx(), 12.dpToPx(), 10.dpToPx())
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val catEmoji = when (p.category) {
            "preference" -> "💡"; "habit" -> "🔄"; "goal" -> "🎯"
            "identity" -> "👤"; "project" -> "📋"; "relationship" -> "👥"
            else -> "📌"
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val titleText = TextView(this).apply {
            text = "$catEmoji ${p.value}"
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

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 6.dpToPx() }
        }
        actions.addView(createActionBtn("编辑") {
            showEditProfileDialog(p)
        })
        actions.addView(createActionBtn("删除") {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { memoryRepo.deleteProfile(p.id) }
                loadAll()
            }
        })
        row.addView(actions)

        card.addView(row)
        return card
    }

    // ── 待确认 ──

    private fun renderPending(pending: List<UserProfileMemory>, all: List<UserProfileMemory>) {
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
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 6.dpToPx() }
            }
            actions.addView(createActionBtn("确认") {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { memoryRepo.upsertProfile(p.copy(status = "confirmed")) }
                    loadAll()
                }
            })
            actions.addView(createActionBtn("丢弃") {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { memoryRepo.deleteProfile(p.id) }
                    loadAll()
                }
            })
            row.addView(actions)
            card.addView(row)
            containerPending.addView(card)
        }
    }

    // ── 记忆卡片 ──

    private fun renderCards(cards: List<MemoryCard>) {
        containerCards.removeAllViews()
        if (cards.isEmpty()) { tvEmptyCards.visibility = View.VISIBLE; return }
        tvEmptyCards.visibility = View.GONE
        for (c in cards) {
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 8.dpToPx() }
                radius = 12.dpToPx().toFloat()
                cardElevation = 1.dpToPx().toFloat()
                setCardBackgroundColor(0xFFF5F5F5.toInt())
                setContentPadding(12.dpToPx(), 10.dpToPx(), 12.dpToPx(), 10.dpToPx())
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val pinTag = if (c.pinned) " 📌" else ""
            val quote = TextView(this).apply {
                text = "💬 ${c.quote}$pinTag"
                textSize = 14f; setTextColor(0xFF333333.toInt())
            }
            row.addView(quote)
            if (c.note.isNotBlank()) {
                val note = TextView(this).apply {
                    text = c.note
                    textSize = 12f; setTextColor(0xFF666666.toInt())
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 4.dpToPx() }
                }
                row.addView(note)
            }
            val meta = TextView(this).apply {
                text = "${c.memoryDate} · ${c.mood ?: ""} · ${c.tags.joinToString(", ")}"
                textSize = 11f; setTextColor(0xFF999999.toInt())
            }
            row.addView(meta)
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
            actions.addView(createActionBtn("编辑") {
                showEditCardDialog(c)
            })
            actions.addView(createActionBtn("删除") {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { memoryRepo.deleteCard(c.id) }
                    loadAll()
                }
            })
            row.addView(actions)
            card.addView(row)
            containerCards.addView(card)
        }
    }

    // ── 长期记忆片段 (MEMORY.md) ──

    data class MemoryFact(val index: Int, val content: String)

    private fun parseMemoryMd(): List<MemoryFact> {
        val md = FileStore.readWorkspaceFile("MEMORY.md")
        val facts = mutableListOf<MemoryFact>()
        var idx = 0
        for (line in md.split("\n")) {
            val trimmed = line.trim()
            if (trimmed.startsWith("- [") && trimmed.length > 10) {
                facts.add(MemoryFact(idx++, trimmed.removePrefix("- ")))
            }
        }
        return facts
    }

    private fun renderFacts(facts: List<MemoryFact>) {
        containerFacts.removeAllViews()
        if (facts.isEmpty()) { tvEmptyFacts.visibility = View.VISIBLE; return }
        tvEmptyFacts.visibility = View.GONE
        for (f in facts) {
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 8.dpToPx() }
                radius = 12.dpToPx().toFloat()
                cardElevation = 1.dpToPx().toFloat()
                setCardBackgroundColor(0xFFF5F5F5.toInt())
                setContentPadding(12.dpToPx(), 10.dpToPx(), 12.dpToPx(), 10.dpToPx())
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val tv = TextView(this).apply {
                text = f.content
                textSize = 13f; setTextColor(0xFF333333.toInt())
            }
            row.addView(tv)
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 6.dpToPx() }
            }
            actions.addView(createActionBtn("删除") {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        removeMemoryMdFact(f.index)
                    }
                    loadAll()
                }
            })
            row.addView(actions)
            card.addView(row)
            containerFacts.addView(card)
        }
    }

    private fun removeMemoryMdFact(targetIndex: Int) {
        val md = FileStore.readWorkspaceFile("MEMORY.md")
        val lines = md.split("\n").toMutableList()
        var idx = 0
        val toRemove = mutableListOf<Int>()
        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed.startsWith("- [") && trimmed.length > 10) {
                if (idx == targetIndex) toRemove.add(i)
                idx++
            }
        }
        for (i in toRemove.reversed()) lines.removeAt(i)
        FileStore.writeWorkspaceFile("MEMORY.md", lines.joinToString("\n"))
    }

    // ── 编辑对话框 ──

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

    // ── 工具方法 ──

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
