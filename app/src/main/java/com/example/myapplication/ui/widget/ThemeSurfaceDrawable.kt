package com.example.myapplication.ui.widget

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import androidx.core.graphics.ColorUtils
import com.example.myapplication.ui.ThemeManager
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A restrained living material placed below card content. It deliberately runs
 * at 15 fps: enough for texture motion, without competing with scrolling/text.
 */
class ThemeSurfaceDrawable(
    private val motion: ThemeManager.Motion,
    private val primary: Int,
    private val accent: Int,
    private val density: Float,
) : Drawable(), Runnable {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val path = Path()
    private val oval = RectF()
    private val startedAt = SystemClock.uptimeMillis()
    private var scheduled = false
    private var drawableAlpha = 255

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val duration = when (motion) {
            ThemeManager.Motion.TIDE -> 9_000L
            ThemeManager.Motion.ORBIT -> 14_000L
            ThemeManager.Motion.GROVE -> 10_500L
            ThemeManager.Motion.INK_RAIN -> 6_500L
            else -> 12_000L
        }
        val phase = ((SystemClock.uptimeMillis() - startedAt) % duration) / duration.toFloat()
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        when (motion) {
            ThemeManager.Motion.TIDE -> drawTide(canvas, phase)
            ThemeManager.Motion.ORBIT -> drawOrbit(canvas, phase)
            ThemeManager.Motion.GROVE -> drawGrove(canvas, phase)
            ThemeManager.Motion.INK_RAIN -> drawInkRain(canvas, phase)
            ThemeManager.Motion.NONE -> Unit
        }
        canvas.restore()
        scheduleNextFrame()
    }

    private fun drawTide(canvas: Canvas, phase: Float) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            phase * w - w * 0.45f,
            0f,
            phase * w + w * 0.55f,
            h,
            intArrayOf(Color.TRANSPARENT, color(primary, 28), color(accent, 22), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        repeat(6) { index ->
            val baseline = h * (0.12f + index * 0.17f)
            val amplitude = h * (0.035f + index % 2 * 0.018f)
            val travel = phase * TWO_PI * (if (index % 2 == 0) 1f else -0.72f) + index * 0.8f
            path.reset()
            path.moveTo(-w * 0.08f, baseline)
            var x = -w * 0.08f
            while (x <= w * 1.08f) {
                path.lineTo(x, baseline + sin(x / w * TWO_PI * 1.7f + travel) * amplitude)
                x += w / 24f
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = density * (0.8f + index % 3 * 0.35f)
            paint.color = color(if (index % 2 == 0) primary else accent, 34 + index * 5)
            canvas.drawPath(path, paint)
        }

        repeat(4) { index ->
            val x = ((phase * 1.35f + index * 0.27f) % 1f) * w
            val y = h * (0.24f + index * 0.18f)
            paint.style = Paint.Style.FILL
            paint.color = color(Color.WHITE, 34)
            canvas.drawOval(x - density * 9f, y - density * 1.1f, x + density * 9f, y + density * 1.1f, paint)
        }
    }

    private fun drawOrbit(canvas: Canvas, phase: Float) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val cx = w * 0.78f
        val cy = h * 0.36f
        repeat(4) { index ->
            val rx = w * (0.18f + index * 0.12f)
            val ry = h * (0.12f + index * 0.07f)
            oval.set(cx - rx, cy - ry, cx + rx, cy + ry)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = density * (0.65f + index * 0.18f)
            paint.color = color(if (index == 2) accent else primary, 42 - index * 4)
            canvas.drawOval(oval, paint)

            val angle = phase * TWO_PI * (0.72f + index * 0.17f) + index * 1.63f
            val x = cx + cos(angle) * rx
            val y = cy + sin(angle) * ry
            paint.style = Paint.Style.FILL
            paint.color = color(if (index == 2) accent else primary, 135)
            canvas.drawCircle(x, y, density * (1.4f + index * 0.35f), paint)
        }
        repeat(18) { index ->
            val drift = phase * w * (0.05f + index % 3 * 0.025f)
            val x = (((index * 79) % 181) / 181f * w + drift) % w
            val y = ((index * 47) % 97) / 97f * h
            val pulse = 0.5f + 0.5f * sin(phase * TWO_PI * 2f + index * 0.8f)
            paint.color = color(if (index % 6 == 0) accent else Color.WHITE, (30 + pulse * 58).toInt())
            canvas.drawCircle(x, y, density * (0.45f + index % 3 * 0.25f), paint)
        }
    }

    private fun drawGrove(canvas: Canvas, phase: Float) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        repeat(7) { index ->
            val sweep = sin(phase * TWO_PI + index * 0.74f)
            val x = w * (0.05f + index * 0.155f) + sweep * w * 0.055f
            val y = h * (0.16f + (index % 3) * 0.31f)
            val leafW = w * (0.025f + index % 2 * 0.012f)
            val leafH = h * (0.13f + index % 3 * 0.025f)
            path.reset()
            path.moveTo(x, y - leafH)
            path.cubicTo(x + leafW, y - leafH * 0.35f, x + leafW, y + leafH * 0.35f, x, y + leafH)
            path.cubicTo(x - leafW, y + leafH * 0.35f, x - leafW, y - leafH * 0.35f, x, y - leafH)
            paint.style = Paint.Style.FILL
            paint.color = color(if (index % 3 == 0) accent else primary, 30 + index % 3 * 12)
            canvas.drawPath(path, paint)
        }
        repeat(3) { index ->
            val x = w * ((phase * (0.18f + index * 0.07f) + index * 0.36f) % 1f)
            val y = h * (0.2f + index * 0.28f)
            val radius = w * (0.17f + index * 0.04f)
            paint.shader = RadialGradient(x, y, radius, color(accent, 26), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, radius, paint)
            paint.shader = null
        }
    }

    private fun drawInkRain(canvas: Canvas, phase: Float) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        repeat(18) { index ->
            val x = ((index * 71f + phase * w * 0.34f) % (w + density * 30f)) - density * 15f
            val y = ((index * 113f + phase * h * 2.1f) % (h + density * 46f)) - density * 23f
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = density * (0.65f + index % 3 * 0.18f)
            paint.color = color(primary, 32 + index % 4 * 8)
            canvas.drawLine(x, y, x - density * 3.8f, y + density * 15f, paint)
        }
        repeat(4) { index ->
            val local = (phase * 2f + index * 0.27f) % 1f
            val x = w * (0.12f + ((index * 31) % 79) / 100f)
            val y = h * (0.22f + (index % 3) * 0.3f)
            val radius = w * 0.14f * local
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = density * 0.75f
            paint.color = color(if (index == 3) accent else primary, ((1f - local) * 72).toInt())
            canvas.drawOval(x - radius, y - radius * 0.24f, x + radius, y + radius * 0.24f, paint)
        }
    }

    private fun color(base: Int, alpha: Int): Int =
        ColorUtils.setAlphaComponent(base, (alpha.coerceIn(0, 255) * drawableAlpha / 255))

    private fun scheduleNextFrame() {
        if (!isVisible || callback == null || scheduled || motion == ThemeManager.Motion.NONE) return
        scheduled = true
        scheduleSelf(this, SystemClock.uptimeMillis() + FRAME_DELAY_MS)
    }

    override fun run() {
        scheduled = false
        invalidateSelf()
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        if (!visible) {
            unscheduleSelf(this)
            scheduled = false
        }
        val changed = super.setVisible(visible, restart)
        if (visible) scheduleNextFrame()
        return changed
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        private const val FRAME_DELAY_MS = 66L
        private const val TWO_PI = (PI * 2).toFloat()
    }
}
