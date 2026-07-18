package com.example.myapplication.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.repository.DiaryRepository
import com.example.myapplication.data.repository.LifeRecordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 日历弹窗 — 展示当月每日日记情绪色 / 遗漏提示 / 点击补生成。
 */
class CalendarDialog(
    context: Context,
    private val onGenerateDiary: (date: String) -> Unit
) : Dialog(context) {

    private val diaryRepo = DiaryRepository()
    private val recordRepo = LifeRecordRepository()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val cal = Calendar.getInstance()

    private var currentYear: Int = cal.get(Calendar.YEAR)
    private var currentMonth: Int = cal.get(Calendar.MONTH)  // 0-based

    private lateinit var tvMonthLabel: TextView
    private lateinit var gridDays: LinearLayout
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton

    private var diaries: List<DailyDiary> = emptyList()
    private var recordDates: Set<String> = emptySet()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createView())
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onStart() {
        super.onStart()
        loadMonth()
    }

    private fun createView(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 20.dp, 24.dp, 20.dp)
            background = null // use theme background
        }

        // ── Header ──
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        btnPrev = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            background = null
            setOnClickListener {
                cal.set(currentYear, currentMonth, 1)
                cal.add(Calendar.MONTH, -1)
                currentYear = cal.get(Calendar.YEAR)
                currentMonth = cal.get(Calendar.MONTH)
                loadMonth()
            }
        }
        header.addView(btnPrev, 48.dp, 48.dp)

        tvMonthLabel = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(ThemeColors.textPrimary(context))
        }
        header.addView(tvMonthLabel)

        btnNext = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_media_next)
            background = null
            setOnClickListener {
                cal.set(currentYear, currentMonth, 1)
                cal.add(Calendar.MONTH, 1)
                currentYear = cal.get(Calendar.YEAR)
                currentMonth = cal.get(Calendar.MONTH)
                loadMonth()
            }
        }
        header.addView(btnNext, 48.dp, 48.dp)

        root.addView(header)

        // ── Weekday headers ──
        val weekRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val weekLabels = arrayOf("日", "一", "二", "三", "四", "五", "六")
        for (w in weekLabels) {
            val tv = TextView(context).apply {
                text = w
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(ThemeColors.hint(context))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            weekRow.addView(tv)
        }
        root.addView(weekRow)

        // ── Day grid ──
        gridDays = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(gridDays)

        return root
    }

    private fun loadMonth() {
        tvMonthLabel.text = "${currentYear}年${currentMonth + 1}月"

        val fromDate = dateFormat.format(Calendar.getInstance().apply {
            set(currentYear, currentMonth, 1)
        }.time)
        val toDate = dateFormat.format(Calendar.getInstance().apply {
            set(currentYear, currentMonth, getActualMaximum(Calendar.DAY_OF_MONTH))
        }.time)

        CoroutineScope(Dispatchers.IO).launch {
            diaries = diaryRepo.getByDateRange(fromDate, toDate)
            val records = recordRepo.getByDateRange(fromDate, toDate)
            recordDates = records.map { it.date }.toSet()

            withContext(Dispatchers.Main) {
                buildGrid()
            }
        }
    }

    private fun buildGrid() {
        gridDays.removeAllViews()

        val firstCal = Calendar.getInstance().apply { set(currentYear, currentMonth, 1) }
        val daysInMonth = firstCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = firstCal.get(Calendar.DAY_OF_WEEK)  // 1=Sun

        val diaryMap = diaries.associateBy { it.date }
        val screenW = context.resources.displayMetrics.widthPixels
        val cellSize = ((screenW * 0.92f - 48.dp * 2f) / 7f).toInt()

        val cells = mutableListOf<View>()

        // Empty cells before first day
        for (i in 1 until firstDayOfWeek) {
            cells.add(createDayCell("", Color.TRANSPARENT, false, null, cellSize))
        }

        // Day cells
        for (day in 1..daysInMonth) {
            val cal2 = Calendar.getInstance().apply { set(currentYear, currentMonth, day) }
            val dateStr = dateFormat.format(cal2.time)
            val diary = diaryMap[dateStr]
            val hasRecords = recordDates.contains(dateStr)

            when {
                diary != null -> {
                    cells.add(createDayCell(day.toString(), parseColor(diary.color), false, null, cellSize))
                }
                hasRecords -> {
                    cells.add(createDayCell("?", Color.WHITE, true, dateStr, cellSize))
                }
                else -> {
                    cells.add(createDayCell(day.toString(), Color.WHITE, false, null, cellSize))
                }
            }
        }

        // Fill remaining cells to complete last row
        val totalCells = (firstDayOfWeek - 1) + daysInMonth
        val remaining = if (totalCells % 7 == 0) 0 else 7 - (totalCells % 7)
        for (i in 0 until remaining) {
            cells.add(createDayCell("", Color.TRANSPARENT, false, null, cellSize))
        }

        // Arrange into rows of 7
        for (i in cells.indices step 7) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (j in 0 until 7) {
                if (i + j < cells.size) {
                    row.addView(cells[i + j])
                }
            }
            gridDays.addView(row)
        }
    }

    private fun createDayCell(text: String, bgColor: Int, clickable: Boolean, clickDate: String?, cellSize: Int): View {
        val cell = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(cellSize, cellSize)
        }

        val tv = TextView(context).apply {
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(if (text == "?") 0xFF888888.toInt() else Color.BLACK)
            setBackgroundColor(bgColor)
            layoutParams = FrameLayout.LayoutParams(
                (cellSize * 0.85f).toInt(),
                (cellSize * 0.85f).toInt(),
                Gravity.CENTER
            )
        }

        cell.addView(tv)

        if (clickable && clickDate != null) {
            cell.setOnClickListener {
                dismiss()
                onGenerateDiary(clickDate)
            }
        }

        return cell
    }

    private fun parseColor(hex: String?): Int {
        if (hex == null) return 0xFFEEEEEE.toInt()
        return try {
            val h = hex.removePrefix("#")
            (0xFF000000.toInt() or java.lang.Long.parseLong(h, 16).toInt())
        } catch (_: Exception) {
            0xFFEEEEEE.toInt()
        }
    }

    private val Int.dp: Int get() = (this * context.resources.displayMetrics.density).toInt()
}
