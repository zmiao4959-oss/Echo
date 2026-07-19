package com.example.myapplication.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.myapplication.MyApplication
import com.example.myapplication.ui.ThemeColors
import com.example.myapplication.ui.ThemeManager
import kotlin.math.PI
import kotlin.math.sin

/** A text-compatible empty state: authored art above, living motif only for dynamic themes. */
class ThemeStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val spec = ThemeManager.specFor((context.applicationContext as MyApplication).appConfig.themeKey)
    private val density = resources.displayMetrics.density
    private var artWidth = 0f
    private var artHeight = 0f

    init {
        gravity = android.view.Gravity.CENTER
        compoundDrawablePadding = (12f * density).toInt()
        setLineSpacing(3f * density, 1f)
        val artwork = ContextCompat.getDrawable(context, spec.stateArtworkRes)?.mutate()
        artwork?.alpha = 218
        artwork?.setBounds(0, 0, (220f * density).toInt(), (147f * density).toInt())
        setCompoundDrawables(null, artwork, null, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        artWidth = (w - paddingLeft - paddingRight).coerceAtMost((220f * density).toInt()).toFloat()
        artHeight = artWidth * 2f / 3f
        compoundDrawables[1]?.setBounds(0, 0, artWidth.toInt(), artHeight.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (spec.kind != ThemeManager.Kind.DYNAMIC || artWidth <= 0f || !animationsEnabled()) return
        val top = paddingTop.toFloat()
        val left = (width - artWidth) / 2f
        val time = (System.currentTimeMillis() % 8000L) / 8000f
        paint.color = ColorUtils.setAlphaComponent(ThemeColors.accent(context), 125)
        fill.color = ColorUtils.setAlphaComponent(ThemeColors.primary(context), 125)
        paint.strokeWidth = 1.2f * density
        when (spec.motion) {
            ThemeManager.Motion.TIDE -> {
                path.reset(); path.moveTo(left, top + artHeight*.62f)
                val shift = sin(time * 2f * PI).toFloat() * 10f*density
                path.cubicTo(left+artWidth*.24f,top+artHeight*.55f+shift,left+artWidth*.68f,top+artHeight*.72f-shift,left+artWidth,top+artHeight*.60f)
                canvas.drawPath(path, paint)
            }
            ThemeManager.Motion.ORBIT -> {
                val a=time*2f*PI
                val cx=left+artWidth*.5f; val cy=top+artHeight*.5f
                canvas.drawCircle(cx + kotlin.math.cos(a).toFloat()*artWidth*.30f, cy + sin(a).toFloat()*artHeight*.22f, 2.3f*density, fill)
            }
            ThemeManager.Motion.GROVE -> {
                val breathe=.96f + sin(time*2f*PI).toFloat()*.04f
                canvas.save(); canvas.scale(breathe,breathe,left+artWidth*.72f,top+artHeight*.52f)
                canvas.drawOval(left+artWidth*.67f,top+artHeight*.40f,left+artWidth*.77f,top+artHeight*.60f,paint); canvas.restore()
            }
            ThemeManager.Motion.INK_RAIN -> {
                val y=top+artHeight*(.20f+time*.52f)
                canvas.drawCircle(left+artWidth*.5f,y,2.5f*density,fill)
                canvas.drawCircle(left+artWidth*.5f,top+artHeight*.76f,artWidth*(.05f+time*.08f),paint)
            }
            else -> Unit
        }
        postInvalidateOnAnimation()
    }

    private fun animationsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
}
