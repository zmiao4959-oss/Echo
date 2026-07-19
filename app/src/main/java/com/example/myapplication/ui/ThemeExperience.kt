package com.example.myapplication.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import androidx.core.widget.NestedScrollView
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.google.android.material.card.MaterialCardView

/** One interaction grammar shared by navigation, cards and state changes. */
object ThemeExperience {

    fun apply(root: View) {
        val config = (root.context.applicationContext as? MyApplication)?.appConfig ?: return
        val spec = ThemeManager.specFor(config.themeKey)
        decorateTree(root, spec)
    }

    fun enter(root: View) {
        val config = (root.context.applicationContext as? MyApplication)?.appConfig ?: return
        val experience = ThemeManager.specFor(config.themeKey).experience
        if (!animationsEnabled()) return
        root.animate().cancel()
        root.alpha = 0f
        when (experience) {
            ThemeManager.Experience.PAPER -> {
                root.pivotX = 0f
                root.rotationY = -3.2f
                root.translationX = dp(root, 18f)
            }
            ThemeManager.Experience.ARCHIVE -> {
                root.scaleX = .985f; root.scaleY = .985f; root.translationY = dp(root, 10f)
            }
            ThemeManager.Experience.FILM -> {
                root.scaleX = 1.025f; root.scaleY = 1.025f
            }
            ThemeManager.Experience.CEDAR -> root.translationY = dp(root, -14f)
            ThemeManager.Experience.TIDE -> root.translationX = dp(root, 22f)
            ThemeManager.Experience.ORBIT -> {
                root.pivotX = root.width * .5f; root.pivotY = root.height.toFloat()
                root.rotation = 1.2f; root.scaleX = .985f; root.scaleY = .985f
            }
            ThemeManager.Experience.GROVE -> {
                root.translationY = dp(root, 16f); root.scaleY = .975f
            }
            ThemeManager.Experience.INK -> {
                root.translationY = dp(root, -8f); root.scaleX = .992f
            }
        }
        root.animate()
            .alpha(1f).translationX(0f).translationY(0f)
            .rotation(0f).rotationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(duration(experience))
            .setInterpolator(DecelerateInterpolator(1.45f))
            .start()
    }

    fun dateChange(view: View) {
        if (!animationsEnabled()) return
        val config = (view.context.applicationContext as? MyApplication)?.appConfig ?: return
        val experience = ThemeManager.specFor(config.themeKey).experience
        view.animate().cancel()
        when (experience) {
            ThemeManager.Experience.PAPER, ThemeManager.Experience.CEDAR -> {
                view.translationX = dp(view, -10f); view.alpha = .25f
            }
            ThemeManager.Experience.FILM -> {
                view.scaleX = 1.08f; view.scaleY = 1.08f; view.alpha = .15f
            }
            ThemeManager.Experience.ORBIT -> {
                view.rotation = -3f; view.alpha = .2f
            }
            else -> {
                view.translationY = dp(view, 7f); view.alpha = .2f
            }
        }
        view.animate().alpha(1f).translationX(0f).translationY(0f)
            .rotation(0f).scaleX(1f).scaleY(1f)
            .setDuration(260L).setInterpolator(DecelerateInterpolator()).start()
    }

    fun expand(view: View, expanded: Boolean) {
        if (!animationsEnabled()) return
        view.animate().cancel()
        view.animate()
            .scaleX(if (expanded) 1.012f else 1f)
            .scaleY(if (expanded) 1.012f else 1f)
            .setDuration(130L)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(150L).start()
            }.start()
    }

    private fun decorateTree(view: View, spec: ThemeManager.ThemeSpec) {
        if (view is MaterialCardView) {
            installChrome(view, spec)
            installPressMotion(view, spec.experience)
        }
        if (view is NestedScrollView) installScrollMotion(view, spec.experience)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) decorateTree(view.getChildAt(index), spec)
        }
    }

    private fun installScrollMotion(scroll: NestedScrollView, experience: ThemeManager.Experience) {
        if (scroll.getTag(R.id.tag_theme_scroll_motion) == experience.name) return
        var lastY = scroll.scrollY
        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            val child = scroll.getChildAt(0) ?: return@setOnScrollChangeListener
            val delta = (y - lastY).coerceIn(-24, 24)
            lastY = y
            val direction = if (delta == 0) 0f else if (delta > 0) -1f else 1f
            child.animate().cancel()
            when (experience) {
                ThemeManager.Experience.PAPER, ThemeManager.Experience.CEDAR -> child.translationX = direction * dp(scroll, 1.2f)
                ThemeManager.Experience.ORBIT -> child.rotation = direction * .08f
                ThemeManager.Experience.TIDE -> child.translationX = direction * dp(scroll, 1.8f)
                else -> child.translationY = direction * dp(scroll, .8f)
            }
            child.animate().translationX(0f).translationY(0f).rotation(0f)
                .setStartDelay(26L).setDuration(190L).start()
        }
        scroll.setTag(R.id.tag_theme_scroll_motion, experience.name)
    }

    private fun installChrome(card: MaterialCardView, spec: ThemeManager.ThemeSpec) {
        if (card.getTag(R.id.tag_theme_chrome) == spec.key) return
        card.overlay.clear()
        val chrome = ThemeChromeDrawable(
            spec.experience,
            ColorUtils.setAlphaComponent(ThemeColors.accent(card.context), 115),
            card.resources.displayMetrics.density
        )
        fun updateBounds() = chrome.setBounds(0, 0, card.width, card.height)
        updateBounds()
        card.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateBounds() }
        card.overlay.add(chrome)
        card.setTag(R.id.tag_theme_chrome, spec.key)
    }

    private fun installPressMotion(view: View, experience: ThemeManager.Experience) {
        if (!view.isClickable || view.getTag(R.id.tag_theme_press_motion) == experience.name) return
        val pressedScale = when (experience) {
            ThemeManager.Experience.FILM -> .985f
            ThemeManager.Experience.ORBIT -> .975f
            ThemeManager.Experience.PAPER, ThemeManager.Experience.CEDAR -> .99f
            else -> .982f
        }
        fun set(scale: Float, duration: Long): AnimatorSet = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, scale),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, scale),
            )
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
        }
        view.stateListAnimator = StateListAnimator().apply {
            addState(intArrayOf(android.R.attr.state_pressed), set(pressedScale, 85L))
            addState(intArrayOf(), set(1f, 160L))
        }
        view.setTag(R.id.tag_theme_press_motion, experience.name)
    }

    private fun duration(experience: ThemeManager.Experience): Long = when (experience) {
        ThemeManager.Experience.FILM -> 220L
        ThemeManager.Experience.PAPER, ThemeManager.Experience.CEDAR -> 330L
        ThemeManager.Experience.ORBIT -> 420L
        else -> 360L
    }

    private fun animationsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    private fun dp(view: View, value: Float): Float = value * view.resources.displayMetrics.density
}

private class ThemeChromeDrawable(
    private val experience: ThemeManager.Experience,
    color: Int,
    private val density: Float,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        when (experience) {
            ThemeManager.Experience.PAPER -> repeat(4) { i -> canvas.drawCircle(7f*density, h*.28f+i*12f*density, 1.4f*density, fill) }
            ThemeManager.Experience.ARCHIVE -> {
                val d=10f*density
                canvas.drawLine(d, d, d*2.4f, d, paint); canvas.drawLine(d, d, d, d*2.4f, paint)
                canvas.drawLine(w-d, h-d, w-d*2.4f, h-d, paint); canvas.drawLine(w-d, h-d, w-d, h-d*2.4f, paint)
            }
            ThemeManager.Experience.FILM -> {
                repeat((w/(18f*density)).toInt().coerceAtMost(22)) { i ->
                    val x=12f*density+i*18f*density
                    canvas.drawRoundRect(x, 5f*density, x+7f*density, 9f*density, 1.5f*density, 1.5f*density, fill)
                    canvas.drawRoundRect(x, h-9f*density, x+7f*density, h-5f*density, 1.5f*density, 1.5f*density, fill)
                }
            }
            ThemeManager.Experience.CEDAR -> canvas.drawRoundRect(w-18f*density, 0f, w-8f*density, 24f*density, 0f, 0f, fill)
            ThemeManager.Experience.TIDE -> {
                path.reset(); path.moveTo(0f,h-10f*density); path.cubicTo(w*.25f,h-18f*density,w*.55f,h,w,h-12f*density); canvas.drawPath(path,paint)
            }
            ThemeManager.Experience.ORBIT -> canvas.drawArc(-w*.12f, -h*.65f, w*.72f, h*.72f, 18f, 94f, false, paint)
            ThemeManager.Experience.GROVE -> {
                path.reset(); path.moveTo(w-8f*density,h*.15f); path.quadTo(w-28f*density,h*.42f,w-12f*density,h*.72f); canvas.drawPath(path,paint)
                canvas.drawLine(w-20f*density,h*.35f,w-33f*density,h*.30f,paint)
                canvas.drawLine(w-17f*density,h*.48f,w-4f*density,h*.42f,paint)
            }
            ThemeManager.Experience.INK -> {
                paint.pathEffect = DashPathEffect(floatArrayOf(16f*density, 5f*density, 3f*density, 7f*density), 0f)
                canvas.drawLine(8f*density,h-7f*density,w-8f*density,h-7f*density,paint)
                paint.pathEffect = null
            }
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; fill.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter; fill.colorFilter = colorFilter }
    @Deprecated("Deprecated in Android") override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
