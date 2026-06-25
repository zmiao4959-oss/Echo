package com.example.myapplication.ui.diary

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.res.ColorStateList
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DiaryFragment : Fragment() {

    private lateinit var viewModel: DiaryViewModel

    // Today card
    private lateinit var layoutDiaryPreview: View
    private lateinit var layoutNoDiary: View
    private lateinit var layoutGenerating: View
    private lateinit var tvTodayDiaryTitle: TextView
    private lateinit var tvTodayDiarySummary: TextView
    private lateinit var tvTodayDiaryMood: TextView
    private lateinit var tvNoDiaryStatus: TextView
    private lateinit var tvGeneratingStatus: TextView

    // History list
    private lateinit var recyclerDiaries: RecyclerView
    private lateinit var tvDiaryCount: TextView
    private lateinit var tvEmptyDiaries: TextView
    private lateinit var chipContainerMonths: LinearLayout
    private lateinit var btnFilter: TextView

    // Mood chart
    private lateinit var cardMoodChart: MaterialCardView
    private lateinit var moodBarsContainer: LinearLayout
    private lateinit var tvMoodEmpty: TextView
    private lateinit var btnMood7d: TextView
    private lateinit var btnMood30d: TextView

    private var diaryAdapter: DiaryListAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_diary, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory(requireActivity().application as MyApplication)
        )[DiaryViewModel::class.java]

        bindViews(view)
        setupRecycler()
        setupButtons()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadDiaries()
    }

    private fun bindViews(view: View) {
        layoutDiaryPreview = view.findViewById(R.id.layout_diary_preview)
        layoutNoDiary = view.findViewById(R.id.layout_no_diary)
        layoutGenerating = view.findViewById(R.id.layout_generating)
        tvTodayDiaryTitle = view.findViewById(R.id.tv_today_diary_title)
        tvTodayDiarySummary = view.findViewById(R.id.tv_today_diary_summary)
        tvTodayDiaryMood = view.findViewById(R.id.tv_today_diary_mood)
        tvNoDiaryStatus = view.findViewById(R.id.tv_no_diary_status)
        tvGeneratingStatus = view.findViewById(R.id.tv_generating_status)

        recyclerDiaries = view.findViewById(R.id.recycler_diaries)
        tvDiaryCount = view.findViewById(R.id.tv_diary_count)
        tvEmptyDiaries = view.findViewById(R.id.tv_empty_diaries)
        chipContainerMonths = view.findViewById(R.id.chip_container_months)
        btnFilter = view.findViewById(R.id.btn_filter)

        cardMoodChart = view.findViewById(R.id.card_mood_chart)
        moodBarsContainer = view.findViewById(R.id.mood_bars_container)
        tvMoodEmpty = view.findViewById(R.id.tv_mood_empty)
        btnMood7d = view.findViewById(R.id.btn_mood_7d)
        btnMood30d = view.findViewById(R.id.btn_mood_30d)
    }

    private fun setupRecycler() {
        diaryAdapter = DiaryListAdapter { diary ->
            val intent = Intent(requireContext(), DiaryDetailActivity::class.java)
            intent.putExtra("diary_id", diary.id)
            startActivity(intent)
        }
        recyclerDiaries.layoutManager = LinearLayoutManager(requireContext())
        recyclerDiaries.adapter = diaryAdapter
    }

    private fun setupButtons() {
        requireView().findViewById<View>(R.id.btn_generate_diary).setOnClickListener {
            viewModel.generateTodayDiary()
        }
        requireView().findViewById<View>(R.id.btn_view_diary).setOnClickListener {
            viewModel.todayDiary.value?.let { diary ->
                val intent = Intent(requireContext(), DiaryDetailActivity::class.java)
                intent.putExtra("diary_id", diary.id)
                startActivity(intent)
            }
        }
        btnFilter.setOnClickListener { showFilterDialog() }

        btnMood7d.setOnClickListener {
            viewModel.computeMoodStats(7)
        }
        btnMood30d.setOnClickListener {
            viewModel.computeMoodStats(30)
        }
    }

    private fun showFilterDialog() {
        val moods = viewModel.availableMoods.value
        val tags = viewModel.availableTags.value
        if (moods.isEmpty() && tags.isEmpty()) {
            Toast.makeText(requireContext(), "暂无筛选选项", Toast.LENGTH_SHORT).show()
            return
        }
        val items = mutableListOf<String>()
        items.add("全部（清除筛选）")
        if (moods.isNotEmpty()) {
            items.add("── 情绪 ──")
            items.addAll(moods)
        }
        if (tags.isNotEmpty()) {
            items.add("── 标签 ──")
            items.addAll(tags)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("筛选日记")
            .setItems(items.toTypedArray()) { _, which ->
                val selected = items[which]
                when {
                    selected == "全部（清除筛选）" -> viewModel.clearFilters()
                    selected.startsWith("──") -> {} // section header, do nothing
                    moods.contains(selected) -> viewModel.setMoodFilter(selected)
                    tags.contains(selected) -> viewModel.setTagFilter(selected)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun buildMonthChips() {
        chipContainerMonths.removeAllViews()
        val months = viewModel.availableMonths.value
        val selected = viewModel.filterMonth.value

        // "全部" chip
        addMonthChip("全部", null, selected == null)

        for (month in months) {
            val display = formatMonthLabel(month)
            addMonthChip(display, month, month == selected)
        }
    }

    private fun addMonthChip(label: String, monthValue: String?, isSelected: Boolean) {
        val chip = Chip(requireContext())
        chip.text = label
        chip.chipStrokeWidth = 1f
        chip.chipStrokeColor = ColorStateList.valueOf(
            if (isSelected) 0xFF2F7D7A.toInt() else 0xFFE0E0E0.toInt()
        )
        chip.chipBackgroundColor = ColorStateList.valueOf(
            if (isSelected) 0xFF2F7D7A.toInt() else 0xFFFFFFFF.toInt()
        )
        chip.setTextColor(
            ColorStateList.valueOf(if (isSelected) 0xFFFFFFFF.toInt() else 0xFF163536.toInt())
        )
        chip.isCheckable = false
        chip.isClickable = true
        chip.textSize = 13f
        chip.setOnClickListener { viewModel.setMonthFilter(monthValue) }
        chipContainerMonths.addView(chip)
    }

    private fun formatMonthLabel(month: String): String {
        return try {
            val parts = month.split("-")
            "${parts[1].toInt()}月"
        } catch (_: Exception) { month }
    }

    private fun buildMoodBars(stats: List<MoodStat>) {
        moodBarsContainer.removeAllViews()

        cardMoodChart.visibility = View.VISIBLE

        if (stats.isEmpty()) {
            tvMoodEmpty.visibility = View.VISIBLE
            moodBarsContainer.visibility = View.GONE
            return
        }

        tvMoodEmpty.visibility = View.GONE
        moodBarsContainer.visibility = View.VISIBLE

        val maxCount = stats.maxOf { it.count }
        val density = requireContext().resources.displayMetrics.density
        val maxBarWidth = (200 * density).toInt() // max 200dp

        for (stat in stats) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (6 * density).toInt() }
            }

            // mood label
            val label = TextView(requireContext()).apply {
                text = stat.mood
                textSize = 13f
                setTextColor(0xFF163536.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    (72 * density).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            row.addView(label)

            // bar
            val barWidth = ((stat.count.toFloat() / maxCount) * maxBarWidth).toInt().coerceAtLeast((4 * density).toInt())
            val bar = View(requireContext()).apply {
                setBackgroundColor(stat.color)
                layoutParams = LinearLayout.LayoutParams(
                    barWidth,
                    (20 * density).toInt()
                ).apply {
                    marginStart = (8 * density).toInt()
                }
            }
            row.addView(bar)

            // count
            val countText = TextView(requireContext()).apply {
                text = "${stat.count}"
                textSize = 12f
                setTextColor(0xFF9E9E9E.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = (8 * density).toInt() }
            }
            row.addView(countText)

            moodBarsContainer.addView(row)
        }
    }

    private fun updateMoodDaysToggle() {
        val days = viewModel.moodStatsDays.value
        val activeBg = AppCompatResources.getDrawable(requireContext(), R.drawable.bg_send_button)
        val inactiveBg = AppCompatResources.getDrawable(requireContext(), R.drawable.bg_input)
        if (days == 7) {
            btnMood7d.background = activeBg
            btnMood7d.setTextColor(0xFFFFFFFF.toInt())
            btnMood30d.background = inactiveBg
            btnMood30d.setTextColor(0xFF7A7A7A.toInt())
        } else {
            btnMood7d.background = inactiveBg
            btnMood7d.setTextColor(0xFF7A7A7A.toInt())
            btnMood30d.background = activeBg
            btnMood30d.setTextColor(0xFFFFFFFF.toInt())
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.todayDiary.collectLatest { diary ->
                if (diary != null) {
                    layoutDiaryPreview.visibility = View.VISIBLE
                    layoutNoDiary.visibility = View.GONE
                    tvTodayDiaryTitle.text = diary.title
                    tvTodayDiarySummary.text = diary.summary
                    tvTodayDiaryMood.text = diary.mood
                } else {
                    layoutDiaryPreview.visibility = View.GONE
                    layoutNoDiary.visibility = View.VISIBLE
                    val recordCount = viewModel.todayRecords.value.size
                    tvNoDiaryStatus.text = if (recordCount > 0) {
                        getString(R.string.diary_ready_to_generate, recordCount)
                    } else {
                        getString(R.string.diary_no_today)
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.filteredDiaries.collectLatest { diaries ->
                diaryAdapter?.submitList(diaries)
                tvDiaryCount.text = if (diaries.isNotEmpty()) "共 ${diaries.size} 篇日记" else ""
                tvEmptyDiaries.visibility = if (diaries.isEmpty()) View.VISIBLE else View.GONE
                recyclerDiaries.visibility = if (diaries.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        lifecycleScope.launch {
            viewModel.availableMonths.collectLatest { buildMonthChips() }
        }

        lifecycleScope.launch {
            viewModel.moodStats.collectLatest { stats ->
                buildMoodBars(stats)
                updateMoodDaysToggle()
            }
        }

        lifecycleScope.launch {
            viewModel.isGenerating.collectLatest { generating ->
                layoutGenerating.visibility = if (generating) View.VISIBLE else View.GONE
                if (generating) {
                    layoutNoDiary.visibility = View.GONE
                    layoutDiaryPreview.visibility = View.GONE
                }
            }
        }

        lifecycleScope.launch {
            viewModel.statusMessage.collectLatest { msg ->
                if (msg != null) {
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.todayRecords.collectLatest { records ->
                val diary = viewModel.todayDiary.value
                if (diary == null) {
                    tvNoDiaryStatus.text = if (records.isNotEmpty()) {
                        getString(R.string.diary_ready_to_generate, records.size)
                    } else {
                        getString(R.string.diary_no_today)
                    }
                }
            }
        }
    }
}
