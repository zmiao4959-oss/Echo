package com.example.myapplication.ui.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import com.example.myapplication.ui.ThemeColors
import com.example.myapplication.MyApplication
import com.example.myapplication.ui.ThemeManager

/** 一段生活片段从记录按钮沿光轨进入 Echo 的一次性叙事动效。 */
class EchoCaptureMotionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val path = Path()
    private val visibleTrail = Path()
    private val pathMeasure = PathMeasure()
    private val position = FloatArray(2)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
        pathEffect = DashPathEffect(floatArrayOf(5f, 11f), 0f)
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * resources.displayMetrics.density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            12f,
            resources.displayMetrics
        )
    }
    private val labelPillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
    }

    private var progress = 0f
    private var endX = 0f
    private var endY = 0f
    private var arrived = false
    private var onArrive: (() -> Unit)? = null
    private var animator: ValueAnimator? = null

    init {
        visibility = INVISIBLE
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun play(
        startX: Float,
        startY: Float,
        targetX: Float,
        targetY: Float,
        onArrive: () -> Unit
    ) {
        animator?.cancel()
        if (!animationsEnabled()) {
            onArrive()
            return
        }

        this.endX = targetX
        this.endY = targetY
        this.onArrive = onArrive
        arrived = false
        progress = 0f

        val density = resources.displayMetrics.density
        val experience = ThemeManager.specFor(
            (context.applicationContext as MyApplication).appConfig.themeKey
        ).experience
        path.reset()
        path.moveTo(startX, startY)
        val controls = when (experience) {
            ThemeManager.Experience.PAPER -> floatArrayOf(-8f, -82f, -88f, 32f)
            ThemeManager.Experience.ARCHIVE -> floatArrayOf(-42f, -55f, -138f, 12f)
            ThemeManager.Experience.FILM -> floatArrayOf(0f, -48f, -62f, 8f)
            ThemeManager.Experience.CEDAR -> floatArrayOf(-10f, -118f, -72f, 54f)
            ThemeManager.Experience.TIDE -> floatArrayOf(34f, -96f, -146f, 86f)
            ThemeManager.Experience.ORBIT -> floatArrayOf(76f, -142f, -176f, 96f)
            ThemeManager.Experience.GROVE -> floatArrayOf(-38f, -136f, -108f, 62f)
            ThemeManager.Experience.INK -> floatArrayOf(2f, -72f, -118f, 20f)
        }
        path.cubicTo(
            startX + controls[0] * density,
            startY + controls[1] * density,
            targetX + controls[2] * density,
            targetY + controls[3] * density,
            targetX, targetY
        )
        pathMeasure.setPath(path, false)
        trailPaint.shader = LinearGradient(
            startX,
            startY,
            targetX,
            targetY,
            ColorUtils.setAlphaComponent(ThemeColors.accent(context), 35),
            ThemeColors.primary(context),
            Shader.TileMode.CLAMP
        )
        guidePaint.color = ColorUtils.setAlphaComponent(ThemeColors.primary(context), 45)
        particlePaint.color = ThemeColors.accent(context)
        ripplePaint.color = ThemeColors.primary(context)
        labelPaint.color = ThemeColors.textPrimary(context)
        labelPillPaint.color = ColorUtils.setAlphaComponent(ThemeColors.surfaceVariant(context), 232)
        labelPillStrokePaint.color = ColorUtils.setAlphaComponent(ThemeColors.primary(context), 110)

        visibility = VISIBLE
        bringToFront()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = when (experience) {
                ThemeManager.Experience.FILM -> 720L
                ThemeManager.Experience.ORBIT -> 1280L
                ThemeManager.Experience.PAPER, ThemeManager.Experience.CEDAR -> 980L
                else -> 1100L
            }
            interpolator = DecelerateInterpolator(1.25f)
            addUpdateListener {
                progress = it.animatedValue as Float
                if (!arrived && progress >= ARRIVAL_PROGRESS) {
                    arrived = true
                    this@EchoCaptureMotionView.onArrive?.invoke()
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    visibility = INVISIBLE
                    this@EchoCaptureMotionView.onArrive = null
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visibility != VISIBLE || pathMeasure.length <= 0f) return

        guidePaint.alpha = ((1f - progress) * 80).toInt().coerceIn(0, 80)
        canvas.drawPath(path, guidePaint)

        val travel = (progress / ARRIVAL_PROGRESS).coerceIn(0f, 1f)
        val distance = pathMeasure.length * travel
        visibleTrail.reset()
        pathMeasure.getSegment(
            (distance - pathMeasure.length * 0.24f).coerceAtLeast(0f),
            distance,
            visibleTrail,
            true
        )
        trailPaint.alpha = ((1f - travel * 0.30f) * 255).toInt()
        canvas.drawPath(visibleTrail, trailPaint)

        pathMeasure.getPosTan(distance, position, null)
        repeat(4) { index ->
            val behind = (distance - index * 10f * resources.displayMetrics.density)
                .coerceAtLeast(0f)
            pathMeasure.getPosTan(behind, position, null)
            particlePaint.alpha = 255 - index * 48
            canvas.drawCircle(
                position[0],
                position[1],
                (5.2f - index * 0.8f) * resources.displayMetrics.density,
                particlePaint
            )
        }

        if (progress >= ARRIVAL_PROGRESS) {
            val rippleProgress = ((progress - ARRIVAL_PROGRESS) / (1f - ARRIVAL_PROGRESS))
                .coerceIn(0f, 1f)
            repeat(3) { index ->
                val local = (rippleProgress - index * 0.12f).coerceIn(0f, 1f)
                ripplePaint.alpha = ((1f - local) * (150 - index * 25)).toInt()
                canvas.drawCircle(
                    endX,
                    endY,
                    (18f + local * 52f) * resources.displayMetrics.density,
                    ripplePaint
                )
            }
            val labelAlpha = when {
                rippleProgress < 0.20f -> rippleProgress / 0.20f
                rippleProgress > 0.82f -> (1f - rippleProgress) / 0.18f
                else -> 1f
            }.coerceIn(0f, 1f)
            val density = resources.displayMetrics.density
            val label = "这一刻，已收好"
            val labelY = endY + 63f * density
            val halfWidth = labelPaint.measureText(label) / 2f + 14f * density
            val pillTop = labelY - 17f * density
            val pillBottom = labelY + 8f * density
            labelPillPaint.alpha = (labelAlpha * 232).toInt()
            labelPillStrokePaint.alpha = (labelAlpha * 110).toInt()
            canvas.drawRoundRect(
                endX - halfWidth,
                pillTop,
                endX + halfWidth,
                pillBottom,
                13f * density,
                13f * density,
                labelPillPaint
            )
            canvas.drawRoundRect(
                endX - halfWidth,
                pillTop,
                endX + halfWidth,
                pillBottom,
                13f * density,
                13f * density,
                labelPillStrokePaint
            )
            labelPaint.alpha = (labelAlpha * 235).toInt()
            canvas.drawText(
                label,
                endX,
                labelY,
                labelPaint
            )
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun animationsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    private companion object {
        const val ARRIVAL_PROGRESS = 0.68f
    }
}
