package com.example.myapplication.ui.widget

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.example.myapplication.ui.ThemeColors
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * Echo 的统一底部空间。面板会从来源卡片原位展开，关闭时回到原卡片，
 * 同一 Session 可以原地切换详情、编辑和删除确认状态。
 */
object EchoSheet {
    data class Action(
        val label: String,
        val destructive: Boolean = false,
        val onClick: (Session) -> Unit
    )

    fun show(
        activity: Activity,
        source: View?,
        kicker: String,
        title: String,
        body: View,
        actions: List<Action>
    ): Session {
        return Session(activity, source).also {
            it.render(kicker, title, body, actions)
            it.show()
        }
    }

    fun text(
        activity: Activity,
        value: CharSequence,
        sizeSp: Float = 15f,
        secondary: Boolean = false
    ) = TextView(activity).apply {
        text = value
        textSize = sizeSp
        setTextColor(
            if (secondary) ThemeColors.textSecondary(activity)
            else ThemeColors.textPrimary(activity)
        )
        setLineSpacing(0f, 1.25f)
    }

    fun input(
        activity: Activity,
        value: String,
        hintText: String,
        minLines: Int = 1
    ) = EditText(activity).apply {
        setText(value)
        hint = hintText
        setTextColor(ThemeColors.textPrimary(activity))
        setHintTextColor(ThemeColors.hint(activity))
        textSize = 15f
        this.minLines = minLines
        maxLines = if (minLines > 1) 8 else 1
        inputType = if (minLines > 1) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        background = GradientDrawable().apply {
            cornerRadius = 16f * resources.displayMetrics.density
            setColor(ThemeColors.surface(activity))
            setStroke(resources.displayMetrics.density.toInt().coerceAtLeast(1), ThemeColors.border(activity))
        }
        setPadding(dp(16), dp(13), dp(16), dp(13))
        setSelection(text.length)
    }

    fun vertical(activity: Activity, spacingDp: Int = 10, vararg children: View): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            children.forEachIndexed { index, child ->
                addView(child, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (index > 0) {
                        topMargin = (spacingDp * activity.resources.displayMetrics.density).toInt()
                    }
                })
            }
        }

    class Session internal constructor(
        private val activity: Activity,
        private val source: View?
    ) {
        private val root = FrameLayout(activity).apply {
            isClickable = true
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        private val dim = View(activity).apply {
            setBackgroundColor(ColorUtils.setAlphaComponent(Color.BLACK, 190))
            alpha = 0f
            setOnClickListener { dismiss() }
        }
        private val panel = MaterialCardView(activity).apply {
            radius = dp(28).toFloat()
            cardElevation = dp(18).toFloat()
            setCardBackgroundColor(ThemeColors.surfaceVariant(activity))
            strokeColor = ColorUtils.setAlphaComponent(ThemeColors.primary(activity), 92)
            strokeWidth = dp(1)
            isClickable = true
        }
        private val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(22))
        }
        private var shown = false
        private var closing = false
        private val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = dismiss()
        }

        init {
            root.addView(dim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            panel.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            root.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
                bottomMargin = dp(18)
            })
        }

        fun render(kicker: String, title: String, body: View, actions: List<Action>) {
            column.removeAllViews()
            column.addView(View(activity).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(2).toFloat()
                    setColor(ColorUtils.setAlphaComponent(ThemeColors.textSecondary(activity), 90))
                }
            }, LinearLayout.LayoutParams(dp(38), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(18)
            })
            column.addView(text(activity, kicker.uppercase(), 11f, secondary = true).apply {
                letterSpacing = 0.12f
                setTextColor(ThemeColors.accent(activity))
            })
            column.addView(text(activity, title, 22f).apply {
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(7)
            })
            val scroll = ScrollView(activity).apply {
                isFillViewport = false
                addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            column.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(16)
            })
            scroll.post {
                val maxBodyHeight = (root.height * 0.52f).toInt()
                if (maxBodyHeight > 0 && scroll.height > maxBodyHeight) {
                    scroll.layoutParams = scroll.layoutParams.apply { height = maxBodyHeight }
                }
            }
            if (actions.isNotEmpty()) {
                val actionRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                }
                actions.forEach { action ->
                    val button = MaterialButton(
                        activity,
                        null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle
                    ).apply {
                        text = action.label
                        textSize = 13f
                        isAllCaps = false
                        cornerRadius = dp(18)
                        insetTop = 0
                        insetBottom = 0
                        setTextColor(
                            if (action.destructive) ThemeColors.destructive(activity)
                            else ThemeColors.primary(activity)
                        )
                        strokeColor = android.content.res.ColorStateList.valueOf(
                            if (action.destructive) ColorUtils.setAlphaComponent(ThemeColors.destructive(activity), 100)
                            else ColorUtils.setAlphaComponent(ThemeColors.primary(activity), 85)
                        )
                        setOnClickListener {
                            EchoFeedback.play(this, if (action.destructive) EchoFeedback.Kind.DELETE else EchoFeedback.Kind.CONNECT)
                            action.onClick(this@Session)
                        }
                    }
                    actionRow.addView(button, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                        if (actionRow.childCount > 0) marginStart = dp(8)
                    })
                }
                column.addView(actionRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(20)
                })
            }
            if (shown) {
                column.alpha = 0.55f
                column.animate().alpha(1f).setDuration(190L).start()
                panel.requestLayout()
            }
        }

        internal fun show() {
            val decor = activity.findViewById<ViewGroup>(android.R.id.content)
            (activity as? ComponentActivity)?.onBackPressedDispatcher?.addCallback(backCallback)
            decor.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            root.post {
                shown = true
                dim.animate().alpha(1f).setDuration(260L).start()
                val sourceView = source?.takeIf { it.isAttachedToWindow && it.width > 0 && it.height > 0 }
                panel.pivotX = 0f
                panel.pivotY = 0f
                if (sourceView != null) {
                    val sourceLocation = IntArray(2)
                    val rootLocation = IntArray(2)
                    sourceView.getLocationOnScreen(sourceLocation)
                    root.getLocationOnScreen(rootLocation)
                    val sourceX = sourceLocation[0] - rootLocation[0]
                    val sourceY = sourceLocation[1] - rootLocation[1]
                    panel.scaleX = (sourceView.width.toFloat() / panel.width).coerceIn(0.42f, 1f)
                    panel.scaleY = (sourceView.height.toFloat() / panel.height).coerceIn(0.18f, 1f)
                    panel.translationX = sourceX - panel.x
                    panel.translationY = sourceY - panel.y
                    column.alpha = 0f
                    sourceView.alpha = 0.12f
                } else {
                    panel.translationY = panel.height * 0.34f
                    panel.alpha = 0f
                }
                panel.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(430L)
                    .setInterpolator(DecelerateInterpolator(1.35f))
                    .start()
                column.animate().alpha(1f).setStartDelay(145L).setDuration(260L).start()
                EchoFeedback.play(panel, EchoFeedback.Kind.OPEN)
            }
        }

        fun dismiss(after: (() -> Unit)? = null) {
            if (closing) return
            closing = true
            val sourceView = source?.takeIf { it.isAttachedToWindow && it.width > 0 && it.height > 0 }
            dim.animate().alpha(0f).setDuration(210L).start()
            if (sourceView != null) {
                val sourceLocation = IntArray(2)
                val rootLocation = IntArray(2)
                sourceView.getLocationOnScreen(sourceLocation)
                root.getLocationOnScreen(rootLocation)
                panel.pivotX = 0f
                panel.pivotY = 0f
                panel.animate()
                    .translationX(sourceLocation[0] - rootLocation[0] - panel.x)
                    .translationY(sourceLocation[1] - rootLocation[1] - panel.y)
                    .scaleX((sourceView.width.toFloat() / panel.width).coerceIn(0.42f, 1f))
                    .scaleY((sourceView.height.toFloat() / panel.height).coerceIn(0.18f, 1f))
                    .alpha(0f)
                    .setDuration(300L)
                    .withEndAction {
                        sourceView.alpha = 1f
                        backCallback.remove()
                        (root.parent as? ViewGroup)?.removeView(root)
                        after?.invoke()
                    }
                    .start()
            } else {
                panel.animate().translationY(panel.height * 0.28f).alpha(0f).setDuration(250L)
                    .withEndAction {
                        backCallback.remove()
                        (root.parent as? ViewGroup)?.removeView(root)
                        after?.invoke()
                    }.start()
            }
        }

        private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
    }

    private fun View.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
