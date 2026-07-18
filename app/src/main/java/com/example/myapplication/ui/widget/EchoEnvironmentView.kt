package com.example.myapplication.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.example.myapplication.ui.ThemeColors
import java.util.Calendar
import kotlin.math.sin

/** 全局时间与天气环境层：始终安静地存在于卡片之后。 */
class EchoEnvironmentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var weather = "晴"
    private var stage = 0
    private var animator: ValueAnimator? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setWeather(description: String) {
        weather = description
        invalidate()
    }

    fun setStage(value: Int) {
        stage = value.coerceIn(0, 3)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animationsEnabled() && animator == null) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 12_000L
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    phase = it.animatedValue as Float
                    postInvalidateOnAnimation()
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

    override fun onDraw(canvas: Canvas) {
        val background = ThemeColors.background(context)
        val primary = ThemeColors.primary(context)
        val accent = ThemeColors.accent(context)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val night = hour < 6 || hour >= 19
        val dawn = hour in 6..9
        val dusk = hour in 16..18
        val timeTint = when {
            dawn -> Color.rgb(111, 83, 132)
            dusk -> Color.rgb(127, 68, 107)
            night -> Color.rgb(27, 21, 58)
            else -> Color.rgb(30, 35, 54)
        }
        val stageTint = if (stage == 3) accent else primary
        val top = ColorUtils.blendARGB(background, timeTint, if (night) 0.42f else 0.24f)
        val bottom = ColorUtils.blendARGB(background, stageTint, 0.08f + stage * 0.012f)
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        val density = resources.displayMetrics.density
        if (night || "晴" in weather) {
            val glowX = width * (0.78f + sin(phase * Math.PI * 2).toFloat() * 0.015f)
            val glowY = height * 0.16f
            paint.shader = RadialGradient(
                glowX,
                glowY,
                width * 0.34f,
                intArrayOf(ColorUtils.setAlphaComponent(primary, if (night) 42 else 28), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(glowX, glowY, width * 0.34f, paint)
        }
        paint.shader = null

        when {
            "雨" in weather || "雷" in weather -> drawRain(canvas, density, accent)
            "雪" in weather -> drawSnow(canvas, density, primary)
            "雾" in weather || "霾" in weather -> drawFog(canvas, density, primary)
            night -> drawStars(canvas, density, accent)
        }
    }

    private fun drawRain(canvas: Canvas, density: Float, color: Int) {
        particlePaint.strokeWidth = 1.1f * density
        particlePaint.strokeCap = Paint.Cap.ROUND
        repeat(22) { index ->
            val x = ((index * 79f + phase * 310f * density) % (width + 80f)) - 40f
            val y = ((index * 137f + phase * 760f * density) % (height + 160f)) - 80f
            particlePaint.color = ColorUtils.setAlphaComponent(color, 18 + index % 4 * 7)
            canvas.drawLine(x, y, x - 7f * density, y + 25f * density, particlePaint)
        }
    }

    private fun drawSnow(canvas: Canvas, density: Float, color: Int) {
        repeat(18) { index ->
            val x = ((index * 91f + sin(phase * 6.28f + index) * 20f * density) % width).coerceAtLeast(0f)
            val y = ((index * 149f + phase * height) % height)
            particlePaint.color = ColorUtils.setAlphaComponent(color, 25 + index % 3 * 10)
            canvas.drawCircle(x, y, (1.1f + index % 3 * 0.45f) * density, particlePaint)
        }
    }

    private fun drawFog(canvas: Canvas, density: Float, color: Int) {
        particlePaint.strokeWidth = 18f * density
        particlePaint.strokeCap = Paint.Cap.ROUND
        repeat(4) { index ->
            val y = height * (0.22f + index * 0.18f)
            val offset = sin(phase * 6.28f + index) * 28f * density
            particlePaint.color = ColorUtils.setAlphaComponent(color, 12 + index * 3)
            canvas.drawLine(width * 0.08f + offset, y, width * 0.92f + offset, y, particlePaint)
        }
    }

    private fun drawStars(canvas: Canvas, density: Float, color: Int) {
        repeat(26) { index ->
            val x = ((index * 113) % 997) / 997f * width
            val y = ((index * 197) % 613) / 613f * height * 0.72f
            val pulse = (0.5f + 0.5f * sin(phase * 6.28f + index * 0.7f))
            particlePaint.color = ColorUtils.setAlphaComponent(color, (10 + pulse * 24).toInt())
            canvas.drawCircle(x, y, (0.55f + index % 3 * 0.25f) * density, particlePaint)
        }
    }

    private fun animationsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
}
