package com.example.myapplication.ui.memory

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.ui.diary.DiaryDetailActivity
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MemoryFragment : Fragment() {

    private lateinit var viewModel: MemoryViewModel

    // Search
    private lateinit var etSearch: EditText
    private lateinit var layoutSearchResults: View
    private lateinit var layoutDefault: View

    // Default mode
    private lateinit var cardRandom: MaterialCardView
    private lateinit var tvRandomQuote: TextView
    private lateinit var tvRandomDate: TextView
    private lateinit var tvNoRandom: TextView
    private lateinit var cardOnThisDay: MaterialCardView
    private lateinit var tvOnThisDayDate: TextView
    private lateinit var tvOnThisDayTitle: TextView
    private lateinit var tvOnThisDaySnippet: TextView
    private lateinit var recyclerCards: RecyclerView
    private lateinit var tvEmptyCards: TextView
    private lateinit var recyclerProfiles: RecyclerView
    private lateinit var tvEmptyProfiles: TextView

    private var cardAdapter: MemoryCardAdapter? = null
    private var profileAdapter: ProfileMemoryAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_memory, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory(requireActivity().application as MyApplication)
        )[MemoryViewModel::class.java]

        bindViews(view)
        setupAdapters()
        setupSearch()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadMemories()
        viewModel.loadOnThisDay()
    }

    private fun bindViews(view: View) {
        etSearch = view.findViewById(R.id.et_search)
        layoutSearchResults = view.findViewById(R.id.layout_search_results)
        layoutDefault = view.findViewById(R.id.layout_default)

        cardRandom = view.findViewById(R.id.card_random)
        tvRandomQuote = view.findViewById(R.id.tv_random_quote)
        tvRandomDate = view.findViewById(R.id.tv_random_date)
        tvNoRandom = view.findViewById(R.id.tv_no_random)
        cardOnThisDay = view.findViewById(R.id.card_on_this_day)
        tvOnThisDayDate = view.findViewById(R.id.tv_on_this_day_date)
        tvOnThisDayTitle = view.findViewById(R.id.tv_on_this_day_title)
        tvOnThisDaySnippet = view.findViewById(R.id.tv_on_this_day_snippet)
        recyclerCards = view.findViewById(R.id.recycler_cards)
        tvEmptyCards = view.findViewById(R.id.tv_empty_cards)
        recyclerProfiles = view.findViewById(R.id.recycler_profiles)
        tvEmptyProfiles = view.findViewById(R.id.tv_empty_profiles)

        view.findViewById<View>(R.id.btn_clear_search).setOnClickListener {
            clearSearch()
        }
    }

    private fun setupAdapters() {
        cardAdapter = MemoryCardAdapter { card ->
            showCardOptions(card)
        }
        recyclerCards.layoutManager = LinearLayoutManager(requireContext())
        recyclerCards.adapter = cardAdapter

        profileAdapter = ProfileMemoryAdapter { profile ->
            viewModel.toggleProfile(profile.id)
        }
        recyclerProfiles.layoutManager = LinearLayoutManager(requireContext())
        recyclerProfiles.adapter = profileAdapter
    }

    private fun setupSearch() {
        requireView().findViewById<View>(R.id.btn_search).setOnClickListener {
            val query = etSearch.text.toString().trim()
            viewModel.search(query)
        }
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = etSearch.text.toString().trim()
                viewModel.search(query)
                true
            } else false
        }
    }

    private fun clearSearch() {
        etSearch.text.clear()
        viewModel.clearSearch()
    }

    private fun observeViewModel() {
        // Random card
        lifecycleScope.launch {
            viewModel.randomCard.collectLatest { card ->
                if (card != null) {
                    cardRandom.visibility = View.VISIBLE
                    tvNoRandom.visibility = View.GONE
                    tvRandomQuote.text = "「${card.quote}」"
                    tvRandomDate.text = card.memoryDate
                } else {
                    cardRandom.visibility = View.GONE
                    tvNoRandom.visibility = View.VISIBLE
                }
            }
        }

        // Cards list
        lifecycleScope.launch {
            viewModel.cards.collectLatest { cards ->
                cardAdapter?.submitList(cards)
                tvEmptyCards.visibility = if (cards.isEmpty()) View.VISIBLE else View.GONE
                recyclerCards.visibility = if (cards.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        // Profiles
        lifecycleScope.launch {
            viewModel.profiles.collectLatest { profiles ->
                profileAdapter?.submitList(profiles)
                tvEmptyProfiles.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE
                recyclerProfiles.visibility = if (profiles.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        // On this day
        lifecycleScope.launch {
            viewModel.onThisDayItem.collectLatest { item ->
                if (item != null) {
                    cardOnThisDay.visibility = View.VISIBLE
                    tvOnThisDayDate.text = item.date
                    tvOnThisDayTitle.text = item.title
                    tvOnThisDaySnippet.text = item.snippet
                    cardOnThisDay.setOnClickListener {
                        if (item.diaryId != null) {
                            val intent = Intent(requireContext(), com.example.myapplication.ui.diary.DiaryDetailActivity::class.java)
                            intent.putExtra("diary_id", item.diaryId)
                            startActivity(intent)
                        }
                    }
                } else {
                    cardOnThisDay.visibility = View.GONE
                }
            }
        }

        // Search results
        lifecycleScope.launch {
            viewModel.searchResults.collectLatest { result ->
                if (result != null) {
                    layoutSearchResults.visibility = View.VISIBLE
                    layoutDefault.visibility = View.GONE
                    updateSearchResultUI(result)
                } else {
                    layoutSearchResults.visibility = View.GONE
                    layoutDefault.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun updateSearchResultUI(result: MemorySearchUiResult) {
        val infoView = requireView().findViewById<TextView>(R.id.tv_search_info)
        infoView.text = "搜索「${result.query}」: ${result.totalCount} 条结果"

        val emptyView = requireView().findViewById<TextView>(R.id.tv_search_empty)
        emptyView.visibility = if (result.totalCount == 0) View.VISIBLE else View.GONE

        // 简化搜索结果展示（用 TextView 拼接）
        val sb = StringBuilder()
        if (result.lifeRecords.isNotEmpty()) {
            sb.appendLine("── 生活片段 ──")
            for (r in result.lifeRecords.take(3)) {
                sb.appendLine("✏️ ${r.content.take(80)}")
            }
        }
        if (result.diaries.isNotEmpty()) {
            sb.appendLine("\n── 日记 ──")
            for (d in result.diaries.take(3)) {
                sb.appendLine("📖 ${d.title}")
            }
        }
        if (result.memoryCards.isNotEmpty()) {
            sb.appendLine("\n── 记忆卡片 ──")
            for (c in result.memoryCards.take(3)) {
                sb.appendLine("💭 ${c.quote.take(80)}")
            }
        }
        if (result.profileMemories.isNotEmpty()) {
            sb.appendLine("\n── 长期记忆 ──")
            for (p in result.profileMemories.take(3)) {
                sb.appendLine("💡 ${p.value}")
            }
        }

        // Put results in a simple text view
        val resultsText = requireView().findViewById<android.widget.TextView>(R.id.tv_search_empty)
        if (result.totalCount > 0) {
            resultsText.text = sb.toString().trim()
            resultsText.gravity = android.view.Gravity.START or android.view.Gravity.TOP
            resultsText.textSize = 14f
            resultsText.setTextColor(0xFF163536.toInt())
        }
    }

    private fun showCardOptions(card: com.example.myapplication.data.model.MemoryCard) {
        val items = arrayOf(
            if (card.pinned) "取消置顶" else "置顶",
            "删除"
        )
        AlertDialog.Builder(requireContext())
            .setTitle(card.quote.take(30) + "…")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> viewModel.togglePin(card.id)
                    1 -> {
                        AlertDialog.Builder(requireContext())
                            .setTitle("删除记忆卡片")
                            .setMessage("确定要删除这张卡片吗？")
                            .setPositiveButton("删除") { _, _ ->
                                lifecycleScope.launch {
                                    val repo = com.example.myapplication.data.repository.MemoryRepository()
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        repo.deleteCard(card.id)
                                    }
                                    viewModel.loadMemories()
                                }
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                }
            }
            .show()
    }
}
