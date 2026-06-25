package com.example.myapplication.ui.diary

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.data.model.DailyDiary
import com.google.android.material.card.MaterialCardView
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
            viewModel.diaries.collectLatest { diaries ->
                val sorted = diaries.sortedByDescending { it.date }
                diaryAdapter?.submitList(sorted)
                tvDiaryCount.text = if (diaries.isNotEmpty()) "共 ${diaries.size} 篇日记" else ""
                tvEmptyDiaries.visibility = if (diaries.isEmpty()) View.VISIBLE else View.GONE
                recyclerDiaries.visibility = if (diaries.isEmpty()) View.GONE else View.VISIBLE
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
