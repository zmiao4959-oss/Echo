package com.example.myapplication.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.example.myapplication.ui.ThemeColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 记录卡的原创时段符号：晨、昼、暮、夜共用同一套 Echo 线条语言。 */
class EchoMomentGlyphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arc = RectF()
    private var hour = 12

    fun setHour(value: Int) {
        hour = value.coerceIn(0, 23)
        contentDescription = when (hour) {
            in 5..8 -> "清晨"
            in 9..16 -> "白天"
            in 17..19 -> "傍晚"
            else -> "夜晚"
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val unit = min(width, height) / 24f
        val cx = width / 2f
        val cy = height / 2f
        val primary = ThemeColors.primary(context)
        val accent = ThemeColors.accent(context)
        stroke.strokeWidth = 1.35f * unit
        stroke.color = ColorUtils.setAlphaComponent(primary, 215)
        fill.color = ColorUtils.blendARGB(primary, accent, 0.38f)

        when (hour) {
            in 5..8 -> {
                canvas.drawLine(3f * unit, cy + 5f * unit, 21f * unit, cy + 5f * unit, stroke)
                arc.set(cx - 5f * unit, cy, cx + 5f * unit, cy + 10f * unit)
                canvas.drawArc(arc, 180f, 180f, false, stroke)
                canvas.drawCircle(cx, cy + 5f * unit, 2.1f * unit, fill)
            }
            in 9..16 -> {
                canvas.drawCircle(cx, cy, 4.1f * unit, fill)
                repeat(8) { index ->
                    val angle = index * PI / 4
                    canvas.drawLine(
                        cx + cos(angle).toFloat() * 6.5f * unit,
                        cy + sin(angle).toFloat() * 6.5f * unit,
                        cx + cos(angle).toFloat() * 8.7f * unit,
                        cy + sin(angle).toFloat() * 8.7f * unit,
                        stroke
                    )
                }
            }
            in 17..19 -> {
                canvas.drawLine(3f * unit, cy + 3f * unit, 21f * unit, cy + 3f * unit, stroke)
                canvas.drawCircle(cx, cy + 1f * unit, 4.2f * unit, fill)
                canvas.drawLine(6f * unit, cy + 7f * unit, 18f * unit, cy + 7f * unit, stroke)
            }
            else -> {
                fill.color = primary
                canvas.drawCircle(cx, cy, 7f * unit, fill)
                fill.color = ThemeColors.surface(context)
                canvas.drawCircle(cx + 3.1f * unit, cy - 2.1f * unit, 6.5f * unit, fill)
                fill.color = accent
                fill.alpha = 175
                canvas.drawCircle(cx - 7f * unit, cy - 5.5f * unit, 1.1f * unit, fill)
                fill.alpha = 255
            }
        }
    }
}
