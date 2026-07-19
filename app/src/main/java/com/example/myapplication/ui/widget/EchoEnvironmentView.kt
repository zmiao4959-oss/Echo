package com.example.myapplication.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.example.myapplication.MyApplication
import com.example.myapplication.ui.BackgroundManager
import com.example.myapplication.ui.ThemeColors
import com.example.myapplication.ui.ThemeManager
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Full-screen authored environment for the selected Echo theme. */
class EchoEnvironmentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val finePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var phase = 0f
    private var weather = "晴"
    private var stage = 0
    private var animator: ValueAnimator? = null
    private var lastInvalidateNanos = 0L
    private var cachedArtworkRes: Int? = null
    private var cachedArtwork: Bitmap? = null

    private val config get() = (context.applicationContext as MyApplication).appConfig
    private val spec get() = ThemeManager.specFor(config.themeKey)

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
        updateAnimator()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun updateAnimator() {
        animator?.cancel()
        animator = null
        if (!ThemeManager.isDynamic(config.themeKey) || !animationsEnabled()) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = when (spec.motion) {
                ThemeManager.Motion.TIDE -> 22_000L
                ThemeManager.Motion.ORBIT -> 32_000L
                ThemeManager.Motion.GROVE -> 16_000L
                ThemeManager.Motion.INK_RAIN -> 12_000L
                else -> 20_000L
            }
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val now = System.nanoTime()
                if (now - lastInvalidateNanos >= 32_000_000L) {
                    phase = it.animatedValue as Float
                    lastInvalidateNanos = now
                    postInvalidateOnAnimation()
                }
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val themeOwnsBackground = config.backgroundKey == BackgroundManager.THEME_BACKGROUND
        if (!themeOwnsBackground) {
            drawManualBackgroundOverlay(canvas)
            return
        }

        when (spec.motion) {
            ThemeManager.Motion.NONE -> drawStaticArtwork(canvas)
            ThemeManager.Motion.TIDE -> drawTidalLight(canvas)
            ThemeManager.Motion.ORBIT -> drawOrbitalNight(canvas)
            ThemeManager.Motion.GROVE -> drawBreathingGrove(canvas)
            ThemeManager.Motion.INK_RAIN -> drawInkRain(canvas)
        }
    }

    private fun drawStaticArtwork(canvas: Canvas) {
        val resource = spec.artworkRes ?: return drawThemeColor(canvas)
        if (cachedArtworkRes != resource || cachedArtwork == null) {
            cachedArtwork = BitmapFactory.decodeResource(resources, resource)
            cachedArtworkRes = resource
        }
        val bitmap = cachedArtwork ?: return drawThemeColor(canvas)
        val scale = maxOf(width / bitmap.width.toFloat(), height / bitmap.height.toFloat())
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val destination = RectF(
            (width - drawWidth) / 2f,
            (height - drawHeight) / 2f,
            (width + drawWidth) / 2f,
            (height + drawHeight) / 2f,
        )
        paint.shader = null
        paint.alpha = 255
        canvas.drawBitmap(bitmap, null, destination, paint)
        paint.color = ColorUtils.setAlphaComponent(
            ThemeColors.background(context),
            if (spec.dark) 26 else 12,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawThemeColor(canvas: Canvas) {
        canvas.drawColor(ThemeColors.background(context))
    }

    private fun drawManualBackgroundOverlay(canvas: Canvas) {
        val primary = ThemeColors.primary(context)
        val transparentPrimary = ColorUtils.setAlphaComponent(primary, 28)
        paint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            transparentPrimary,
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
    }

    private fun drawTidalLight(canvas: Canvas) {
        val background = ThemeColors.background(context)
        val primary = ThemeColors.primary(context)
        val accent = ThemeColors.accent(context)
        val shift = sin(phase * TWO_PI) * height * 0.08f
        paint.shader = LinearGradient(
            width * 0.12f,
            shift,
            width * 0.88f,
            height + shift,
            intArrayOf(
                ColorUtils.blendARGB(background, Color.WHITE, 0.12f),
                ColorUtils.blendARGB(background, primary, 0.22f),
                ColorUtils.blendARGB(background, accent, 0.16f),
            ),
            floatArrayOf(0f, 0.54f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        repeat(4) { index ->
            val baseline = height * (0.20f + index * 0.22f)
            val amplitude = height * (0.018f + index * 0.004f)
            val travel = phase * TWO_PI + index * 0.9f
            path.reset()
            path.moveTo(-width * 0.1f, baseline)
            var x = -width * 0.1f
            while (x <= width * 1.1f) {
                val y = baseline + sin(x / width * TWO_PI * 1.4f + travel) * amplitude
                path.lineTo(x, y)
                x += width / 28f
            }
            finePaint.style = Paint.Style.STROKE
            finePaint.strokeWidth = resources.displayMetrics.density * (0.7f + index * 0.25f)
            finePaint.color = ColorUtils.setAlphaComponent(if (index % 2 == 0) primary else accent, 24 + index * 8)
            canvas.drawPath(path, finePaint)
        }

        val glowX = width * (0.2f + phase * 0.6f)
        val glowY = height * (0.18f + sin(phase * TWO_PI) * 0.04f)
        paint.shader = RadialGradient(
            glowX,
            glowY,
            width * 0.42f,
            ColorUtils.setAlphaComponent(Color.WHITE, 48),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(glowX, glowY, width * 0.42f, paint)
        paint.shader = null
    }

    private fun drawOrbitalNight(canvas: Canvas) {
        val background = ThemeColors.background(context)
        val primary = ThemeColors.primary(context)
        val accent = ThemeColors.accent(context)
        paint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            ColorUtils.blendARGB(background, Color.rgb(25, 34, 63), 0.42f),
            background,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        val centerX = width * 0.72f
        val centerY = height * 0.17f
        repeat(3) { index ->
            val radiusX = width * (0.18f + index * 0.075f)
            val radiusY = radiusX * (0.32f + index * 0.025f)
            finePaint.style = Paint.Style.STROKE
            finePaint.strokeWidth = resources.displayMetrics.density * 0.75f
            finePaint.color = ColorUtils.setAlphaComponent(primary, 28 - index * 5)
            canvas.drawOval(RectF(centerX - radiusX, centerY - radiusY, centerX + radiusX, centerY + radiusY), finePaint)

            val angle = phase * TWO_PI * (0.55f + index * 0.15f) + index * 2.1f
            val dotX = centerX + cos(angle) * radiusX
            val dotY = centerY + sin(angle) * radiusY
            finePaint.style = Paint.Style.FILL
            finePaint.color = ColorUtils.setAlphaComponent(if (index == 1) accent else primary, 170)
            canvas.drawCircle(dotX, dotY, resources.displayMetrics.density * (1.4f + index * 0.4f), finePaint)
        }

        repeat(46) { index ->
            val seedX = ((index * 137) % 997) / 997f
            val seedY = ((index * 223) % 887) / 887f
            val drift = sin(phase * TWO_PI + index * 0.63f) * width * 0.004f
            val pulse = 0.45f + 0.55f * sin(phase * TWO_PI * 1.7f + index)
            finePaint.color = ColorUtils.setAlphaComponent(
                if (index % 9 == 0) accent else Color.WHITE,
                (20 + pulse * 52).toInt().coerceIn(12, 74),
            )
            canvas.drawCircle(
                seedX * width + drift,
                seedY * height * 0.78f,
                resources.displayMetrics.density * (0.35f + index % 3 * 0.22f),
                finePaint,
            )
        }
        paint.shader = RadialGradient(
            centerX,
            centerY,
            width * 0.34f,
            ColorUtils.setAlphaComponent(primary, 35),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(centerX, centerY, width * 0.34f, paint)
        paint.shader = null
    }

    private fun drawBreathingGrove(canvas: Canvas) {
        val background = ThemeColors.background(context)
        val primary = ThemeColors.primary(context)
        val accent = ThemeColors.accent(context)
        paint.shader = LinearGradient(
            0f,
            0f,
            width * 0.7f,
            height.toFloat(),
            ColorUtils.blendARGB(background, Color.WHITE, 0.08f),
            ColorUtils.blendARGB(background, primary, 0.14f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        repeat(7) { index ->
            val breath = 0.90f + 0.10f * sin(phase * TWO_PI + index * 0.78f)
            val x = width * (((index * 37) % 100) / 100f)
            val y = height * (0.08f + ((index * 29) % 83) / 100f)
            val radius = width * (0.18f + index % 3 * 0.05f) * breath
            paint.shader = RadialGradient(
                x,
                y,
                radius,
                ColorUtils.setAlphaComponent(if (index % 3 == 0) accent else primary, 22 + index % 3 * 6),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(x, y, radius, paint)
        }
        paint.shader = null

        repeat(9) { index ->
            val sway = sin(phase * TWO_PI + index * 0.52f) * width * 0.018f
            val x = width * (0.06f + index * 0.12f) + sway
            val y = height * (0.12f + (index % 4) * 0.23f)
            val leafW = width * (0.026f + index % 2 * 0.009f)
            val leafH = leafW * 2.1f
            path.reset()
            path.moveTo(x, y - leafH)
            path.cubicTo(x + leafW, y - leafH * 0.45f, x + leafW, y + leafH * 0.45f, x, y + leafH)
            path.cubicTo(x - leafW, y + leafH * 0.45f, x - leafW, y - leafH * 0.45f, x, y - leafH)
            finePaint.style = Paint.Style.FILL
            finePaint.color = ColorUtils.setAlphaComponent(primary, 12 + index % 3 * 6)
            canvas.drawPath(path, finePaint)
        }
    }

    private fun drawInkRain(canvas: Canvas) {
        val background = ThemeColors.background(context)
        val primary = ThemeColors.primary(context)
        val accent = ThemeColors.accent(context)
        canvas.drawColor(background)

        repeat(4) { index ->
            val x = width * (0.16f + index * 0.25f)
            val y = height * (0.18f + (index % 3) * 0.27f)
            val pulse = 0.92f + sin(phase * TWO_PI + index) * 0.07f
            val radius = width * (0.18f + index * 0.035f) * pulse
            paint.shader = RadialGradient(
                x,
                y,
                radius,
                ColorUtils.setAlphaComponent(if (index == 3) accent else primary, 18 + index * 3),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(x, y, radius, paint)
        }
        paint.shader = null

        repeat(20) { index ->
            val x = ((index * 83f + phase * width * 0.22f) % (width + 70f)) - 35f
            val y = ((index * 149f + phase * height * 1.15f) % (height + 160f)) - 80f
            finePaint.style = Paint.Style.STROKE
            finePaint.strokeCap = Paint.Cap.ROUND
            finePaint.strokeWidth = resources.displayMetrics.density * (0.65f + index % 3 * 0.16f)
            finePaint.color = ColorUtils.setAlphaComponent(primary, 18 + index % 4 * 5)
            canvas.drawLine(x, y, x - resources.displayMetrics.density * 5f, y + resources.displayMetrics.density * 20f, finePaint)
        }

        repeat(5) { index ->
            val local = (phase * 1.7f + index * 0.23f) % 1f
            val x = width * (((index * 43) % 91) / 100f + 0.05f)
            val y = height * (0.18f + (index % 4) * 0.20f)
            val radius = width * 0.12f * local
            finePaint.style = Paint.Style.STROKE
            finePaint.strokeWidth = resources.displayMetrics.density * 0.7f
            finePaint.color = ColorUtils.setAlphaComponent(primary, ((1f - local) * 45).toInt())
            canvas.drawOval(RectF(x - radius, y - radius * 0.28f, x + radius, y + radius * 0.28f), finePaint)
        }
    }

    private fun animationsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    companion object {
        private const val TWO_PI = (PI * 2).toFloat()
    }
}
