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
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.data.model.EchoForeshadow
import com.example.myapplication.data.model.ForeshadowOutcome
import com.example.myapplication.data.model.ForeshadowState
import com.example.myapplication.policy.PastEchoPolicy
import com.example.myapplication.ui.CardTextureManager
import com.example.myapplication.ui.PageTextureManager
import com.example.myapplication.ui.diary.DiaryDetailActivity
import com.example.myapplication.ui.widget.EchoFeedback
import com.example.myapplication.ui.widget.EchoMemoryMapView
import com.example.myapplication.ui.widget.EchoSheet
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class MemoryFragment : Fragment() {

    private lateinit var viewModel: MemoryViewModel

    // Search
    private lateinit var etSearch: EditText
    private lateinit var layoutSearchResults: View
    private lateinit var layoutDefault: View

    // Default mode
    private lateinit var cardSearchResults: MaterialCardView
    private lateinit var cardRandom: MaterialCardView
    private lateinit var tvRandomQuote: TextView
    private lateinit var tvRandomDate: TextView
    private lateinit var tvNoRandom: TextView
    private lateinit var cardOnThisDay: MaterialCardView
    private lateinit var tvOnThisDayDate: TextView
    private lateinit var tvOnThisDayTitle: TextView
    private lateinit var tvOnThisDaySnippet: TextView
    private lateinit var tvOnThisDayEcho: TextView
    private lateinit var recyclerCards: RecyclerView
    private lateinit var tvEmptyCards: TextView
    private lateinit var recyclerProfiles: RecyclerView
    private lateinit var tvEmptyProfiles: TextView
    private lateinit var memoryStarMap: EchoMemoryMapView

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
        applyCardTextures()
        applyPageTexture()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadMemories()
        viewModel.loadOnThisDay()
        applyCardTextures()
        applyPageTexture()
    }

    private fun applyPageTexture() {
        val config = (requireActivity().application as MyApplication).appConfig
        val key = config.getPageTextureKey(PageTextureManager.MEMORY_PAGE)
        PageTextureManager.apply(requireView(), key, transparentWhenNone = true)
    }

    private fun applyCardTextures() {
        val config = (requireActivity().application as MyApplication).appConfig
        val key = config.getCardTextureKey(CardTextureManager.MEMORY)

        CardTextureManager.apply(cardRandom, key, R.attr.echoSurfaceVariant)
        CardTextureManager.apply(cardOnThisDay, key, R.attr.echoSurface)
        CardTextureManager.apply(cardSearchResults, key, R.attr.echoSurface)
    }

    private fun bindViews(view: View) {
        etSearch = view.findViewById(R.id.et_search)
        layoutSearchResults = view.findViewById(R.id.layout_search_results)
        layoutDefault = view.findViewById(R.id.layout_default)

        cardSearchResults = view.findViewById(R.id.card_search_results)
        cardRandom = view.findViewById(R.id.card_random)
        tvRandomQuote = view.findViewById(R.id.tv_random_quote)
        tvRandomDate = view.findViewById(R.id.tv_random_date)
        tvNoRandom = view.findViewById(R.id.tv_no_random)
        cardOnThisDay = view.findViewById(R.id.card_on_this_day)
        tvOnThisDayDate = view.findViewById(R.id.tv_on_this_day_date)
        tvOnThisDayTitle = view.findViewById(R.id.tv_on_this_day_title)
        tvOnThisDaySnippet = view.findViewById(R.id.tv_on_this_day_snippet)
        tvOnThisDayEcho = view.findViewById(R.id.tv_on_this_day_echo)
        recyclerCards = view.findViewById(R.id.recycler_cards)
        tvEmptyCards = view.findViewById(R.id.tv_empty_cards)
        recyclerProfiles = view.findViewById(R.id.recycler_profiles)
        tvEmptyProfiles = view.findViewById(R.id.tv_empty_profiles)
        memoryStarMap = view.findViewById(R.id.memory_star_map)

        view.findViewById<View>(R.id.btn_clear_search).setOnClickListener {
            clearSearch()
        }
        cardRandom.setOnClickListener {
            viewModel.shuffleRandomCard()
        }
        memoryStarMap.onCardSelected = { card -> showCardDetail(card, null) }
        memoryStarMap.onForeshadowSelected = { thread -> showForeshadowDetail(thread) }
    }

    private fun setupAdapters() {
        cardAdapter = MemoryCardAdapter(
            onClick = { card, source -> showCardDetail(card, source) },
            onLongClick = { card, source -> showCardOptions(card, source) }
        )
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
                    tvRandomDate.text = formatMemoryDate(card.memoryDate)
                    cardRandom.animate().cancel()
                    cardRandom.alpha = 0.45f
                    cardRandom.scaleX = 0.975f
                    cardRandom.scaleY = 0.975f
                    cardRandom.translationY = 6f * resources.displayMetrics.density
                    cardRandom.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationY(0f)
                        .setDuration(240L)
                        .start()
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

        lifecycleScope.launch {
            combine(viewModel.cards, viewModel.foreshadows) { cards, foreshadows ->
                cards to foreshadows
            }.collectLatest { (cards, foreshadows) ->
                memoryStarMap.setContent(cards, foreshadows)
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
                    tvOnThisDayDate.text = getString(
                        R.string.memory_past_echo_meta,
                        item.contextLabel,
                        item.date
                    )
                    tvOnThisDayTitle.text = item.title
                    tvOnThisDaySnippet.text = item.snippet
                    tvOnThisDayEcho.visibility = if (item.microEcho != null) View.VISIBLE else View.GONE
                    tvOnThisDayEcho.text = item.microEcho?.let {
                        getString(R.string.memory_past_echo_spoken, it)
                    }.orEmpty()
                    cardOnThisDay.setOnClickListener {
                        if (item.sourceType == "diary") {
                            val intent = Intent(requireContext(), com.example.myapplication.ui.diary.DiaryDetailActivity::class.java)
                            intent.putExtra("diary_id", item.sourceId)
                            startActivity(intent)
                        } else {
                            showPastEcho(item)
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

    private fun showPastEcho(item: PastEchoPolicy.PastEcho) {
        val details = buildString {
            appendLine(item.date)
            appendLine()
            append(item.snippet)
            item.mood?.let {
                appendLine()
                appendLine()
                append(getString(R.string.memory_past_echo_mood, it))
            }
            item.microEcho?.let {
                appendLine()
                appendLine()
                append(getString(R.string.memory_past_echo_spoken, it))
            }
        }
        EchoSheet.show(
            requireActivity(),
            cardOnThisDay,
            "往日回声",
            item.contextLabel,
            EchoSheet.text(requireActivity(), details, 15f),
            listOf(EchoSheet.Action("收起") { it.dismiss() })
        )
    }

    private fun formatMemoryDate(value: String): String {
        return runCatching {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value)
                ?: return value
            SimpleDateFormat("M月d日", Locale.CHINESE).format(parsed)
        }.getOrDefault(value)
    }

    private fun updateSearchResultUI(result: MemorySearchUiResult) {
        val infoView = requireView().findViewById<TextView>(R.id.tv_search_info)
        infoView.text = "搜索「${result.query}」: ${result.totalCount} 条结果"

        val emptyView = requireView().findViewById<TextView>(R.id.tv_search_empty)
        val cardResults = requireView().findViewById<View>(R.id.card_search_results)
        val resultsText = requireView().findViewById<TextView>(R.id.tv_search_results)

        if (result.totalCount == 0) {
            emptyView.visibility = View.VISIBLE
            cardResults.visibility = View.GONE
            return
        }

        emptyView.visibility = View.GONE
        cardResults.visibility = View.VISIBLE

        val sb = StringBuilder()
        if (result.lifeRecords.isNotEmpty()) {
            sb.appendLine("── 生活片段 ──")
            for (r in result.lifeRecords.take(5)) {
                sb.appendLine("✏️ ${r.content.take(100)}")
                sb.appendLine()
            }
        }
        if (result.diaries.isNotEmpty()) {
            sb.appendLine("── 日记 ──")
            for (d in result.diaries.take(5)) {
                sb.appendLine("📖 ${d.title}")
                sb.appendLine("   ${d.summary.take(80)}")
                sb.appendLine()
            }
        }
        if (result.memoryCards.isNotEmpty()) {
            sb.appendLine("── 记忆卡片 ──")
            for (c in result.memoryCards.take(5)) {
                sb.appendLine("💭 「${c.quote.take(80)}」")
                if (c.note.isNotBlank()) sb.appendLine("   ${c.note.take(60)}")
                sb.appendLine()
            }
        }
        if (result.profileMemories.isNotEmpty()) {
            sb.appendLine("── 长期记忆 ──")
            for (p in result.profileMemories.take(5)) {
                sb.appendLine("💡 ${p.value}")
                sb.appendLine()
            }
        }

        resultsText.text = sb.toString().trim()
    }

    private fun showCardDetail(card: MemoryCard, source: View?) {
        val details = buildString {
            if (card.note.isNotBlank()) append(card.note)
            card.mood?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) appendLine().appendLine()
                append("心情：$it")
            }
            if (card.tags.isNotEmpty()) {
                if (isNotEmpty()) appendLine().appendLine()
                append("标签：${card.tags.joinToString(" · ")}")
            }
            if (isEmpty()) append("这张卡片暂时没有补充说明。")
        }
        val body = EchoSheet.vertical(
            requireActivity(),
            14,
            EchoSheet.text(requireActivity(), details, 15f),
            EchoSheet.text(
                requireActivity(),
                "片段  ───  日记  ───  规划  ───  ✦ 回忆",
                12f,
                secondary = true
            )
        )
        EchoSheet.show(
            requireActivity(),
            source,
            "记忆星图 · ${formatMemoryDate(card.memoryDate)}",
            "「${card.quote}」",
            body,
            listOf(
                EchoSheet.Action("编辑") { renderEditCard(it, card) },
                EchoSheet.Action(if (card.pinned) "取消置顶" else "置顶") {
                    viewModel.togglePin(card.id)
                    EchoFeedback.play(requireView(), EchoFeedback.Kind.COMPLETE)
                    it.dismiss()
                },
                EchoSheet.Action("删除", destructive = true) { renderDeleteCard(it, card) }
            )
        )
    }

    private fun showForeshadowDetail(thread: EchoForeshadow) {
        val dateFormat = SimpleDateFormat("yyyy年M月d日", Locale.CHINESE)
        val stateLabel = when (thread.state) {
            ForeshadowState.WATCHING -> "故事仍在生长"
            ForeshadowState.CLOSED -> when (thread.outcome) {
                ForeshadowOutcome.HAPPENED -> "已经发生"
                ForeshadowOutcome.CHANGED -> "有了变化"
                ForeshadowOutcome.ABANDONED -> "已经放下"
                ForeshadowOutcome.CONTINUING -> "仍在继续"
                null -> "已经有了结局"
            }
            ForeshadowState.DISMISSED -> "不再提起"
        }
        val evidence = thread.sourceRefs
            .sortedBy { it.createdAt }
            .joinToString("\n\n") { source ->
                "${dateFormat.format(java.util.Date(source.createdAt))}\n“${source.excerpt}”"
            }
        val suggestion = thread.suggestedOutcome?.let { outcome ->
            val label = when (outcome) {
                ForeshadowOutcome.HAPPENED -> "似乎已经发生"
                ForeshadowOutcome.CHANGED -> "似乎出现变化"
                ForeshadowOutcome.ABANDONED -> "似乎已经放下"
                ForeshadowOutcome.CONTINUING -> "似乎有了新进展"
            }
            "Echo 从最近的片段里发现：$label。它只是一条线索，结局仍由你确认。"
        }
        val body = EchoSheet.vertical(
            requireActivity(),
            14,
            EchoSheet.text(requireActivity(), stateLabel, 13f, secondary = true),
            EchoSheet.text(requireActivity(), evidence, 15f),
            EchoSheet.text(
                requireActivity(),
                suggestion ?: "虚线会继续等待后来的片段；故事有了答案后，它会变成完整轨迹。",
                13f,
                secondary = true
            )
        )
        val actions = if (thread.state == ForeshadowState.WATCHING) {
            listOf(
                EchoSheet.Action("以后再看") {
                    viewModel.snoozeForeshadow(thread.id)
                    it.dismiss()
                },
                EchoSheet.Action("不再提起", destructive = true) {
                    viewModel.dismissForeshadow(thread.id)
                    it.dismiss()
                }
            )
        } else {
            listOf(EchoSheet.Action("收起") { it.dismiss() })
        }
        EchoSheet.show(
            requireActivity(),
            memoryStarMap,
            "记忆星图 · 伏笔轨迹",
            thread.title,
            body,
            actions
        )
    }

    private fun renderEditCard(session: EchoSheet.Session, card: MemoryCard) {
        val content = layoutInflater.inflate(R.layout.dialog_edit_memory_card, null)
        val quote = content.findViewById<EditText>(R.id.et_memory_quote).apply {
            setText(card.quote)
            setSelection(text.length)
        }
        val note = content.findViewById<EditText>(R.id.et_memory_note).apply { setText(card.note) }
        val mood = content.findViewById<EditText>(R.id.et_memory_mood).apply { setText(card.mood.orEmpty()) }
        val tags = content.findViewById<EditText>(R.id.et_memory_tags).apply {
            setText(card.tags.joinToString("，"))
        }

        session.render(
            "编辑回忆",
            "整理这张记忆",
            content,
            listOf(
                EchoSheet.Action("返回") { it.dismiss() },
                EchoSheet.Action("保存") {
                val newQuote = quote.text.toString().trim()
                if (newQuote.isEmpty()) {
                    quote.error = "想记住的内容不能为空"
                    return@Action
                }
                val newTags = tags.text.toString()
                    .split(Regex("[,，、]"))
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinct()
                    .take(8)
                viewModel.updateCard(
                    card.copy(
                        quote = newQuote,
                        note = note.text.toString().trim(),
                        mood = mood.text.toString().trim().ifEmpty { null },
                        tags = newTags
                    )
                )
                    EchoFeedback.play(requireView(), EchoFeedback.Kind.COMPLETE)
                    it.dismiss()
                }
            )
        )
    }

    private fun renderDeleteCard(session: EchoSheet.Session, card: MemoryCard) {
        session.render(
            "不可撤销",
            "让这颗星熄灭？",
            EchoSheet.text(requireActivity(), "这张记忆卡片会从星图与回忆列表中移除。", 15f),
            listOf(
                EchoSheet.Action("留下") { it.dismiss() },
                EchoSheet.Action("删除", destructive = true) {
                    viewModel.deleteCard(card.id)
                    it.dismiss()
                }
            )
        )
    }

    private fun showCardOptions(card: MemoryCard, source: View?) {
        showCardDetail(card, source)
    }
}
