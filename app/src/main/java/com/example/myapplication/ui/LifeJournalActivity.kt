package com.example.myapplication.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.data.model.LifeJournalIssue
import com.example.myapplication.data.model.LifeJournalPeriod
import com.example.myapplication.data.repository.LifeJournalRepository
import com.example.myapplication.memory.LifeJournalBuilder
import com.example.myapplication.ui.journal.LifeJournalExporter
import com.example.myapplication.ui.journal.LifeJournalPageRenderer
import com.example.myapplication.ui.journal.LifeJournalPageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LifeJournalActivity : ThemedActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var pageView: LifeJournalPageView
    private lateinit var pageLabel: TextView
    private lateinit var status: TextView
    private lateinit var weekChip: TextView
    private lateinit var monthChip: TextView
    private var issue: LifeJournalIssue? = null
    private var period = LifeJournalPeriod.MONTH

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        loadOrGenerate()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(14))
            setBackgroundColor(ThemeColors.background(this@LifeJournalActivity))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(18), bars.top + dp(10), dp(18), bars.bottom + dp(12))
            insets
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("‹", 32f, ThemeColors.textPrimary(this), Gravity.CENTER).apply { setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(44), dp(48)))
        val headText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headText.addView(label("个人生活志", 20f, ThemeColors.textPrimary(this), Gravity.START).apply { setTypeface(typeface, Typeface.BOLD) })
        status = label("正在整理本期材料…", 11f, ThemeColors.hint(this), Gravity.START)
        headText.addView(status)
        header.addView(headText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(label("Echo 重编", 13f, ThemeColors.primary(this), Gravity.CENTER).apply {
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { generate() }
        }, LinearLayout.LayoutParams(dp(78), dp(42)))
        root.addView(header)

        val selector = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        weekChip = chip("本周") { switchPeriod(LifeJournalPeriod.WEEK) }
        monthChip = chip("本月") { switchPeriod(LifeJournalPeriod.MONTH) }
        selector.addView(weekChip, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(5) })
        selector.addView(monthChip, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginStart = dp(5) })
        root.addView(selector, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(4) })
        updateChips()

        val stage = FrameLayout(this).apply { foregroundGravity = Gravity.CENTER }
        pageView = LifeJournalPageView(this).apply {
            elevation = dp(8).toFloat()
            onPageChanged = { updatePageLabel() }
        }
        stage.addView(pageView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(stage, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8); bottomMargin = dp(8) })

        val navigation = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        navigation.addView(action("←") { pageView.pageIndex-- }, LinearLayout.LayoutParams(dp(52), dp(46)))
        pageLabel = label("—", 12f, ThemeColors.hint(this), Gravity.CENTER)
        navigation.addView(pageLabel, LinearLayout.LayoutParams(0, dp(46), 1f))
        navigation.addView(action("→") { pageView.pageIndex++ }, LinearLayout.LayoutParams(dp(52), dp(46)))
        root.addView(navigation)

        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        tools.addView(action("校订本页") { editPage() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        tools.addView(action("导出成刊") { showExport() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
        root.addView(tools)
        return root
    }

    private fun loadOrGenerate() {
        scope.launch {
            val latest = withContext(Dispatchers.IO) { LifeJournalRepository().getLatest() }
            if (latest != null) {
                issue = latest
                period = latest.period
                showIssue(latest)
            } else generate()
        }
    }

    private fun switchPeriod(value: LifeJournalPeriod) {
        if (period == value) return
        period = value
        updateChips()
        scope.launch {
            val window = LifeJournalBuilder.dateWindow(value)
            val saved = withContext(Dispatchers.IO) { LifeJournalRepository().getForPeriod(window.startDate, window.endDate) }
            if (saved != null) showIssue(saved) else generate()
        }
    }

    private fun generate() {
        status.text = "Echo 正在检索记忆并主编本期…"
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { LifeJournalBuilder.build(period, refineCopy = true) } }
                .onSuccess {
                    showIssue(it)
                    Toast.makeText(this@LifeJournalActivity, "本期生活志已重新编排", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    status.text = "生成未完成"
                    Toast.makeText(this@LifeJournalActivity, "生成失败：${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun showIssue(value: LifeJournalIssue) {
        issue = value
        period = value.period
        pageView.issue = value
        status.text = issueStatus(value)
        updateChips()
        updatePageLabel()
    }

    private fun updatePageLabel() {
        val current = issue ?: return
        val index = pageView.pageIndex
        pageLabel.text = when {
            index == 0 -> "封面"
            index == 1 -> "本期序言"
            index == 2 -> "气候与心绪"
            index in 3 until current.chapters.size + 3 -> current.chapters[index - 3].title
            else -> "资料与版本"
        } + "  ·  ${index + 1} / ${LifeJournalPageRenderer.pageCount(current)}"
    }

    private fun editPage() {
        val current = issue ?: return
        val index = pageView.pageIndex
        if (index == 2) {
            Toast.makeText(this, "气候页来自每日心情、心情色与天气记录，不能手动改写", Toast.LENGTH_SHORT).show()
            return
        }
        if (index == LifeJournalPageRenderer.pageCount(current) - 1) {
            Toast.makeText(this, "资料页保留来源口径，不开放改写", Toast.LENGTH_SHORT).show()
            return
        }
        val titleInput = EditText(this).apply {
            setText(when {
                index == 0 -> current.title
                index == 1 -> "本期序言"
                else -> current.chapters[index - 3].title
            })
            setTextColor(ThemeColors.textPrimary(this@LifeJournalActivity))
            setHintTextColor(ThemeColors.hint(this@LifeJournalActivity))
            setSingleLine()
        }
        val bodyInput = EditText(this).apply {
            setText(when {
                index == 0 -> current.subtitle
                index == 1 -> current.overview
                else -> current.chapters[index - 3].body
            })
            setTextColor(ThemeColors.textPrimary(this@LifeJournalActivity))
            setHintTextColor(ThemeColors.hint(this@LifeJournalActivity))
            gravity = Gravity.TOP
            minLines = 5
            maxLines = 12
            setPadding(0, dp(12), 0, 0)
        }
        val itemsInput = if (index in 3 until current.chapters.size + 3) {
            EditText(this).apply {
                setText(current.chapters[index - 3].fragments.joinToString("\n"))
                hint = "本页条目，每行一项；可删除误判或直接改写"
                setTextColor(ThemeColors.textPrimary(this@LifeJournalActivity))
                setHintTextColor(ThemeColors.hint(this@LifeJournalActivity))
                gravity = Gravity.TOP
                minLines = 4
                maxLines = 10
                setPadding(0, dp(14), 0, 0)
            }
        } else null
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
            addView(titleInput)
            addView(bodyInput)
            itemsInput?.let {
                val itemHint = if (current.chapters[index - 3].id == "coordinates") {
                    "本页条目 · 每行一项；保留“人物｜/地点｜”可维持分栏"
                } else {
                    "本页条目 · 每行一项，可删除误判"
                }
                addView(label(itemHint, 11f, ThemeColors.hint(this@LifeJournalActivity), Gravity.START).apply {
                    setPadding(0, dp(14), 0, 0)
                })
                addView(it)
            }
        }
        val scrollBox = android.widget.ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this)
            .setTitle("校订这一页")
            .setView(scrollBox)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                saveEdit(
                    index,
                    titleInput.text.toString(),
                    bodyInput.text.toString(),
                    itemsInput?.text?.lines()?.map(String::trim)?.filter(String::isNotBlank)?.distinct()
                )
            }
            .show()
    }

    private fun saveEdit(index: Int, title: String, body: String, items: List<String>?) {
        val current = issue ?: return
        val now = System.currentTimeMillis()
        val pageId = when (index) {
            0 -> "cover"
            1 -> "overview"
            else -> current.chapters[index - 3].id
        }
        val editedPages = (current.manuallyEditedPages.orEmpty() + pageId).distinct()
        val updated = when {
            index == 0 -> current.copy(title = title.trim(), subtitle = body.trim(), updatedAt = now, revision = current.revision + 1, manuallyEditedPages = editedPages)
            index == 1 -> current.copy(overview = body.trim(), updatedAt = now, revision = current.revision + 1, manuallyEditedPages = editedPages)
            else -> {
                val chapters = current.chapters.toMutableList()
                val chapterIndex = index - 3
                chapters[chapterIndex] = chapters[chapterIndex].copy(
                    title = title.trim(),
                    body = body.trim(),
                    fragments = items ?: chapters[chapterIndex].fragments
                )
                current.copy(chapters = chapters, updatedAt = now, revision = current.revision + 1, manuallyEditedPages = editedPages)
            }
        }
        scope.launch {
            withContext(Dispatchers.IO) { LifeJournalRepository().save(updated) }
            issue = updated
            pageView.issue = updated
            pageView.pageIndex = index
            status.text = issueStatus(updated)
            updatePageLabel()
            Toast.makeText(this@LifeJournalActivity, "已校订；以后 Echo 重编也会保留这一页", Toast.LENGTH_SHORT).show()
        }
    }

    private fun issueStatus(value: LifeJournalIssue): String = buildString {
        append(value.startDate.replace('-', '.')).append('—').append(value.endDate.replace('-', '.'))
        append(" · 第 ").append(value.revision).append(" 版")
        val edited = value.manuallyEditedPages.orEmpty().size
        if (edited > 0) append(" · ").append(edited).append(" 页已校订")
    }

    private fun showExport() {
        val current = issue ?: return
        AlertDialog.Builder(this)
            .setTitle("导出这期生活志")
            .setItems(arrayOf("PDF · 一页一版", "长图 · 连续阅读")) { _, which ->
                status.text = "正在排版导出…"
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            if (which == 0) LifeJournalExporter.exportPdf(this@LifeJournalActivity, current)
                            else LifeJournalExporter.exportLongImage(this@LifeJournalActivity, current)
                        }
                    }.onSuccess { file ->
                        status.text = "导出完成 · ${if (which == 0) "PDF" else "长图"}"
                        LifeJournalExporter.share(this@LifeJournalActivity, file, if (which == 0) "application/pdf" else "image/jpeg")
                    }.onFailure {
                        Toast.makeText(this@LifeJournalActivity, "导出失败：${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.show()
    }

    private fun chip(text: String, onClick: () -> Unit) = label(text, 13f, ThemeColors.textPrimary(this), Gravity.CENTER).apply { setOnClickListener { onClick() } }

    private fun updateChips() {
        if (!::weekChip.isInitialized) return
        listOf(weekChip to LifeJournalPeriod.WEEK, monthChip to LifeJournalPeriod.MONTH).forEach { (view, value) ->
            view.background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(if (period == value) ThemeColors.surfaceVariant(this@LifeJournalActivity) else Color.TRANSPARENT)
                setStroke(dp(1), if (period == value) ThemeColors.primary(this@LifeJournalActivity) else ThemeColors.border(this@LifeJournalActivity))
            }
            view.setTextColor(if (period == value) ThemeColors.primary(this) else ThemeColors.hint(this))
        }
    }

    private fun action(text: String, onClick: () -> Unit) = label(text, 14f, ThemeColors.textPrimary(this), Gravity.CENTER).apply {
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(ThemeColors.surface(this@LifeJournalActivity))
            setStroke(dp(1), ThemeColors.border(this@LifeJournalActivity))
        }
        setOnClickListener { onClick() }
    }

    private fun label(text: String, size: Float, color: Int, gravity: Int) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        this.gravity = gravity
        includeFontPadding = false
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
