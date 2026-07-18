package com.example.myapplication.ui.widget

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.example.myapplication.ui.ThemeColors

/** 四个时间空间之间的一次性光轨，显示“片段正在去往哪里”。 */
class EchoJourneyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val path = Path()
    private val measure = PathMeasure()
    private val position = FloatArray(2)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = resources.displayMetrics.density
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var progress = 0f
    private var fromStage = 0
    private var toStage = 0
    private var animator: ValueAnimator? = null

    init {
        visibility = INVISIBLE
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun travel(from: Int, to: Int) {
        if (from == to || width == 0 || height == 0) return
        fromStage = from.coerceIn(0, 3)
        toStage = to.coerceIn(0, 3)
        if (!animationsEnabled()) return
        buildPath()
        visibility = VISIBLE
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 720L
            addUpdateListener {
                progress = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    visibility = INVISIBLE
                }
            })
            start()
        }
    }

    private fun buildPath() {
        val y = height - 112f * resources.displayMetrics.density
        val startX = width * ((fromStage + 0.5f) / 4f)
        val endX = width * ((toStage + 0.5f) / 4f)
        path.reset()
        path.moveTo(startX, y)
        path.cubicTo(startX, y - 54f * resources.displayMetrics.density, endX, y - 54f * resources.displayMetrics.density, endX, y)
        measure.setPath(path, false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = buildPath()

    override fun onDraw(canvas: Canvas) {
        if (visibility != VISIBLE || measure.length <= 0f) return
        val primary = ThemeColors.primary(context)
        val accent = ThemeColors.accent(context)
        linePaint.color = ColorUtils.setAlphaComponent(primary, ((1f - progress) * 90).toInt())
        canvas.drawPath(path, linePaint)
        measure.getPosTan(measure.length * progress, position, null)
        repeat(4) { index ->
            val behind = (measure.length * progress - index * 10f * resources.displayMetrics.density).coerceAtLeast(0f)
            measure.getPosTan(behind, position, null)
            particlePaint.color = ColorUtils.setAlphaComponent(accent, 240 - index * 48)
            canvas.drawCircle(position[0], position[1], (4.8f - index * 0.75f) * resources.displayMetrics.density, particlePaint)
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
