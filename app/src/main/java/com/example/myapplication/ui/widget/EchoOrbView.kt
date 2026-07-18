package com.example.myapplication.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
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

/**
 * Echo 的动态视觉锚点。
 *
 * 只使用原生 Canvas 与主题语义色，不依赖固定位图，因此可随五套主题一起变化；
 * 系统关闭动画时会自动停在静态构图上。
 */
class EchoOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orbitBounds = RectF()

    private var haloShader: Shader? = null
    private var orbShader: Shader? = null
    private var radius = 0f
    private var phase = 0f
    private var celebration = 0f
    private var ambientAnimator: ValueAnimator? = null
    private var celebrationAnimator: ValueAnimator? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cx = w / 2f
        val cy = h / 2f
        radius = min(w, h) * 0.22f
        val primary = ThemeColors.primary(context)
        val accent = ThemeColors.accent(context)
        haloShader = RadialGradient(
            cx,
            cy,
            radius * 2.7f,
            intArrayOf(
                ColorUtils.setAlphaComponent(accent, 105),
                ColorUtils.setAlphaComponent(primary, 52),
                ColorUtils.setAlphaComponent(primary, 0)
            ),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP
        )
        orbShader = RadialGradient(
            cx - radius * 0.35f,
            cy - radius * 0.42f,
            radius * 1.55f,
            intArrayOf(
                ColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 230),
                ColorUtils.setAlphaComponent(primary, 245),
                ColorUtils.setAlphaComponent(accent, 190),
                ColorUtils.setAlphaComponent(ThemeColors.primaryDark(context), 245)
            ),
            floatArrayOf(0f, 0.24f, 0.68f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (radius <= 0f) return

        val cx = width / 2f
        val cy = height / 2f + sin(phase * TWO_PI).toFloat() * radius * 0.06f
        val pulse = 1f + sin(phase * TWO_PI).toFloat() * 0.035f + celebration * 0.16f

        fillPaint.shader = haloShader
        fillPaint.alpha = (175 + celebration * 70).toInt().coerceAtMost(255)
        canvas.drawCircle(cx, cy, radius * (2.45f + celebration * 0.35f), fillPaint)

        orbitPaint.color = ColorUtils.setAlphaComponent(ThemeColors.primary(context), 72)
        repeat(3) { index ->
            val orbitScale = 1.35f + index * 0.42f + celebration * 0.18f
            orbitBounds.set(
                cx - radius * orbitScale,
                cy - radius * orbitScale * 0.42f,
                cx + radius * orbitScale,
                cy + radius * orbitScale * 0.42f
            )
            orbitPaint.alpha = 92 - index * 20
            canvas.drawOval(orbitBounds, orbitPaint)
        }

        canvas.save()
        canvas.scale(pulse, pulse, cx, cy)
        fillPaint.shader = orbShader
        fillPaint.alpha = 255
        canvas.drawCircle(cx, cy, radius, fillPaint)
        fillPaint.shader = null
        fillPaint.color = ColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 105)
        canvas.drawCircle(cx - radius * 0.27f, cy - radius * 0.31f, radius * 0.17f, fillPaint)
        canvas.restore()

        particlePaint.color = ThemeColors.accent(context)
        repeat(6) { index ->
            val angle = phase * TWO_PI + index * TWO_PI / 6f
            val distance = radius * (1.42f + (index % 3) * 0.24f) + celebration * radius * 0.9f
            val px = cx + cos(angle).toFloat() * distance
            val py = cy + sin(angle).toFloat() * distance * 0.46f
            particlePaint.alpha = 105 + (index % 3) * 44
            canvas.drawCircle(px, py, radius * (0.035f + (index % 2) * 0.014f), particlePaint)
        }
    }

    /** 用户保存片段时给出一次克制的扩散反馈。 */
    fun celebrate() {
        if (!animationsEnabled()) return
        celebrationAnimator?.cancel()
        celebrationAnimator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = 820L
            addUpdateListener {
                celebration = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAmbientMotion()
    }

    override fun onDetachedFromWindow() {
        ambientAnimator?.cancel()
        celebrationAnimator?.cancel()
        ambientAnimator = null
        celebrationAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isAttachedToWindow) startAmbientMotion()
        else ambientAnimator?.cancel()
    }

    private fun startAmbientMotion() {
        if (!animationsEnabled() || ambientAnimator?.isRunning == true || visibility != VISIBLE) {
            invalidate()
            return
        }
        ambientAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 5600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun animationsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    private companion object {
        const val TWO_PI = (PI * 2.0).toFloat()
    }
}
