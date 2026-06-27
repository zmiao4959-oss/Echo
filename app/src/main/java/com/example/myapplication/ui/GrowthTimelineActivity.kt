package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.ui.diary.DiaryDetailActivity
import com.example.myapplication.ui.plan.PlanEditActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Growth timeline page — unified reverse-chronological view of all user content.
 */
class GrowthTimelineActivity : ThemedActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var progressLoading: ProgressBar
    private lateinit var chipContainer: LinearLayout
    private lateinit var viewModel: GrowthTimelineViewModel

    private val adapter = GrowthTimelineAdapter { item -> onItemClick(item) }

    private val typeFilters = listOf(
        "全部" to null,
        "生活记录" to "✏️",
        "日记" to "📖",
        "记忆卡片" to "💬",
        "周回顾" to "📊",
        "计划" to "⏰",
        "画像变更" to "💡"
    )

    private var selectedFilters = mutableSetOf<String>()
        .also { it.addAll(GrowthTimelineViewModel.ALL_TYPES) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_growth_timeline)

        viewModel = ViewModelProvider(this)[GrowthTimelineViewModel::class.java]

        recycler = findViewById(R.id.recycler_timeline)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        tvEmpty = findViewById(R.id.tv_empty_timeline)
        progressLoading = findViewById(R.id.progress_loading)
        chipContainer = findViewById(R.id.chip_group)

        findViewById<TextView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        buildFilterChips()

        // Scroll listener for pagination
        val layoutManager = recycler.layoutManager as LinearLayoutManager
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return  // only trigger on scroll down
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
                if (visibleItemCount + firstVisiblePosition >= totalItemCount - 5) {
                    viewModel.loadMore()
                }
            }
        })

        lifecycleScope.launch {
            viewModel.items.collect { items ->
                adapter.submitList(items)
                tvEmpty.visibility = if (items.isEmpty() && !(viewModel.isLoading.value)) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.isLoading.collect { loading ->
                progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
            }
        }

        viewModel.loadTimeline()
    }

    private fun buildFilterChips() {
        chipContainer.removeAllViews()

        for ((label, emoji) in typeFilters) {
            val chip = Chip(this).apply {
                text = if (emoji != null) "$emoji $label" else label
                isCheckable = true
                isChecked = label == "全部" || selectedFilters.contains(label)
                setOnCheckedChangeListener { _, isChecked ->
                    if (label == "全部") {
                        if (isChecked) {
                            selectedFilters.clear()
                            selectedFilters.addAll(GrowthTimelineViewModel.ALL_TYPES)
                            viewModel.setTypeFilter(selectedFilters.toSet())
                            refreshChipStates()
                        }
                    } else {
                        if (isChecked) {
                            selectedFilters.add(label)
                            // Remove "全部" selection when a specific filter is added
                            refreshChipStates()
                        } else {
                            selectedFilters.remove(label)
                            if (selectedFilters.isEmpty()) {
                                selectedFilters.addAll(GrowthTimelineViewModel.ALL_TYPES)
                                refreshChipStates()
                            }
                        }
                        viewModel.setTypeFilter(selectedFilters.toSet())
                    }
                }
            }
            chipContainer.addView(chip)
        }
    }

    private fun refreshChipStates() {
        for (i in 0 until chipContainer.childCount) {
            val chip = chipContainer.getChildAt(i) as? Chip ?: continue
            val pair = typeFilters.getOrNull(i) ?: continue
            val label = pair.first
            chip.isChecked = if (label == "全部") {
                selectedFilters.containsAll(GrowthTimelineViewModel.ALL_TYPES)
            } else {
                label in selectedFilters
            }
        }
    }

    private fun onItemClick(item: TimelineItem) {
        when (item) {
            is TimelineItem.DiaryItem -> {
                val intent = Intent(this, DiaryDetailActivity::class.java)
                intent.putExtra("diary_id", item.id)
                startActivity(intent)
            }
            is TimelineItem.WeeklyReviewItem -> {
                startActivity(Intent(this, WeeklyReviewActivity::class.java))
            }
            is TimelineItem.PlanItem -> {
                val intent = Intent(this, PlanEditActivity::class.java)
                intent.putExtra("plan_id", item.id)
                startActivity(intent)
            }
            is TimelineItem.MemoryCardItem -> {
                showMemoryCardDetail(item)
            }
            is TimelineItem.LifeRecordItem -> {
                showLifeRecordDetail(item)
            }
            is TimelineItem.ProfileChangeItem -> {
                showProfileChangeDetail(item)
            }
        }
    }

    private fun showMemoryCardDetail(item: TimelineItem.MemoryCardItem) {
        val sb = StringBuilder()
        sb.appendLine("📌 已置顶")  // if pinned, but decorative
        if (item.quote.isNotBlank()) sb.appendLine("\"${item.quote}\"")
        if (item.note.isNotBlank()) sb.appendLine(item.note)
        if (item.mood != null) sb.appendLine("心情: ${item.mood}")
        item.tags.takeIf { it.isNotEmpty() }?.let { sb.appendLine("标签: ${it.joinToString(", ")}") }

        AlertDialog.Builder(this)
            .setTitle("💬 记忆卡片")
            .setMessage(sb.toString().trim())
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun showLifeRecordDetail(item: TimelineItem.LifeRecordItem) {
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        sb.appendLine(item.content)
        sb.appendLine()
        sb.appendLine("来源: ${item.source}")
        item.mood?.let { sb.appendLine("心情: $it") }
        item.tags.takeIf { it.isNotEmpty() }?.let { sb.appendLine("标签: ${it.joinToString(", ")}") }

        AlertDialog.Builder(this)
            .setTitle("✏️ 生活记录 (${sdf.format(Date(item.timestamp))})")
            .setMessage(sb.toString().trim())
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun showProfileChangeDetail(item: TimelineItem.ProfileChangeItem) {
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        sb.appendLine("${item.key}: ${item.value}")
        sb.appendLine("分类: ${item.category}")
        sb.appendLine("操作: ${item.changeType}")
        sb.appendLine("时间: ${sdf.format(Date(item.timestamp))}")

        AlertDialog.Builder(this)
            .setTitle("💡 画像变更")
            .setMessage(sb.toString().trim())
            .setPositiveButton("关闭", null)
            .show()
    }
}
