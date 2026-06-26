package com.example.myapplication.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.memory.WeeklyReviewBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WeeklyReviewActivity : ThemedActivity() {

    private lateinit var containerContent: LinearLayout
    private lateinit var tvLoading: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weekly_review)

        containerContent = findViewById(R.id.container_review_content)
        tvLoading = findViewById(R.id.tv_review_loading)

        loadReview()
    }

    private fun loadReview() {
        lifecycleScope.launch {
            tvLoading.visibility = View.VISIBLE
            containerContent.removeAllViews()

            val review = withContext(Dispatchers.IO) {
                try { WeeklyReviewBuilder.build() }
                catch (_: Exception) { null }
            }

            tvLoading.visibility = View.GONE

            if (review == null) {
                Toast.makeText(this@WeeklyReviewActivity, "加载失败", Toast.LENGTH_SHORT).show()
                return@launch
            }

            renderReview(review)
        }
    }

    private fun renderReview(review: WeeklyReviewBuilder.WeeklyReview) {
        // Date range + summary
        containerContent.addView(sectionTitle("${review.dateRange}"))

        val summaryText = if (review.isEmpty) "本周暂无记录。" else review.summary
        containerContent.addView(bodyText(summaryText))

        if (review.isEmpty) return

        // Stats
        containerContent.addView(sectionTitle("本周数据"))
        containerContent.addView(bodyText(
            "生活片段: ${review.lifeRecordCount}  ·  日记: ${review.diaryCount}  ·  记忆卡片: ${review.memoryCardCount}"
        ))
        if (review.totalPlanCount > 0) {
            containerContent.addView(bodyText(
                "计划完成: ${review.completedPlanCount}/${review.totalPlanCount}"
            ))
        }

        // Keywords
        if (review.topKeywords.isNotEmpty()) {
            containerContent.addView(sectionTitle("关键词"))
            containerContent.addView(bodyText(review.topKeywords.joinToString(" · ")))
        }

        // Mood
        if (review.dominantMood != null) {
            containerContent.addView(sectionTitle("主要情绪"))
            containerContent.addView(bodyText(review.dominantMood))
        }

        // Limits per section
        val MAX_RECORDS = 10
        val MAX_DIARIES = 5
        val MAX_CARDS = 5
        val MAX_PLANS = 10

        // Life records
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

        // Diaries
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

        // Memory cards
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

        // Plans
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
    }

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

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
