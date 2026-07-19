package com.example.myapplication.ui.journal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.example.myapplication.data.model.LifeJournalChapter
import com.example.myapplication.data.model.LifeJournalIssue
import com.example.myapplication.data.model.LifeJournalMoodPoint
import com.example.myapplication.policy.LifeJournalClimateScale
import com.example.myapplication.ui.ThemeManager
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

object LifeJournalPageRenderer {
    const val PAGE_WIDTH = 1080
    const val PAGE_HEIGHT = 1440

    private const val PANEL_LEFT = 82f
    private const val PANEL_RIGHT = 998f
    private const val CONTENT_LEFT = 128f
    private const val CONTENT_RIGHT = 952f
    private const val CONTENT_WIDTH = CONTENT_RIGHT - CONTENT_LEFT

    /** Cover + editorial + climate + chapters + colophon. */
    fun pageCount(issue: LifeJournalIssue): Int = issue.chapters.size + 4

    fun render(context: Context, canvas: Canvas, issue: LifeJournalIssue, page: Int, phase: Float = 0f) {
        val spec = ThemeManager.specFor(issue.themeKey)
        canvas.save()
        canvas.scale(canvas.width / PAGE_WIDTH.toFloat(), canvas.height / PAGE_HEIGHT.toFloat())
        val base = palette(spec)
        val colors = base.copy(accent = parseColor(issue.primaryColor, base.accent))
        drawWorld(context, canvas, spec, colors, phase)
        when {
            page == 0 -> drawCover(canvas, issue, spec, colors)
            page == 1 -> drawOverview(canvas, issue, spec, colors)
            page == 2 -> drawClimate(canvas, issue, spec, colors)
            page in 3 until issue.chapters.size + 3 -> drawChapter(canvas, issue.chapters[page - 3], spec, colors)
            else -> drawColophon(canvas, issue, spec, colors)
        }
        drawFolio(canvas, page, pageCount(issue), colors)
        canvas.restore()
    }

    private data class Palette(val bg: Int, val ink: Int, val muted: Int, val accent: Int, val accent2: Int, val paper: Int)

    private fun palette(spec: ThemeManager.ThemeSpec): Palette = when (spec.experience) {
        ThemeManager.Experience.PAPER -> Palette(c("#F1EADF"), c("#283A34"), c("#786E61"), c("#B45E42"), c("#668074"), c("#F8F3E9"))
        ThemeManager.Experience.ARCHIVE -> Palette(c("#061521"), c("#E7F3F3"), c("#819DA7"), c("#59D5D7"), c("#2A637D"), c("#0A202E"))
        ThemeManager.Experience.FILM -> Palette(c("#201823"), c("#F5E9DC"), c("#BDA7AE"), c("#E79060"), c("#8F5F79"), c("#302333"))
        ThemeManager.Experience.CEDAR -> Palette(c("#20170F"), c("#F1E4CB"), c("#B9A381"), c("#C5A265"), c("#704A2E"), c("#2B2016"))
        ThemeManager.Experience.TIDE -> Palette(c("#DDECEA"), c("#183C42"), c("#58787A"), c("#4D858B"), c("#A87898"), c("#EDF5F3"))
        ThemeManager.Experience.ORBIT -> Palette(c("#090D18"), c("#EBEEF8"), c("#8790A8"), c("#D0A96B"), c("#6679B2"), c("#11182A"))
        ThemeManager.Experience.GROVE -> Palette(c("#E3E8DE"), c("#263C32"), c("#64756B"), c("#4D6858"), c("#B18762"), c("#F0F2EA"))
        ThemeManager.Experience.INK -> Palette(c("#E6E3DD"), c("#202426"), c("#727575"), c("#78645F"), c("#4C5456"), c("#F2F0EB"))
    }

    private fun drawWorld(context: Context, canvas: Canvas, spec: ThemeManager.ThemeSpec, p: Palette, phase: Float) {
        canvas.drawColor(p.bg)
        spec.surfaceTextureRes?.let { resource ->
            ContextCompat.getDrawable(context, resource)?.let { drawable ->
                drawable.bounds = android.graphics.Rect(0, 0, PAGE_WIDTH, PAGE_HEIGHT)
                drawable.alpha = if (spec.dark) 72 else 88
                drawable.draw(canvas)
            }
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (spec.experience) {
            ThemeManager.Experience.PAPER -> {
                paint.color = alpha(p.accent2, 34); paint.strokeWidth = 1.5f
                for (x in 72..1000 step 38) canvas.drawLine(x.toFloat(), 0f, x + 62f, 1440f, paint)
                paint.pathEffect = DashPathEffect(floatArrayOf(5f, 10f), 0f)
                canvas.drawLine(68f, 0f, 68f, 1440f, paint)
            }
            ThemeManager.Experience.ARCHIVE -> {
                paint.color = alpha(p.accent, 26); paint.strokeWidth = 1f
                for (x in 0..1080 step 72) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), 1440f, paint)
                for (y in 0..1440 step 72) canvas.drawLine(0f, y.toFloat(), 1080f, y.toFloat(), paint)
                paint.style = Paint.Style.STROKE; paint.color = alpha(p.accent, 72); paint.strokeWidth = 2f
                repeat(4) { i -> canvas.drawOval(RectF(770f - i * 32, 35f + i * 18, 1200f + i * 42, 420f + i * 54), paint) }
            }
            ThemeManager.Experience.FILM -> {
                paint.shader = LinearGradient(0f, 0f, 1080f, 1440f, alpha(p.accent2, 4), alpha(p.accent, 70), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, 1080f, 1440f, paint); paint.shader = null
                paint.color = alpha(p.ink, 52)
                for (y in 34..1400 step 82) {
                    canvas.drawRoundRect(15f, y.toFloat(), 48f, y + 48f, 6f, 6f, paint)
                    canvas.drawRoundRect(1032f, y.toFloat(), 1065f, y + 48f, 6f, 6f, paint)
                }
            }
            ThemeManager.Experience.CEDAR -> {
                paint.color = alpha(p.accent2, 22)
                for (y in 0..1440 step 22) canvas.drawLine(0f, y.toFloat(), 1080f, y + 7f, paint)
                paint.color = alpha(p.accent, 180); canvas.drawRect(884f, 0f, 918f, 310f, paint)
                canvas.drawPath(Path().apply { moveTo(884f, 310f); lineTo(901f, 286f); lineTo(918f, 310f); close() }, paint)
            }
            ThemeManager.Experience.TIDE -> {
                repeat(6) { i ->
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 8f + i * 3f
                    paint.color = alpha(if (i % 2 == 0) p.accent else p.accent2, 38 + i * 4)
                    val path = Path().apply { moveTo(-80f, 280f + i * 180) }
                    for (x in -80..1160 step 24) path.lineTo(x.toFloat(), 280f + i * 180 + sin(x / 135f + phase * .7f + i) * (38 + i * 5))
                    canvas.drawPath(path, paint)
                }
            }
            ThemeManager.Experience.ORBIT -> {
                repeat(5) { i ->
                    val radius = 130f + i * 92f
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
                    paint.color = alpha(if (i % 2 == 0) p.accent else p.accent2, 62)
                    canvas.drawOval(RectF(540f - radius, 720f - radius * .55f, 540f + radius, 720f + radius * .55f), paint)
                    val angle = phase * .35f + i * 1.31f
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(540f + cos(angle) * radius, 720f + sin(angle) * radius * .55f, 6f + i * 1.5f, paint)
                }
                repeat(34) { i ->
                    paint.color = alpha(p.ink, 28 + i % 4 * 14)
                    canvas.drawCircle(((i * 193) % 1080).toFloat(), ((i * 307) % 1440).toFloat(), 1f + i % 3, paint)
                }
            }
            ThemeManager.Experience.GROVE -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f; paint.color = alpha(p.accent, 62)
                canvas.drawPath(Path().apply { moveTo(90f, 1510f); cubicTo(140f, 1100f, 40f, 660f, 250f, 180f) }, paint)
                paint.style = Paint.Style.FILL
                repeat(9) { i ->
                    val y = 1260f - i * 125f; val x = 95f + sin(i.toFloat()) * 42f
                    val breathe = 1f + sin(phase * .9f) * .06f
                    canvas.save(); canvas.scale(breathe, breathe, x, y); canvas.drawOval(RectF(x - 42, y - 16, x + 42, y + 16), paint); canvas.restore()
                }
            }
            ThemeManager.Experience.INK -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
                repeat(7) { i ->
                    val x = ((i * 173 + 91) % 1030).toFloat(); val y = ((phase * 96f + i * 257) % 1550f) - 80f
                    paint.color = alpha(p.ink, 34 + i * 4); canvas.drawCircle(x, y, 22f + ((phase * 18 + i * 13) % 55), paint)
                }
                paint.style = Paint.Style.FILL; paint.color = alpha(p.ink, 34); canvas.drawOval(RectF(720f, 1090f, 1190f, 1510f), paint)
            }
        }
    }

    private fun drawCover(canvas: Canvas, issue: LifeJournalIssue, spec: ThemeManager.ThemeSpec, p: Palette) {
        contentPanel(canvas, spec, p, cover = true)
        text(canvas, issue.issueLabel, CONTENT_LEFT, 210f, 27f, p.accent, true, 1.8f)
        val titleY = if (spec.experience == ThemeManager.Experience.ORBIT) 585f else 470f
        wrapped(canvas, issue.title, CONTENT_LEFT, titleY, CONTENT_WIDTH - 30f, 84f, p.ink, 1.06f, 3, true)
        wrapped(canvas, issue.subtitle, CONTENT_LEFT, titleY + 255f, CONTENT_WIDTH - 90f, 27f, p.muted, 1.48f, 3)
        if (issue.keywords.isNotEmpty()) wrapped(canvas, issue.keywords.take(4).joinToString("  /  "), CONTENT_LEFT, 1120f, CONTENT_WIDTH - 80f, 21f, p.muted, 1.45f, 3)
        text(canvas, "ECHO PRIVATE JOURNAL", CONTENT_LEFT, 1262f, 21f, p.accent, true, 2.35f)
    }

    private fun drawOverview(canvas: Canvas, issue: LifeJournalIssue, spec: ThemeManager.ThemeSpec, p: Palette) {
        contentPanel(canvas, spec, p)
        text(canvas, "EDITORIAL / ECHO", CONTENT_LEFT, 184f, 22f, p.accent, true, 2.1f)
        val period = if (issue.period.name == "MONTH") "这个月" else "这一周"
        wrapped(canvas, "$period 真正发生了什么", CONTENT_LEFT, 252f, CONTENT_WIDTH, 55f, p.ink, 1.15f, 3, true)
        wrapped(canvas, issue.overview, CONTENT_LEFT, 430f, CONTENT_WIDTH, 29f, p.ink, 1.72f, 14)

        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(p.muted, 70); strokeWidth = 1f }
        canvas.drawLine(CONTENT_LEFT, 1120f, CONTENT_RIGHT, 1120f, divider)
        text(canvas, "MATERIAL INDEX", CONTENT_LEFT, 1175f, 18f, p.muted, true, 1.8f)
        val total = issue.sourceCounts.values.sum()
        text(canvas, total.toString().padStart(2, '0'), CONTENT_LEFT, 1266f, 51f, p.accent, true, 1f)
        wrapped(canvas, "份本期材料被整理进这一本刊物。叙述可以继续校订，原始记录不会被改写。", CONTENT_LEFT + 112f, 1215f, CONTENT_WIDTH - 112f, 21f, p.muted, 1.52f, 3)
    }

    private fun drawClimate(canvas: Canvas, issue: LifeJournalIssue, spec: ThemeManager.ThemeSpec, p: Palette) {
        contentPanel(canvas, spec, p)
        text(canvas, "CLIMATE OF THE DAYS", CONTENT_LEFT, 176f, 21f, p.accent, true, 2f)
        wrapped(canvas, "气候与心绪", CONTENT_LEFT, 244f, CONTENT_WIDTH, 57f, p.ink, 1.1f, 2, true)
        wrapped(
            canvas,
            "天气使用实际摄氏温度；心情色与心情文字先形成情绪值，再等比映射到同一条 −10—40℃ 纵轴。它们共享尺度，但不互相冒充。",
            CONTENT_LEFT, 330f, CONTENT_WIDTH, 21f, p.muted, 1.48f, 4
        )

        val chartLeft = 188f
        val chartRight = 936f
        val chartTop = 490f
        val chartBottom = 1115f
        val chartHeight = chartBottom - chartTop
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(p.muted, 55); strokeWidth = 1f }
        for (temperature in -10..40 step 10) {
            val y = valueToY(temperature.toFloat(), chartTop, chartHeight)
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            text(canvas, if (temperature > 0) "+$temperature" else "$temperature", CONTENT_LEFT, y + 6f, 16f, p.muted, false, 1f)
        }
        canvas.drawLine(chartLeft, chartTop, chartLeft, chartBottom, gridPaint)

        val mood = issue.moodPoints.filter { it.label.isNotBlank() }
        val weather = issue.moodPoints.filter { it.temperatureC != null }
        val observedStart = issue.moodPoints.minOfOrNull { it.date } ?: issue.startDate
        val observedEnd = issue.moodPoints.maxOfOrNull { it.date } ?: issue.endDate
        val moodPositions = mood.map {
            PointF(dateToX(it.date, observedStart, observedEnd, chartLeft, chartRight), valueToY(LifeJournalClimateScale.moodToTemperature(it.score), chartTop, chartHeight))
        }
        val weatherPositions = weather.map {
            PointF(dateToX(it.date, observedStart, observedEnd, chartLeft, chartRight), valueToY(it.temperatureC ?: 15f, chartTop, chartHeight))
        }

        if (moodPositions.isNotEmpty()) {
            val fill = smoothPath(moodPositions).apply {
                lineTo(moodPositions.last().x, chartBottom)
                lineTo(moodPositions.first().x, chartBottom)
                close()
            }
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, chartTop, 0f, chartBottom, alpha(p.accent, 72), alpha(p.accent, 4), Shader.TileMode.CLAMP)
            }
            canvas.drawPath(fill, fillPaint)
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(p.accent, 210); style = Paint.Style.STROKE; strokeWidth = 4.5f }
            canvas.drawPath(smoothPath(moodPositions), linePaint)
            mood.forEachIndexed { index, point ->
                val pos = moodPositions[index]
                val color = parseColor(point.moodColor, p.accent)
                drawMoodMarker(canvas, pos.x, pos.y, color, spec.experience)
                drawMoodLabel(canvas, point.label, pos, index, p, color)
            }
        } else {
            wrapped(canvas, "这一期还没有足够的心情文字。", chartLeft + 28f, 760f, 560f, 23f, p.muted, 1.4f, 2)
        }

        if (weatherPositions.isNotEmpty()) {
            val weatherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = p.accent2; style = Paint.Style.STROKE; strokeWidth = 3f
                pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
            }
            canvas.drawPath(smoothPath(weatherPositions), weatherPaint)
            weather.forEachIndexed { index, point ->
                val pos = weatherPositions[index]
                val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.paper; style = Paint.Style.FILL }
                canvas.drawCircle(pos.x, pos.y, 8f, marker)
                marker.style = Paint.Style.STROKE; marker.strokeWidth = 3f; marker.color = p.accent2
                canvas.drawCircle(pos.x, pos.y, 8f, marker)
                if (index == 0 || index == weather.lastIndex || index % 4 == 0) {
                    val label = buildString {
                        append((point.temperatureC ?: 0f).toInt()).append('°')
                        point.weatherLabel?.takeIf(String::isNotBlank)?.let { append(' ').append(it.take(4)) }
                    }
                    val offset = if ((index / 4) % 2 == 0) -20f else 34f
                    text(canvas, label, pos.x - 18f, pos.y + offset, 15f, p.accent2, true, 1f)
                }
            }
        }

        val legendY = 1212f
        val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 4f }
        legendPaint.color = p.accent; canvas.drawLine(CONTENT_LEFT, legendY, CONTENT_LEFT + 42f, legendY, legendPaint)
        text(canvas, "心情色 / 情绪等值", CONTENT_LEFT + 58f, legendY + 7f, 17f, p.muted, false, 1f)
        legendPaint.color = p.accent2; legendPaint.pathEffect = DashPathEffect(floatArrayOf(10f, 7f), 0f)
        canvas.drawLine(510f, legendY, 552f, legendY, legendPaint)
        text(canvas, "天气 / 实际温度", 568f, legendY + 7f, 17f, p.muted, false, 1f)
        text(canvas, observedStart.takeLast(5).replace('-', '.'), chartLeft - 8f, 1285f, 15f, p.muted, false, 1f)
        text(canvas, observedEnd.takeLast(5).replace('-', '.'), chartRight - 42f, 1285f, 15f, p.muted, false, 1f)
    }

    private fun drawChapter(canvas: Canvas, chapter: LifeJournalChapter, spec: ThemeManager.ThemeSpec, p: Palette) {
        if (chapter.id == "coordinates") {
            drawCoordinates(canvas, chapter, spec, p)
            return
        }
        contentPanel(canvas, spec, p)
        text(canvas, chapter.eyebrow, CONTENT_LEFT, 170f, 21f, p.accent, true, 2f)
        wrapped(canvas, chapter.title, CONTENT_LEFT, 235f, CONTENT_WIDTH, 59f, p.ink, 1.1f, 2, true)
        wrapped(canvas, chapter.body, CONTENT_LEFT, 360f, CONTENT_WIDTH, 27f, p.ink, 1.65f, 7)
        var y = 650f
        chapter.fragments.take(7).forEachIndexed { index, fragment ->
            drawFragment(canvas, fragment, index, y, spec.experience, p)
            y += if (spec.experience == ThemeManager.Experience.FILM) 102f else 91f
        }
        if (chapter.sourceLabels.isNotEmpty()) {
            wrapped(canvas, "资料口径  ${chapter.sourceLabels.joinToString(" · ")}", CONTENT_LEFT, 1300f, CONTENT_WIDTH, 18f, p.muted, 1.4f, 2)
        }
    }

    private fun drawCoordinates(canvas: Canvas, chapter: LifeJournalChapter, spec: ThemeManager.ThemeSpec, p: Palette) {
        contentPanel(canvas, spec, p)
        text(canvas, chapter.eyebrow, CONTENT_LEFT, 170f, 21f, p.accent, true, 2f)
        wrapped(canvas, chapter.title, CONTENT_LEFT, 235f, CONTENT_WIDTH, 59f, p.ink, 1.1f, 2, true)
        wrapped(canvas, chapter.body, CONTENT_LEFT, 360f, CONTENT_WIDTH, 25f, p.ink, 1.58f, 6)

        val people = chapter.fragments.filter { it.startsWith("人物｜") }.map { it.substringAfter('｜') }
        val places = chapter.fragments.filter { it.startsWith("地点｜") }.map { it.substringAfter('｜') }
        val gap = 34f
        val columnWidth = (CONTENT_WIDTH - gap) / 2f
        drawEntityColumn(canvas, "PEOPLE", "人物", people, CONTENT_LEFT, columnWidth, 650f, spec.experience, p, p.accent)
        drawEntityColumn(canvas, "PLACES", "地点", places, CONTENT_LEFT + columnWidth + gap, columnWidth, 650f, spec.experience, p, p.accent2)

        if (chapter.sourceLabels.isNotEmpty()) {
            wrapped(canvas, "资料口径  ${chapter.sourceLabels.joinToString(" · ")}", CONTENT_LEFT, 1300f, CONTENT_WIDTH, 18f, p.muted, 1.4f, 2)
        }
    }

    private fun drawEntityColumn(
        canvas: Canvas,
        english: String,
        chinese: String,
        items: List<String>,
        left: Float,
        width: Float,
        top: Float,
        experience: ThemeManager.Experience,
        p: Palette,
        accent: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = alpha(accent, if (experience == ThemeManager.Experience.ARCHIVE || experience == ThemeManager.Experience.ORBIT) 45 else 25)
        val corner = if (experience == ThemeManager.Experience.FILM || experience == ThemeManager.Experience.INK) 2f else 24f
        canvas.drawRoundRect(left, top - 42f, left + width, 1238f, corner, corner, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (experience == ThemeManager.Experience.CEDAR) 3f else 1.5f
        paint.color = alpha(accent, 110)
        canvas.drawRoundRect(left, top - 42f, left + width, 1238f, corner, corner, paint)
        text(canvas, english, left + 24f, top + 4f, 17f, accent, true, 2f)
        text(canvas, chinese, left + 24f, top + 48f, 28f, p.ink, true, 1f)
        if (items.isEmpty()) {
            wrapped(canvas, "本期没有可确认条目", left + 24f, top + 126f, width - 48f, 20f, p.muted, 1.5f, 3)
            return
        }
        var y = top + 126f
        items.take(6).forEachIndexed { index, value ->
            paint.style = Paint.Style.FILL
            paint.color = alpha(accent, 150)
            when (experience) {
                ThemeManager.Experience.FILM, ThemeManager.Experience.ARCHIVE -> canvas.drawRect(left + 24f, y - 17f, left + 34f, y - 7f, paint)
                ThemeManager.Experience.ORBIT -> { paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; canvas.drawCircle(left + 29f, y - 12f, 7f + index % 2 * 3, paint) }
                else -> canvas.drawCircle(left + 29f, y - 12f, 5f, paint)
            }
            val name = value.substringBefore(" · ").trim()
            val evidence = value.substringAfter(" · ", "").trim()
            text(canvas, name.take(14), left + 50f, y - 7f, 21f, p.ink, true, 1f)
            if (evidence.isNotBlank()) {
                text(canvas, evidence.take(18), left + 50f, y + 23f, 15f, p.muted, false, 1f)
            }
            y += 86f
        }
    }

    private fun drawColophon(canvas: Canvas, issue: LifeJournalIssue, spec: ThemeManager.ThemeSpec, p: Palette) {
        contentPanel(canvas, spec, p)
        text(canvas, "COLOPHON", CONTENT_LEFT, 188f, 22f, p.accent, true, 2.2f)
        wrapped(canvas, "这一本生活志，只由你留下的材料组成。", CONTENT_LEFT, 280f, CONTENT_WIDTH, 53f, p.ink, 1.24f, 3, true)
        var y = 530f
        issue.sourceCounts.forEach { (label, count) ->
            text(canvas, label, CONTENT_LEFT, y, 25f, p.ink, false, 1f)
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(p.muted, 62); strokeWidth = 1f }
            canvas.drawLine(CONTENT_LEFT + 150f, y - 8f, CONTENT_RIGHT - 155f, y - 8f, line)
            text(canvas, count.toString().padStart(2, '0'), CONTENT_RIGHT - 72f, y, 29f, p.accent, true, 1.3f)
            y += 76f
        }
        wrapped(canvas, "你可以修改封面、序言、章节叙述与条目。校订页会在 Echo 重编时保留，也不会回写原始日记、片段或 Echo 记忆。", CONTENT_LEFT, 1010f, CONTENT_WIDTH, 25f, p.muted, 1.68f, 5)
        text(canvas, "REVISION ${issue.revision.toString().padStart(2, '0')}", CONTENT_LEFT, 1250f, 21f, p.accent, true, 2f)
        text(canvas, "PRIVATE / LOCAL / EDITABLE", CONTENT_LEFT, 1300f, 17f, p.muted, true, 2.2f)
    }

    private fun contentPanel(canvas: Canvas, spec: ThemeManager.ThemeSpec, p: Palette, cover: Boolean = false) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(p.paper, if (spec.dark) 226 else 238) }
        when (spec.experience) {
            ThemeManager.Experience.PAPER -> canvas.drawRect(if (cover) 96f else PANEL_LEFT, 68f, if (cover) 984f else PANEL_RIGHT, 1372f, paint)
            ThemeManager.Experience.ARCHIVE -> { canvas.drawRect(76f, 80f, 1004f, 1360f, paint); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = alpha(p.accent, 90); canvas.drawRect(94f, 98f, 986f, 1342f, paint) }
            ThemeManager.Experience.FILM -> canvas.drawRoundRect(PANEL_LEFT, 100f, PANEL_RIGHT, 1340f, 5f, 5f, paint)
            ThemeManager.Experience.CEDAR -> { canvas.drawRect(102f, 74f, 978f, 1366f, paint); paint.color = alpha(p.accent, 150); canvas.drawRect(102f, 74f, 111f, 1366f, paint) }
            ThemeManager.Experience.TIDE -> canvas.drawRoundRect(72f, 72f, 1008f, 1368f, 68f, 68f, paint)
            ThemeManager.Experience.ORBIT -> canvas.drawRoundRect(PANEL_LEFT, 82f, PANEL_RIGHT, 1358f, 34f, 34f, paint)
            ThemeManager.Experience.GROVE -> canvas.drawRoundRect(105f, 68f, 1000f, 1372f, 170f, 24f, paint)
            ThemeManager.Experience.INK -> canvas.drawRect(96f, 60f, 984f, 1380f, paint)
        }
    }

    private fun drawFragment(canvas: Canvas, fragment: String, index: Int, y: Float, experience: ThemeManager.Experience, p: Palette) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (experience) {
            ThemeManager.Experience.ARCHIVE -> { paint.color = alpha(p.accent, 34); canvas.drawRect(CONTENT_LEFT, y - 40f, CONTENT_RIGHT, y + 42f, paint); text(canvas, (index + 1).toString().padStart(2, '0'), CONTENT_LEFT + 16f, y + 8f, 18f, p.accent, true, 1.4f) }
            ThemeManager.Experience.FILM -> { paint.style = Paint.Style.STROKE; paint.color = alpha(p.accent, 115); paint.strokeWidth = 2f; canvas.drawRect(CONTENT_LEFT, y - 48f, CONTENT_RIGHT, y + 44f, paint) }
            ThemeManager.Experience.CEDAR -> { paint.color = alpha(p.accent, 125); canvas.drawRect(CONTENT_LEFT, y - 30f, CONTENT_LEFT + 5f, y + 26f, paint) }
            ThemeManager.Experience.TIDE -> { paint.color = alpha(if (index % 2 == 0) p.accent else p.accent2, 30); canvas.drawRoundRect(CONTENT_LEFT, y - 46f, CONTENT_RIGHT, y + 40f, 40f, 40f, paint) }
            ThemeManager.Experience.ORBIT -> { paint.style = Paint.Style.STROKE; paint.color = alpha(p.accent, 90); paint.strokeWidth = 2f; canvas.drawCircle(CONTENT_LEFT + 13f, y - 5f, 12f + index % 3 * 4, paint) }
            ThemeManager.Experience.GROVE -> { paint.color = alpha(p.accent, 70); canvas.drawOval(RectF(CONTENT_LEFT, y - 20f, CONTENT_LEFT + 42f, y + 10f), paint) }
            ThemeManager.Experience.INK -> { paint.color = alpha(p.ink, 50 + index * 8); canvas.drawCircle(CONTENT_LEFT + 10f, y - 8f, 8f + index % 3 * 4, paint) }
            ThemeManager.Experience.PAPER -> { paint.color = alpha(p.accent2, 90); canvas.drawRect(CONTENT_LEFT, y - 20f, CONTENT_LEFT + 34f, y - 16f, paint) }
        }
        wrapped(canvas, fragment, CONTENT_LEFT + 58f, y - 24f, CONTENT_WIDTH - 58f, 22f, p.ink, 1.38f, 2)
    }

    private fun drawMoodMarker(canvas: Canvas, x: Float, y: Float, color: Int, experience: ThemeManager.Experience) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        when (experience) {
            ThemeManager.Experience.ARCHIVE, ThemeManager.Experience.FILM -> canvas.drawRect(x - 7f, y - 7f, x + 7f, y + 7f, paint)
            ThemeManager.Experience.CEDAR, ThemeManager.Experience.GROVE -> {
                canvas.drawPath(Path().apply { moveTo(x, y - 10f); lineTo(x + 9f, y); lineTo(x, y + 10f); lineTo(x - 9f, y); close() }, paint)
            }
            ThemeManager.Experience.ORBIT -> { paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f; canvas.drawCircle(x, y, 10f, paint); canvas.drawCircle(x, y, 3f, paint) }
            ThemeManager.Experience.INK -> canvas.drawOval(RectF(x - 7f, y - 11f, x + 7f, y + 9f), paint)
            else -> canvas.drawCircle(x, y, 8f, paint)
        }
    }

    private fun drawMoodLabel(canvas: Canvas, value: String, point: PointF, index: Int, p: Palette, color: Int) {
        val direction = if (index % 2 == 0) -1f else 1f
        val anchorY = point.y + direction * 24f
        canvas.save()
        canvas.rotate(if (direction < 0) -42f else 42f, point.x, anchorY)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = alpha(p.paper, 220); style = Paint.Style.FILL
        }
        val width = 20f + value.length.coerceAtMost(7) * 16f
        canvas.drawRoundRect(point.x - 6f, anchorY - 20f, point.x + width, anchorY + 6f, 8f, 8f, paint)
        text(canvas, value.take(7), point.x + 4f, anchorY, 15f, color, true, 1f)
        canvas.restore()
    }

    private fun smoothPath(points: List<PointF>): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points.first().x, points.first().y)
        if (points.size == 1) return path
        for (index in 0 until points.lastIndex) {
            val p0 = points[(index - 1).coerceAtLeast(0)]
            val p1 = points[index]
            val p2 = points[index + 1]
            val p3 = points[(index + 2).coerceAtMost(points.lastIndex)]
            val c1x = p1.x + (p2.x - p0.x) / 6f
            val c1y = p1.y + (p2.y - p0.y) / 6f
            val c2x = p2.x - (p3.x - p1.x) / 6f
            val c2y = p2.y - (p3.y - p1.y) / 6f
            path.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
        }
        return path
    }

    private fun dateToX(date: String, start: String, end: String, left: Float, right: Float): Float {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val startMs = runCatching { format.parse(start)?.time }.getOrNull() ?: return left
        val endMs = runCatching { format.parse(end)?.time }.getOrNull() ?: return right
        val value = runCatching { format.parse(date)?.time }.getOrNull() ?: startMs
        val fraction = if (endMs <= startMs) .5f else ((value - startMs).toFloat() / (endMs - startMs)).coerceIn(0f, 1f)
        return left + (right - left) * fraction
    }

    private fun valueToY(value: Float, top: Float, height: Float): Float =
        top + height * (1f - LifeJournalClimateScale.temperatureToUnit(value))

    private fun drawFolio(canvas: Canvas, page: Int, count: Int, p: Palette) {
        if (page == 0) return
        text(canvas, "${page.toString().padStart(2, '0')} / ${(count - 1).toString().padStart(2, '0')}", 852f, 1390f, 16f, p.muted, true, 1.5f)
    }

    private fun text(canvas: Canvas, value: String, x: Float, baseline: Float, size: Float, color: Int, bold: Boolean, spacing: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size
            typeface = Typeface.create("sans", if (bold) Typeface.BOLD else Typeface.NORMAL)
            letterSpacing = (spacing - 1f) * .07f
        }
        canvas.drawText(value, x, baseline, paint)
    }

    private fun wrapped(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        width: Float,
        size: Float,
        color: Int,
        lineHeight: Float,
        maxLines: Int,
        bold: Boolean = false
    ): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size
            typeface = Typeface.create("sans", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val lines = mutableListOf<String>()
        value.lines().forEach { paragraph ->
            var current = ""
            paragraph.forEach { character ->
                val next = current + character
                if (paint.measureText(next) > width && current.isNotEmpty()) {
                    lines += current; current = character.toString()
                } else current = next
            }
            if (current.isNotEmpty()) lines += current
        }
        var baseline = y
        lines.take(maxLines).forEachIndexed { index, line ->
            val rendered = if (index == maxLines - 1 && lines.size > maxLines) line.dropLast(1) + "…" else line
            canvas.drawText(rendered, x, baseline, paint)
            baseline += size * lineHeight
        }
        return baseline
    }

    private fun parseColor(value: String?, fallback: Int): Int = runCatching { Color.parseColor(value) }.getOrDefault(fallback)
    private fun c(hex: String): Int = Color.parseColor(hex)
    private fun alpha(color: Int, amount: Int): Int = (color and 0x00FFFFFF) or (amount.coerceIn(0, 255) shl 24)
}
