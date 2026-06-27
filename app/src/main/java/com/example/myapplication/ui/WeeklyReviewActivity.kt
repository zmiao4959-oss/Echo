package com.example.myapplication.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.data.repository.DiaryRepository
import com.example.myapplication.data.repository.MemoryRepository
import com.example.myapplication.memory.WeeklyReviewBuilder
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class WeeklyReviewActivity : ThemedActivity() {

    private lateinit var containerContent: LinearLayout
    private lateinit var tvLoading: TextView
    private lateinit var btnSaveDiary: Button
    private lateinit var btnSaveCard: Button

    private var currentReview: WeeklyReviewBuilder.WeeklyReview? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weekly_review)

        containerContent = findViewById(R.id.container_review_content)
        tvLoading = findViewById(R.id.tv_review_loading)
        btnSaveDiary = findViewById(R.id.btn_save_review_diary)
        btnSaveCard = findViewById(R.id.btn_save_review_card)

        btnSaveDiary.setOnClickListener {
            currentReview?.let { saveAsDiaryDraft(it) }
        }
        btnSaveCard.setOnClickListener {
            currentReview?.let { saveAsMemoryCard(it) }
        }

        // Initially hide save buttons until review is loaded
        btnSaveDiary.visibility = View.GONE
        btnSaveCard.visibility = View.GONE

        loadReview()
    }

    private fun loadReview() {
        lifecycleScope.launch {
            tvLoading.visibility = View.VISIBLE
            containerContent.removeAllViews()

            val review = withContext(Dispatchers.IO) {
                try { WeeklyReviewBuilder.buildWithLLM() }
                catch (_: Exception) { null }
            }

            tvLoading.visibility = View.GONE

            if (review == null) {
                Toast.makeText(this@WeeklyReviewActivity, "加载失败", Toast.LENGTH_SHORT).show()
                return@launch
            }

            currentReview = review
            renderReview(review)
        }
    }

    private fun renderReview(review: WeeklyReviewBuilder.WeeklyReview) {
        // Date range header
        containerContent.addView(sectionTitle("📊 ${review.dateRange}"))

        // When empty, show gentle message and don't show save buttons
        if (review.isEmpty) {
            containerContent.addView(bodyText("本周暂无记录。开始记录你的生活，下周就能看到回顾啦 ✨"))
            btnSaveDiary.visibility = View.GONE
            btnSaveCard.visibility = View.GONE
            return
        }

        // Show save buttons for non-empty reviews
        btnSaveDiary.visibility = View.VISIBLE
        btnSaveCard.visibility = View.VISIBLE

        // ===== Keywords as chips =====
        if (review.topKeywords.isNotEmpty()) {
            containerContent.addView(sectionTitle("本周关键词"))
            val chipGroup = ChipGroup(this).apply {
                setPadding(0, 4.dpToPx(), 0, 8.dpToPx())
            }
            for (kw in review.topKeywords) {
                chipGroup.addView(Chip(this).apply {
                    text = kw
                    isCheckable = false
                    isClickable = false
                })
            }
            containerContent.addView(chipGroup)
        }

        // ===== Echo's restrained summary =====
        if (review.summary.isNotBlank() && review.summary != "本周暂无记录。") {
            containerContent.addView(sectionTitle("Echo 说"))
            containerContent.addView(echoSummaryText(review.summary))
        }

        // ===== Highlight moments =====
        val highlights = collectHighlights(review)
        if (highlights.isNotEmpty()) {
            containerContent.addView(sectionTitle("✨ 本周高光"))
            for (h in highlights.take(3)) {
                containerContent.addView(bodyText("· $h"))
            }
        }

        // ===== Low/stress moments =====
        val lows = collectLows(review)
        if (lows.isNotEmpty()) {
            containerContent.addView(sectionTitle("🌧 本周低谷"))
            for (l in lows.take(2)) {
                containerContent.addView(bodyText("· $l"))
            }
        }

        // ===== Stats =====
        containerContent.addView(sectionTitle("本周数据"))
        containerContent.addView(bodyText(
            "生活片段: ${review.lifeRecordCount}  ·  日记: ${review.diaryCount}  ·  记忆卡片: ${review.memoryCardCount}"
        ))
        if (review.totalPlanCount > 0) {
            containerContent.addView(bodyText(
                "计划完成: ${review.completedPlanCount}/${review.totalPlanCount}"
            ))
        }

        // ===== Mood =====
        if (review.dominantMood != null) {
            containerContent.addView(sectionTitle("主要情绪"))
            containerContent.addView(bodyText(review.dominantMood))
        }

        // ===== Life records =====
        val MAX_RECORDS = 10
        val MAX_DIARIES = 5
        val MAX_CARDS = 5
        val MAX_PLANS = 10

        if (review.lifeRecords.isNotEmpty()) {
            val total = review.lifeRecords.size
            containerContent.addView(sectionTitle("生活片段 ($total)"))
            val byDate = review.lifeRecords.groupBy { it.date }
            var shown = 0
            for ((date, records) in byDate) {
                if (shown >= MAX_RECORDS) break
                containerContent.addView(subTitle("  $date"))
                for (r in records) {
                    if (shown >= MAX_RECORDS) break
                    containerContent.addView(bodyText("  · ${r.content.take(100)}"))
                    shown++
                }
            }
            if (total > MAX_RECORDS) {
                containerContent.addView(bodyText("  还有 ${total - MAX_RECORDS} 条未展示"))
            }
        }

        // ===== Diaries =====
        if (review.diaries.isNotEmpty()) {
            val total = review.diaries.size
            containerContent.addView(sectionTitle("日记 ($total)"))
            review.diaries.take(MAX_DIARIES).forEach { d ->
                containerContent.addView(subTitle("  ${d.date}  ${d.title}"))
                containerContent.addView(bodyText("  ${d.summary.take(120)}"))
            }
            if (total > MAX_DIARIES) {
                containerContent.addView(bodyText("  还有 ${total - MAX_DIARIES} 篇未展示"))
            }
        }

        // ===== Memory cards =====
        if (review.memoryCards.isNotEmpty()) {
            val total = review.memoryCards.size
            containerContent.addView(sectionTitle("记忆卡片 ($total)"))
            review.memoryCards.take(MAX_CARDS).forEach { c ->
                containerContent.addView(subTitle("  ${c.memoryDate}"))
                containerContent.addView(bodyText("  \"${c.quote.take(80)}\""))
            }
            if (total > MAX_CARDS) {
                containerContent.addView(bodyText("  还有 ${total - MAX_CARDS} 张未展示"))
            }
        }

        // ===== Plans =====
        if (review.completedPlans.isNotEmpty()) {
            val total = review.completedPlans.size
            containerContent.addView(sectionTitle("已完成计划 ($total)"))
            review.completedPlans.take(MAX_PLANS).forEach { p ->
                containerContent.addView(bodyText("  ✓ ${p.title}"))
            }
            if (total > MAX_PLANS) {
                containerContent.addView(bodyText("  还有 ${total - MAX_PLANS} 个未展示"))
            }
        }
        if (review.pendingPlans.isNotEmpty()) {
            val total = review.pendingPlans.size
            containerContent.addView(sectionTitle("待完成计划 ($total)"))
            review.pendingPlans.take(MAX_PLANS).forEach { p ->
                containerContent.addView(bodyText("  ○ ${p.title}"))
            }
            if (total > MAX_PLANS) {
                containerContent.addView(bodyText("  还有 ${total - MAX_PLANS} 个未展示"))
            }
        }

        // ===== Source list =====
        containerContent.addView(sectionTitle("数据来源"))
        val sources = mutableListOf<String>()
        if (review.lifeRecordCount > 0) sources.add("${review.lifeRecordCount} 条生活记录")
        if (review.diaryCount > 0) sources.add("${review.diaryCount} 篇日记")
        if (review.memoryCardCount > 0) sources.add("${review.memoryCardCount} 张记忆卡片")
        if (review.totalPlanCount > 0) sources.add("${review.totalPlanCount} 个计划")
        containerContent.addView(bodyText("基于 ${sources.joinToString("、")} 生成"))
    }

    /** Collect highlight moments: positive mood diaries + memory cards. */
    private fun collectHighlights(review: WeeklyReviewBuilder.WeeklyReview): List<String> {
        val highlights = mutableListOf<String>()
        val positiveMoods = setOf("开心", "高兴", "愉快", "充实", "感动", "惊喜", "满足", "兴奋", "放松", "感恩", "期待", "幸福")

        // Positive mood diary snippets
        for (d in review.diaries) {
            val moodLower = d.mood.lowercase()
            if (positiveMoods.any { moodLower.contains(it) }) {
                val snippet = d.summary.ifBlank { d.diaryText.take(80) }
                if (snippet.isNotBlank()) {
                    highlights.add("${d.mood} · ${snippet.take(80)}")
                }
            }
        }

        // Memory cards as highlights
        for (c in review.memoryCards.take(3)) {
            if (c.quote.isNotBlank()) {
                highlights.add("珍藏记忆: \"${c.quote.take(60)}\"")
            }
        }

        return highlights
    }

    /** Collect low/stress moments: negative mood diary snippets. Only if data genuinely indicates it. */
    private fun collectLows(review: WeeklyReviewBuilder.WeeklyReview): List<String> {
        val lows = mutableListOf<String>()
        val negativeMoods = setOf("疲惫", "焦虑", "难过", "伤心", "压力", "烦躁", "沮丧", "生气", "紧张", "失落", "困惑", "累")

        for (d in review.diaries) {
            val moodLower = d.mood.lowercase()
            if (negativeMoods.any { moodLower.contains(it) }) {
                val snippet = d.summary.ifBlank { d.diaryText.take(80) }
                if (snippet.isNotBlank()) {
                    lows.add("${d.mood} · ${snippet.take(80)}")
                }
            }
        }

        return lows
    }

    /** Save weekly review as a diary draft. */
    private fun saveAsDiaryDraft(review: WeeklyReviewBuilder.WeeklyReview) {
        lifecycleScope.launch {
            val diaryRepo = DiaryRepository()
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

            val exists = withContext(Dispatchers.IO) { diaryRepo.hasDiary(dateStr) }
            if (exists) {
                Toast.makeText(this@WeeklyReviewActivity, "今天已有日记，可在日记页编辑", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val diaryText = buildString {
                appendLine("Echo 周回顾 · ${review.dateRange}")
                appendLine()
                appendLine(review.summary)
                if (review.topKeywords.isNotEmpty()) {
                    appendLine()
                    appendLine("关键词: ${review.topKeywords.joinToString("、")}")
                }
                if (review.dominantMood != null) {
                    appendLine("主要情绪: ${review.dominantMood}")
                }
            }

            val diary = DailyDiary(
                id = "weekly_${UUID.randomUUID()}",
                date = dateStr,
                title = "周回顾 · ${review.dateRange}",
                summary = review.summary.take(120),
                diaryText = diaryText,
                mood = review.dominantMood ?: "",
                tags = review.topKeywords.take(5),
                sourceRecordIds = review.lifeRecords.map { it.id },
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            withContext(Dispatchers.IO) { diaryRepo.add(diary) }
            Toast.makeText(this@WeeklyReviewActivity, "已保存为日记", Toast.LENGTH_SHORT).show()
        }
    }

    /** Save weekly review summary as a memory card. */
    private fun saveAsMemoryCard(review: WeeklyReviewBuilder.WeeklyReview) {
        lifecycleScope.launch {
            val memoryRepo = MemoryRepository()
            val card = MemoryCard(
                id = "weekly_card_${UUID.randomUUID()}",
                createdAt = System.currentTimeMillis(),
                memoryDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                quote = if (review.topKeywords.isNotEmpty()) {
                    "关键词: ${review.topKeywords.take(5).joinToString("、")}"
                } else {
                    review.summary.take(80)
                },
                note = review.summary.take(200),
                tags = review.topKeywords.take(5),
                mood = review.dominantMood,
                sourceType = "weekly_review",
                sourceId = "weekly_${review.dateRange}",
                pinned = false,
                confidence = 1.0f,
                status = "confirmed"
            )
            withContext(Dispatchers.IO) { memoryRepo.addCard(card) }
            Toast.makeText(this@WeeklyReviewActivity, "已保存为记忆卡片", Toast.LENGTH_SHORT).show()
        }
    }

    // ── View helpers ──

    private fun sectionTitle(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(0xFF333333.toInt())
            setPadding(0, 20.dpToPx(), 0, 8.dpToPx())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun subTitle(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(0xFF666666.toInt())
            setPadding(0, 4.dpToPx(), 0, 2.dpToPx())
        }

    private fun bodyText(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(0xFF444444.toInt())
            setPadding(0, 2.dpToPx(), 0, 4.dpToPx())
        }

    /** Styled "Echo says" text — visually distinguished from rule-based summary. */
    private fun echoSummaryText(text: String): TextView =
        TextView(this).apply {
            this.text = "\"$text\""
            textSize = 14f
            setTextColor(0xFF2F7D7A.toInt())
            setPadding(12.dpToPx(), 8.dpToPx(), 12.dpToPx(), 12.dpToPx())
            setBackgroundColor(0x152F7D7A.toInt())
        }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
