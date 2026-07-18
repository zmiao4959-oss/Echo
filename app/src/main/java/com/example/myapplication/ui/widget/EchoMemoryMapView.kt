package com.example.myapplication.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.ColorUtils
import com.example.myapplication.data.model.EchoForeshadow
import com.example.myapplication.data.model.ForeshadowState
import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.ui.ThemeColors
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Zoomable memory constellation. Stars are cards; curved trajectories are unfolding stories. */
class EchoMemoryMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class CardNode(
        val card: MemoryCard,
        val nx: Float,
        val ny: Float,
        val radius: Float
    )

    private data class ThreadNode(
        val thread: EchoForeshadow,
        val points: List<PointF>,
        val sourcePointCount: Int
    )

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val threadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            11f,
            resources.displayMetrics
        )
    }
    private val threadPath = Path()
    private val cardNodes = mutableListOf<CardNode>()
    private val threadNodes = mutableListOf<ThreadNode>()
    private var phase = 0f
    private var scaleFactor = 1f
    private var panX = 0f
    private var panY = 0f
    private var selectedCardId: String? = null
    private var selectedThreadId: String? = null
    private var animator: ValueAnimator? = null

    var onCardSelected: ((MemoryCard) -> Unit)? = null
    var onForeshadowSelected: ((EchoForeshadow) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(0.82f, 2.45f)
                invalidate()
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                parent?.requestDisallowInterceptTouchEvent(true)
                panX = (panX - distanceX).coerceIn(-width * 0.45f, width * 0.45f)
                panY = (panY - distanceY).coerceIn(-height * 0.35f, height * 0.35f)
                invalidate()
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val worldX = (e.x - panX) / scaleFactor
                val worldY = (e.y - panY) / scaleFactor
                val nearestCard = cardNodes.minByOrNull { node ->
                    hypot(worldX - node.nx * width, worldY - node.ny * height)
                }
                val cardDistance = nearestCard?.let {
                    hypot(worldX - it.nx * width, worldY - it.ny * height)
                } ?: Float.MAX_VALUE
                val nearestThread = threadNodes.minByOrNull { node ->
                    val point = node.points.last()
                    hypot(worldX - point.x * width, worldY - point.y * height)
                }
                val threadDistance = nearestThread?.let {
                    val point = it.points.last()
                    hypot(worldX - point.x * width, worldY - point.y * height)
                } ?: Float.MAX_VALUE
                val density = resources.displayMetrics.density

                if (nearestThread != null &&
                    threadDistance < cardDistance &&
                    threadDistance <= 28f * density
                ) {
                    selectedThreadId = nearestThread.thread.id
                    selectedCardId = null
                    EchoFeedback.play(this@EchoMemoryMapView, EchoFeedback.Kind.CONNECT)
                    invalidate()
                    onForeshadowSelected?.invoke(nearestThread.thread)
                    return true
                }
                if (nearestCard != null &&
                    cardDistance <= nearestCard.radius * density * 3.2f
                ) {
                    selectedCardId = nearestCard.card.id
                    selectedThreadId = null
                    EchoFeedback.play(this@EchoMemoryMapView, EchoFeedback.Kind.CONNECT)
                    invalidate()
                    onCardSelected?.invoke(nearestCard.card)
                    return true
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                animate().cancel()
                scaleFactor = 1f
                panX = 0f
                panY = 0f
                invalidate()
                return true
            }
        }
    )

    init {
        isClickable = true
        contentDescription = "记忆星图，星点是回忆，弧线是未完的故事。双指缩放，拖动探索"
    }

    fun setCards(cards: List<MemoryCard>) {
        setContent(cards, emptyList())
    }

    fun setContent(cards: List<MemoryCard>, foreshadows: List<EchoForeshadow>) {
        cardNodes.clear()
        cards.take(42).forEachIndexed { index, card ->
            val seed = card.id.hashCode().toLong() and 0xffffffffL
            val ring = 0.16f + (index % 5) * 0.075f
            val angle = ((seed % 6283L) / 1000f) + index * 0.93f
            val x = (0.5f + cos(angle) * ring).coerceIn(0.08f, 0.92f)
            val y = (0.51f + sin(angle) * ring * 0.72f).coerceIn(0.12f, 0.88f)
            val radius = 3.8f + card.tags.size.coerceAtMost(4) * 0.75f +
                if (card.pinned) 2f else 0f
            cardNodes += CardNode(card, x, y, radius)
        }

        threadNodes.clear()
        foreshadows
            .filter { it.state != ForeshadowState.DISMISSED && it.sourceRefs.isNotEmpty() }
            .take(24)
            .forEachIndexed { threadIndex, thread ->
                val points = thread.sourceRefs.take(7).mapIndexed { sourceIndex, source ->
                    pointFor(source.id, sourceIndex, threadIndex)
                }.toMutableList()
                if (points.size == 1) {
                    points += pointFor("${thread.id}:open", 1, threadIndex + 11)
                }
                threadNodes += ThreadNode(
                    thread = thread,
                    points = points,
                    sourcePointCount = thread.sourceRefs.size.coerceAtMost(points.size)
                )
            }
        selectedCardId = null
        selectedThreadId = null
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scaled = scaleDetector.onTouchEvent(event)
        val gestured = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return scaled || gestured || super.onTouchEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animationsEnabled() && animator == null) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 4800L
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
        val primary = ThemeColors.primary(context)
        val accent = ThemeColors.accent(context)
        val density = resources.displayMetrics.density
        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(scaleFactor, scaleFactor)

        drawCardConnections(canvas, primary, accent, density)
        drawForeshadowThreads(canvas, primary, accent, density)
        drawCardNodes(canvas, primary, accent, density)
        canvas.restore()
    }

    private fun drawCardConnections(canvas: Canvas, primary: Int, accent: Int, density: Float) {
        cardNodes.forEachIndexed { firstIndex, first ->
            cardNodes.drop(firstIndex + 1).forEach { second ->
                val sharedTags = first.card.tags.intersect(second.card.tags.toSet()).isNotEmpty()
                val sameSource = first.card.sourceType == second.card.sourceType
                if (sharedTags ||
                    (sameSource && (first.card.id.hashCode() xor second.card.id.hashCode()) % 7 == 0)
                ) {
                    linePaint.color = ColorUtils.setAlphaComponent(
                        if (sharedTags) accent else primary,
                        if (sharedTags) 48 else 22
                    )
                    linePaint.strokeWidth = if (sharedTags) 1.05f * density else 0.65f * density
                    canvas.drawLine(
                        first.nx * width,
                        first.ny * height,
                        second.nx * width,
                        second.ny * height,
                        linePaint
                    )
                }
            }
        }
    }

    private fun drawForeshadowThreads(
        canvas: Canvas,
        primary: Int,
        accent: Int,
        density: Float
    ) {
        threadNodes.forEachIndexed { index, node ->
            val watching = node.thread.state == ForeshadowState.WATCHING
            val selected = node.thread.id == selectedThreadId
            val color = if (node.thread.suggestedOutcome != null || !watching) accent else primary
            threadPaint.color = ColorUtils.setAlphaComponent(
                color,
                if (selected) 210 else if (watching) 92 else 145
            )
            threadPaint.strokeWidth = (if (selected) 2.1f else 1.25f) * density
            threadPaint.pathEffect = if (watching) {
                DashPathEffect(floatArrayOf(5f * density, 7f * density), phase * 24f * density)
            } else null

            threadPath.reset()
            val first = node.points.first()
            threadPath.moveTo(first.x * width, first.y * height)
            node.points.zipWithNext().forEachIndexed { segmentIndex, (from, to) ->
                val fromX = from.x * width
                val fromY = from.y * height
                val toX = to.x * width
                val toY = to.y * height
                val bend = if ((node.thread.id.hashCode() + segmentIndex) and 1 == 0) 1f else -1f
                val controlX = (fromX + toX) / 2f + (toY - fromY) * 0.16f * bend
                val controlY = (fromY + toY) / 2f - (toX - fromX) * 0.16f * bend
                threadPath.quadTo(controlX, controlY, toX, toY)
            }
            canvas.drawPath(threadPath, threadPaint)
            threadPaint.pathEffect = null

            node.points.take(node.sourcePointCount).forEach { point ->
                nodePaint.shader = null
                nodePaint.style = Paint.Style.STROKE
                nodePaint.strokeWidth = 1f * density
                nodePaint.color = ColorUtils.setAlphaComponent(color, 135)
                canvas.drawCircle(point.x * width, point.y * height, 3.3f * density, nodePaint)
            }

            val end = node.points.last()
            val endX = end.x * width
            val endY = end.y * height
            val pulse = 0.88f + sin(phase * 6.28f + index) * 0.12f
            val radius = (if (selected) 7.2f else 5.2f) * density * pulse
            nodePaint.style = Paint.Style.FILL
            nodePaint.shader = RadialGradient(
                endX,
                endY,
                radius * 4.5f,
                intArrayOf(
                    ColorUtils.setAlphaComponent(color, if (selected) 235 else 190),
                    ColorUtils.setAlphaComponent(color, 52),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.38f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(endX, endY, radius * 4.5f, nodePaint)
            nodePaint.shader = null
            if (watching) {
                nodePaint.style = Paint.Style.STROKE
                nodePaint.strokeWidth = 1.4f * density
                nodePaint.color = color
                canvas.drawCircle(endX, endY, radius, nodePaint)
            } else {
                nodePaint.style = Paint.Style.FILL
                nodePaint.color = color
                canvas.drawCircle(endX, endY, radius, nodePaint)
            }

            if (selected) {
                labelPaint.color = ThemeColors.textPrimary(context)
                canvas.drawText(
                    node.thread.title.take(12),
                    endX,
                    endY + radius + 18f * density,
                    labelPaint
                )
            }
        }
        nodePaint.style = Paint.Style.FILL
    }

    private fun drawCardNodes(canvas: Canvas, primary: Int, accent: Int, density: Float) {
        cardNodes.forEachIndexed { index, node ->
            val x = node.nx * width
            val y = node.ny * height
            val selected = node.card.id == selectedCardId
            val pulse = 0.84f + sin(phase * 6.28f + index * 0.6f) * 0.16f
            val radius = node.radius * density * if (selected) 1.45f else pulse
            nodePaint.style = Paint.Style.FILL
            nodePaint.shader = RadialGradient(
                x,
                y,
                radius * 3.6f,
                intArrayOf(
                    ColorUtils.setAlphaComponent(
                        if (node.card.pinned) accent else primary,
                        if (selected) 230 else 175
                    ),
                    ColorUtils.setAlphaComponent(accent, 46),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(x, y, radius * 3.6f, nodePaint)
            nodePaint.shader = null
            nodePaint.color = if (node.card.pinned) accent else primary
            canvas.drawCircle(x, y, radius, nodePaint)
            if (selected) {
                labelPaint.color = ThemeColors.textPrimary(context)
                val label = node.card.quote.replace('\n', ' ').take(12)
                canvas.drawText(label, x, y + radius + 18f * density, labelPaint)
            }
        }
    }

    private fun pointFor(id: String, sourceIndex: Int, threadIndex: Int): PointF {
        val seed = id.hashCode().toLong() and 0xffffffffL
        val angle = (seed % 6283L) / 1000f + sourceIndex * 0.87f + threadIndex * 0.31f
        val ring = 0.18f + ((seed / 17L + sourceIndex + threadIndex) % 5L) * 0.055f
        return PointF(
            (0.5f + cos(angle) * ring).coerceIn(0.09f, 0.91f),
            (0.51f + sin(angle) * ring * 0.74f).coerceIn(0.13f, 0.87f)
        )
    }

    private fun animationsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
}
