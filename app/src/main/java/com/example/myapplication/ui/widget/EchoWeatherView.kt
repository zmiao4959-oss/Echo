package com.example.myapplication.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import com.example.myapplication.ui.ThemeColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 与 Echo 光球同源的主题化天气图形，替代 emoji 与系统默认天气图标。 */
class EchoWeatherView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class WeatherKind { CLEAR, PARTLY_CLOUDY, CLOUDY, RAIN, HEAVY_RAIN, THUNDER, SNOW, FOG }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val cloudPath = Path()
    private val boltPath = Path()
    private var kind = WeatherKind.CLOUDY
    private var phase = 0f
    private var animator: ValueAnimator? = null

    fun setWeatherDescription(description: String) {
        kind = when {
            "雷" in description -> WeatherKind.THUNDER
            "大雨" in description -> WeatherKind.HEAVY_RAIN
            "雨" in description -> WeatherKind.RAIN
            "雪" in description -> WeatherKind.SNOW
            "雾" in description || "霾" in description -> WeatherKind.FOG
            "多云" in description -> WeatherKind.PARTLY_CLOUDY
            "晴" in description -> WeatherKind.CLEAR
            else -> WeatherKind.CLOUDY
        }
        contentDescription = description
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val unit = min(width, height) / 32f
        if (unit <= 0f) return
        val primary = ThemeColors.primary(context)
        val accent = ThemeColors.accent(context)
        val secondary = ThemeColors.textSecondary(context)

        stroke.strokeWidth = 1.7f * unit
        stroke.color = primary
        fill.color = ColorUtils.blendARGB(primary, accent, 0.42f)

        when (kind) {
            WeatherKind.CLEAR -> drawSun(canvas, 16f * unit, 16f * unit, 6.2f * unit, primary, accent, unit)
            WeatherKind.PARTLY_CLOUDY -> {
                drawSun(canvas, 11f * unit, 11f * unit, 4.5f * unit, primary, accent, unit)
                drawCloud(canvas, 17f * unit, 18f * unit, unit, secondary, primary)
            }
            WeatherKind.CLOUDY -> drawCloud(canvas, 16f * unit, 16f * unit, unit, secondary, primary)
            WeatherKind.RAIN, WeatherKind.HEAVY_RAIN -> {
                drawCloud(canvas, 16f * unit, 12f * unit, unit, secondary, primary)
                drawRain(canvas, unit, accent, if (kind == WeatherKind.HEAVY_RAIN) 5 else 3)
            }
            WeatherKind.THUNDER -> {
                drawCloud(canvas, 16f * unit, 11f * unit, unit, secondary, primary)
                boltPath.reset()
                boltPath.moveTo(17f * unit, 18f * unit)
                boltPath.lineTo(13.8f * unit, 24f * unit)
                boltPath.lineTo(18.3f * unit, 23f * unit)
                boltPath.lineTo(15.7f * unit, 29f * unit)
                stroke.color = accent
                stroke.alpha = 155 + (sin(phase * PI * 2).toFloat() * 70).toInt()
                canvas.drawPath(boltPath, stroke)
                stroke.alpha = 255
            }
            WeatherKind.SNOW -> {
                drawCloud(canvas, 16f * unit, 11f * unit, unit, secondary, primary)
                fill.color = accent
                repeat(4) { index ->
                    val y = (20f + ((phase * 10f + index * 3f) % 9f)) * unit
                    val x = (9f + index * 4.7f) * unit
                    fill.alpha = 150 + index * 22
                    canvas.drawCircle(x, y, 1.15f * unit, fill)
                }
            }
            WeatherKind.FOG -> {
                stroke.color = ColorUtils.blendARGB(primary, secondary, 0.45f)
                repeat(3) { index ->
                    val offset = sin((phase + index * 0.18f) * PI * 2).toFloat() * 1.4f * unit
                    val y = (10f + index * 6f) * unit
                    canvas.drawLine((5f + index) * unit + offset, y, (27f - index) * unit + offset, y, stroke)
                }
            }
        }
    }

    private fun drawSun(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        primary: Int,
        accent: Int,
        unit: Float
    ) {
        fill.color = ColorUtils.blendARGB(primary, accent, 0.32f)
        fill.alpha = 235
        canvas.drawCircle(cx, cy, radius, fill)
        stroke.color = ColorUtils.setAlphaComponent(accent, 190)
        stroke.strokeWidth = 1.25f * unit
        repeat(8) { index ->
            val angle = index * PI / 4 + phase * PI * 0.12
            canvas.drawLine(
                cx + cos(angle).toFloat() * radius * 1.38f,
                cy + sin(angle).toFloat() * radius * 1.38f,
                cx + cos(angle).toFloat() * radius * 1.72f,
                cy + sin(angle).toFloat() * radius * 1.72f,
                stroke
            )
        }
    }

    private fun drawCloud(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        unit: Float,
        secondary: Int,
        primary: Int
    ) {
        val drift = sin(phase * PI * 2).toFloat() * 0.7f * unit
        cloudPath.reset()
        cloudPath.moveTo(cx - 10f * unit + drift, cy + 4f * unit)
        cloudPath.cubicTo(cx - 13f * unit + drift, cy - 1f * unit, cx - 8f * unit + drift, cy - 5f * unit, cx - 4f * unit + drift, cy - 3f * unit)
        cloudPath.cubicTo(cx - 1f * unit + drift, cy - 10f * unit, cx + 8f * unit + drift, cy - 8f * unit, cx + 8f * unit + drift, cy - 2f * unit)
        cloudPath.cubicTo(cx + 14f * unit + drift, cy - 1f * unit, cx + 13f * unit + drift, cy + 6f * unit, cx + 7f * unit + drift, cy + 6f * unit)
        cloudPath.lineTo(cx - 7f * unit + drift, cy + 6f * unit)
        cloudPath.cubicTo(cx - 9f * unit + drift, cy + 6f * unit, cx - 10f * unit + drift, cy + 5f * unit, cx - 10f * unit + drift, cy + 4f * unit)
        fill.color = ColorUtils.blendARGB(secondary, primary, 0.24f)
        fill.alpha = 205
        canvas.drawPath(cloudPath, fill)
        stroke.color = ColorUtils.setAlphaComponent(primary, 165)
        canvas.drawPath(cloudPath, stroke)
    }

    private fun drawRain(canvas: Canvas, unit: Float, accent: Int, count: Int) {
        stroke.color = accent
        stroke.strokeWidth = 1.45f * unit
        repeat(count) { index ->
            val x = (8f + index * (16f / (count - 1).coerceAtLeast(1))) * unit
            val offset = ((phase * 12f + index * 2.7f) % 7f) * unit
            val y = 19f * unit + offset
            stroke.alpha = 125 + index * (100 / count)
            canvas.drawLine(x, y, x - 1.5f * unit, y + 4f * unit, stroke)
        }
        stroke.alpha = 255
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animationsEnabled()) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 4200L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    phase = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun animationsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
}
