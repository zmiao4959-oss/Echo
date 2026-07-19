package com.example.myapplication.ui.journal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.content.ContextCompat
import com.example.myapplication.data.model.LifeJournalChapter
import com.example.myapplication.data.model.LifeJournalIssue
import com.example.myapplication.ui.ThemeManager
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object LifeJournalPageRenderer {
    const val PAGE_WIDTH = 1080
    const val PAGE_HEIGHT = 1440

    fun pageCount(issue: LifeJournalIssue): Int = issue.chapters.size + 3

    fun render(context: Context, canvas: Canvas, issue: LifeJournalIssue, page: Int, phase: Float = 0f) {
        val spec = ThemeManager.specFor(issue.themeKey)
        val sx = canvas.width / PAGE_WIDTH.toFloat()
        val sy = canvas.height / PAGE_HEIGHT.toFloat()
        canvas.save()
        canvas.scale(sx, sy)
        val basePalette = palette(spec)
        val palette = basePalette.copy(accent = runCatching { Color.parseColor(issue.primaryColor) }.getOrDefault(basePalette.accent))
        drawWorld(context, canvas, spec, palette, phase)
        when {
            page == 0 -> drawCover(canvas, issue, spec, palette)
            page == 1 -> drawOverview(canvas, issue, spec, palette)
            page in 2 until issue.chapters.size + 2 -> drawChapter(canvas, issue.chapters[page - 2], page, spec, palette)
            else -> drawColophon(canvas, issue, spec, palette)
        }
        drawFolio(canvas, page, pageCount(issue), spec, palette)
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
        spec.surfaceTextureRes?.let { res ->
            ContextCompat.getDrawable(context, res)?.let { drawable ->
                drawable.bounds = android.graphics.Rect(0, 0, PAGE_WIDTH, PAGE_HEIGHT)
                drawable.alpha = if (spec.dark) 76 else 92
                drawable.draw(canvas)
            }
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (spec.experience) {
            ThemeManager.Experience.PAPER -> {
                paint.color = alpha(p.accent2, 42); paint.strokeWidth = 2f
                for (x in 76..1000 step 36) canvas.drawLine(x.toFloat(), 0f, x.toFloat() + 70f, 1440f, paint)
                paint.style = Paint.Style.STROKE; paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 9f), 0f)
                canvas.drawLine(70f, 0f, 70f, 1440f, paint)
            }
            ThemeManager.Experience.ARCHIVE -> {
                paint.color = alpha(p.accent, 28); paint.strokeWidth = 1f
                for (x in 0..1080 step 72) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), 1440f, paint)
                for (y in 0..1440 step 72) canvas.drawLine(0f, y.toFloat(), 1080f, y.toFloat(), paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = alpha(p.accent, 80)
                for (i in 0..3) canvas.drawOval(RectF(770f - i * 34, 40f + i * 18, 1190f + i * 50, 410f + i * 60), paint)
            }
            ThemeManager.Experience.FILM -> {
                paint.shader = LinearGradient(0f, 0f, 1080f, 1440f, alpha(p.accent2, 5), alpha(p.accent, 72), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, 1080f, 1440f, paint); paint.shader = null
                paint.color = alpha(p.ink, 55)
                for (y in 34..1400 step 82) { canvas.drawRoundRect(15f, y.toFloat(), 48f, y + 48f, 6f, 6f, paint); canvas.drawRoundRect(1032f, y.toFloat(), 1065f, y + 48f, 6f, 6f, paint) }
            }
            ThemeManager.Experience.CEDAR -> {
                paint.color = alpha(p.accent2, 24)
                for (y in 0..1440 step 22) canvas.drawLine(0f, y.toFloat(), 1080f, y + 7f, paint)
                paint.color = alpha(p.accent, 185); canvas.drawRect(884f, 0f, 918f, 310f, paint)
                val path = Path().apply { moveTo(884f, 310f); lineTo(901f, 286f); lineTo(918f, 310f); close() }
                canvas.drawPath(path, paint)
            }
            ThemeManager.Experience.TIDE -> {
                val t = phase * .7f
                for (i in 0..5) {
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 8f + i * 3f; paint.color = alpha(if (i % 2 == 0) p.accent else p.accent2, 42 + i * 4)
                    val path = Path(); path.moveTo(-80f, 280f + i * 180)
                    for (x in -80..1160 step 24) path.lineTo(x.toFloat(), 280f + i * 180 + sin(x / 135f + t + i) * (38 + i * 5))
                    canvas.drawPath(path, paint)
                }
            }
            ThemeManager.Experience.ORBIT -> {
                val t = phase * .35f
                paint.style = Paint.Style.STROKE
                for (i in 0..4) {
                    paint.strokeWidth = 2f; paint.color = alpha(if (i % 2 == 0) p.accent else p.accent2, 65)
                    val r = 130f + i * 92f; canvas.drawOval(RectF(540f-r, 720f-r*.55f, 540f+r, 720f+r*.55f), paint)
                    val a = t + i * 1.31f; paint.style = Paint.Style.FILL; canvas.drawCircle(540f + cos(a)*r, 720f + sin(a)*r*.55f, 6f+i*1.5f, paint); paint.style = Paint.Style.STROKE
                }
                paint.style = Paint.Style.FILL
                for (i in 0..34) { paint.color = alpha(p.ink, 30 + i % 4 * 16); canvas.drawCircle(((i*193)%1080).toFloat(), ((i*307)%1440).toFloat(), 1f + i%3, paint) }
            }
            ThemeManager.Experience.GROVE -> {
                val breathe = 1f + sin(phase * .9f) * .06f
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f; paint.color = alpha(p.accent, 68)
                val stem = Path().apply { moveTo(90f, 1510f); cubicTo(140f, 1100f, 40f, 660f, 250f, 180f) }; canvas.drawPath(stem, paint)
                paint.style = Paint.Style.FILL
                for (i in 0..8) { val y=1260f-i*125f; val x=95f+sin(i.toFloat())*42f; canvas.save(); canvas.scale(breathe,breathe,x,y); canvas.drawOval(RectF(x-42,y-16,x+42,y+16),paint); canvas.restore() }
            }
            ThemeManager.Experience.INK -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
                val t = phase * .8f
                for (i in 0..6) { val x=((i*173+91)%1030).toFloat(); val y=((t*120+i*257)%1550)-80f; paint.color=alpha(p.ink,38+i*4); canvas.drawCircle(x,y,22f+((t*18+i*13)%55),paint); canvas.drawLine(x,y-125f,x,y-18f,paint) }
                paint.style=Paint.Style.FILL; paint.color=alpha(p.ink,38); canvas.drawOval(RectF(720f,1090f,1190f,1510f),paint)
            }
        }
    }

    private fun drawCover(canvas: Canvas, issue: LifeJournalIssue, spec: ThemeManager.ThemeSpec, p: Palette) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (spec.experience) {
            ThemeManager.Experience.PAPER -> {
                paint.color = p.paper; canvas.drawRect(96f, 92f, 984f, 1348f, paint)
                paint.style=Paint.Style.STROKE; paint.strokeWidth=3f; paint.color=p.accent2; canvas.drawRect(126f,122f,954f,1318f,paint)
            }
            ThemeManager.Experience.ARCHIVE -> { paint.style=Paint.Style.STROKE; paint.strokeWidth=2f; paint.color=p.accent; canvas.drawRect(72f,72f,1008f,1368f,paint); canvas.drawRect(92f,92f,422f,262f,paint) }
            ThemeManager.Experience.FILM -> { paint.color=alpha(p.paper,215); canvas.drawRoundRect(92f,170f,988f,1250f,4f,4f,paint); paint.color=p.accent; canvas.drawRect(92f,170f,988f,305f,paint) }
            ThemeManager.Experience.CEDAR -> { paint.color=alpha(p.paper,210); canvas.drawRect(126f,104f,948f,1336f,paint); paint.color=p.accent2; canvas.drawRect(126f,104f,162f,1336f,paint) }
            ThemeManager.Experience.TIDE -> { paint.color=alpha(p.paper,205); canvas.drawRoundRect(70f,80f,1010f,1360f,76f,76f,paint) }
            ThemeManager.Experience.ORBIT -> { paint.style=Paint.Style.STROKE; paint.strokeWidth=2f; paint.color=p.accent; canvas.drawCircle(540f,710f,430f,paint); canvas.drawCircle(540f,710f,350f,paint) }
            ThemeManager.Experience.GROVE -> { paint.color=alpha(p.paper,218); canvas.drawRoundRect(125f,105f,975f,1345f,220f,30f,paint) }
            ThemeManager.Experience.INK -> { paint.color=alpha(p.paper,210); canvas.drawRect(110f,70f,970f,1370f,paint); paint.color=p.ink; canvas.drawOval(RectF(700f,70f,970f,360f),paint) }
        }
        val ink = if (spec.experience == ThemeManager.Experience.FILM) p.ink else if (spec.dark && spec.experience != ThemeManager.Experience.FILM && spec.experience != ThemeManager.Experience.CEDAR) p.ink else p.ink
        val left = if (spec.experience == ThemeManager.Experience.ARCHIVE) 110f else 155f
        text(canvas, issue.issueLabel, left, 215f, 28f, p.accent, true, 1.8f)
        val titleY = when (spec.experience) { ThemeManager.Experience.ORBIT -> 610f; ThemeManager.Experience.TIDE -> 510f; else -> 480f }
        wrapped(canvas, issue.title, left, titleY, 760f, 86f, ink, 1.05f, 3, true)
        wrapped(canvas, issue.subtitle, left, titleY + 250f, 700f, 28f, p.muted, 1.45f, 3)
        if (issue.keywords.isNotEmpty()) wrapped(canvas, issue.keywords.take(4).joinToString("  /  "), left, 1120f, 720f, 22f, p.muted, 1.45f, 3)
        text(canvas, "ECHO PRIVATE JOURNAL", left, 1264f, 22f, p.accent, true, 2.4f)
    }

    private fun drawOverview(canvas: Canvas, issue: LifeJournalIssue, spec: ThemeManager.ThemeSpec, p: Palette) {
        contentPanel(canvas, spec, p)
        text(canvas, "EDITOR'S NOTE", 128f, 190f, 24f, p.accent, true, 2f)
        wrapped(canvas, "这个${if (issue.period.name == "MONTH") "月" else "星期"}真正发生了什么", 128f, 252f, 820f, 56f, p.ink, 1.18f, 3, true)
        wrapped(canvas, issue.overview, 128f, 420f, 815f, 30f, p.ink, 1.75f, 11)
        text(canvas, "情绪并不是分数，而是一条留下呼吸的编辑线。", 128f, 935f, 22f, p.muted, false, 1f)
        drawMoodRibbon(canvas, issue, p, 128f, 1010f, 824f, 210f)
        val weather = if (issue.weatherNotes.isEmpty()) "天气线索 · 本期原文未提及" else "天气线索 · ${issue.weatherNotes.joinToString("、")}"
        text(canvas, weather, 128f, 1300f, 21f, p.muted, false, 1f)
    }

    private fun drawChapter(canvas: Canvas, chapter: LifeJournalChapter, page: Int, spec: ThemeManager.ThemeSpec, p: Palette) {
        contentPanel(canvas, spec, p)
        val left = if (spec.experience == ThemeManager.Experience.ARCHIVE) 105f else 128f
        text(canvas, chapter.eyebrow, left, 170f, 22f, p.accent, true, 2f)
        wrapped(canvas, chapter.title, left, 230f, 780f, 62f, p.ink, 1.1f, 2, true)
        wrapped(canvas, chapter.body, left, 355f, 820f, 29f, p.ink, 1.68f, 6)
        var y = 620f
        chapter.fragments.take(7).forEachIndexed { index, fragment ->
            paintFragment(canvas, fragment, index, left, y, spec, p)
            y += if (spec.experience == ThemeManager.Experience.FILM) 102f else 92f
        }
        if (chapter.sourceLabels.isNotEmpty()) {
            wrapped(canvas, "资料口径  ${chapter.sourceLabels.joinToString(" · ")}", left, 1300f, 790f, 19f, p.muted, 1.4f, 2)
        }
    }

    private fun drawColophon(canvas: Canvas, issue: LifeJournalIssue, spec: ThemeManager.ThemeSpec, p: Palette) {
        contentPanel(canvas, spec, p)
        text(canvas, "COLOPHON", 128f, 190f, 23f, p.accent, true, 2.2f)
        wrapped(canvas, "这一本生活志，只由你留下的材料组成。", 128f, 270f, 800f, 54f, p.ink, 1.25f, 3, true)
        var y = 520f
        issue.sourceCounts.forEach { (label, count) ->
            text(canvas, label, 128f, y, 26f, p.ink, false, 1f)
            text(canvas, count.toString().padStart(2, '0'), 800f, y, 30f, p.accent, true, 1.4f)
            val line=Paint().apply{color=alpha(p.muted,75);strokeWidth=1f}; canvas.drawLine(260f,y-8f,765f,y-8f,line); y += 82f
        }
        wrapped(canvas, "你可以修改封面标题、总述和每个章节。修改后的文字会作为这一版生活志保存，不回写原始日记与片段。", 128f, 920f, 790f, 27f, p.muted, 1.7f, 5)
        text(canvas, "REVISION ${issue.revision.toString().padStart(2,'0')}", 128f, 1240f, 22f, p.accent, true, 2f)
        text(canvas, "PRIVATE / LOCAL / EDITABLE", 128f, 1295f, 18f, p.muted, true, 2.2f)
    }

    private fun contentPanel(canvas: Canvas, spec: ThemeManager.ThemeSpec, p: Palette) {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=alpha(p.paper, if(spec.dark) 225 else 236)}
        when(spec.experience){
            ThemeManager.Experience.ARCHIVE -> canvas.drawRect(76f,80f,1004f,1360f,paint)
            ThemeManager.Experience.FILM -> canvas.drawRoundRect(82f,100f,998f,1340f,5f,5f,paint)
            ThemeManager.Experience.CEDAR -> canvas.drawRect(102f,74f,978f,1366f,paint)
            ThemeManager.Experience.TIDE -> canvas.drawRoundRect(72f,72f,1008f,1368f,68f,68f,paint)
            ThemeManager.Experience.ORBIT -> canvas.drawRoundRect(82f,82f,998f,1358f,34f,34f,paint)
            ThemeManager.Experience.GROVE -> canvas.drawRoundRect(105f,68f,1000f,1372f,170f,24f,paint)
            ThemeManager.Experience.INK -> canvas.drawRect(96f,60f,984f,1380f,paint)
            else -> canvas.drawRect(92f,68f,988f,1372f,paint)
        }
    }

    private fun paintFragment(canvas: Canvas, fragment: String, index: Int, x: Float, y: Float, spec: ThemeManager.ThemeSpec, p: Palette) {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        when(spec.experience){
            ThemeManager.Experience.ARCHIVE -> { paint.color=alpha(p.accent,38); canvas.drawRect(x,y-40f,925f,y+42f,paint); text(canvas,(index+1).toString().padStart(2,'0'),x+16,y+8f,19f,p.accent,true,1.4f) }
            ThemeManager.Experience.FILM -> { paint.style=Paint.Style.STROKE;paint.color=alpha(p.accent,120);paint.strokeWidth=2f;canvas.drawRect(x,y-48f,925f,y+44f,paint) }
            ThemeManager.Experience.CEDAR -> { paint.color=alpha(p.accent,120);canvas.drawRect(x,y-30f,x+5f,y+26f,paint) }
            ThemeManager.Experience.TIDE -> { paint.color=alpha(if(index%2==0)p.accent else p.accent2,32);canvas.drawRoundRect(x,y-46f,935f,y+40f,40f,40f,paint) }
            ThemeManager.Experience.ORBIT -> { paint.style=Paint.Style.STROKE;paint.color=alpha(p.accent,90);paint.strokeWidth=2f;canvas.drawCircle(x+12f,y-5f,12f+index%3*4,paint) }
            ThemeManager.Experience.GROVE -> { paint.color=alpha(p.accent,70);canvas.drawOval(RectF(x,y-20f,x+42f,y+10f),paint) }
            ThemeManager.Experience.INK -> { paint.color=alpha(p.ink,50+index*8);canvas.drawCircle(x+10f,y-8f,8f+index%3*4,paint) }
            else -> { paint.color=alpha(p.accent2,90);canvas.drawRect(x,y-20f,x+34f,y-16f,paint) }
        }
        wrapped(canvas, fragment, x+58f, y-24f, 730f, 23f, p.ink, 1.38f, 2)
    }

    private fun drawMoodRibbon(canvas: Canvas, issue: LifeJournalIssue, p: Palette, x: Float, y: Float, w: Float, h: Float) {
        val pts=issue.moodPoints
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        if(pts.isEmpty()){ paint.color=alpha(p.muted,80);paint.strokeWidth=2f;canvas.drawLine(x,y+h/2,x+w,y+h/2,paint);text(canvas,"没有足够的情绪文字",x,y+h/2-20f,20f,p.muted,false,1f);return }
        val path=Path(); val fill=Path()
        pts.forEachIndexed{idx,pt-> val px=x+if(pts.size==1)w/2 else w*idx/(pts.size-1); val py=y+h*.5f-pt.score*h*.34f; if(idx==0){path.moveTo(px,py);fill.moveTo(px,y+h)};path.lineTo(px,py);fill.lineTo(px,py); if(idx==pts.lastIndex)fill.lineTo(px,y+h)}
        fill.close();paint.shader=LinearGradient(0f,y,0f,y+h,alpha(p.accent,95),alpha(p.accent2,8),Shader.TileMode.CLAMP);canvas.drawPath(fill,paint);paint.shader=null;paint.style=Paint.Style.STROKE;paint.strokeWidth=5f;paint.color=p.accent;canvas.drawPath(path,paint);paint.style=Paint.Style.FILL
        pts.forEachIndexed{idx,pt-> val px=x+if(pts.size==1)w/2 else w*idx/(pts.size-1); val py=y+h*.5f-pt.score*h*.34f;canvas.drawCircle(px,py,7f,paint); if(idx==0||idx==pts.lastIndex)text(canvas,pt.date.takeLast(5).replace('-','.'),px-24f,y+h+35f,17f,p.muted,false,1f)}
    }

    private fun drawFolio(canvas: Canvas, page: Int, count: Int, spec: ThemeManager.ThemeSpec, p: Palette) {
        if(page==0)return
        val label="${page.toString().padStart(2,'0')} / ${(count-1).toString().padStart(2,'0')}"
        text(canvas,label,855f,1390f,17f,if(spec.dark)p.muted else p.muted,true,1.5f)
    }

    private fun text(canvas: Canvas, value: String, x: Float, baseline: Float, size: Float, color: Int, bold: Boolean, spacing: Float) {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;textSize=size;typeface=if(bold)android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD) else android.graphics.Typeface.create("sans",android.graphics.Typeface.NORMAL);letterSpacing=(spacing-1f)*.07f}
        canvas.drawText(value,x,baseline,paint)
    }

    private fun wrapped(canvas: Canvas, value: String, x: Float, y: Float, width: Float, size: Float, color: Int, lineHeight: Float, maxLines: Int, bold: Boolean=false): Float {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;textSize=size;typeface=if(bold)android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD) else android.graphics.Typeface.create("sans",android.graphics.Typeface.NORMAL)}
        val lines=mutableListOf<String>(); var current=""
        value.forEach{ch-> val next=current+ch; if(paint.measureText(next)>width&&current.isNotEmpty()){lines+=current;current=ch.toString()}else current=next }
        if(current.isNotEmpty())lines+=current
        var baseline=y
        lines.take(maxLines).forEachIndexed{index,line-> val rendered=if(index==maxLines-1&&lines.size>maxLines)line.dropLast(1)+"…" else line;canvas.drawText(rendered,x,baseline,paint);baseline+=size*lineHeight}
        return baseline
    }

    private fun c(hex:String):Int=Color.parseColor(hex)
    private fun alpha(color:Int,a:Int):Int=(color and 0x00FFFFFF) or (a.coerceIn(0,255) shl 24)
}
