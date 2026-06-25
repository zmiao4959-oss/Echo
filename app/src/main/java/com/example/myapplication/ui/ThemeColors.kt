package com.example.myapplication.ui

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import com.example.myapplication.R

/**
 * 从当前 Activity/Fragment 的 Theme 中解析 echo 语义色。
 *
 * 用法：
 *   val primaryColor = ThemeColors.primary(context)
 *   val textColor = ThemeColors.textPrimary(context)
 *
 * 所有方法都通过 context.theme.resolveAttribute() 动态解析，
 * 因此主题切换后无需额外处理即可获得新颜色。
 */
object ThemeColors {

    fun getColor(context: Context, @AttrRes attrRes: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }

    fun primary(context: Context) = getColor(context, R.attr.echoPrimary)
    fun primaryDark(context: Context) = getColor(context, R.attr.echoPrimaryDark)
    fun background(context: Context) = getColor(context, R.attr.echoBackground)
    fun surface(context: Context) = getColor(context, R.attr.echoSurface)
    fun surfaceVariant(context: Context) = getColor(context, R.attr.echoSurfaceVariant)
    fun textPrimary(context: Context) = getColor(context, R.attr.echoTextPrimary)
    fun textSecondary(context: Context) = getColor(context, R.attr.echoTextSecondary)
    fun accent(context: Context) = getColor(context, R.attr.echoAccent)
    fun destructive(context: Context) = getColor(context, R.attr.echoDestructive)
    fun hint(context: Context) = getColor(context, R.attr.echoHint)
    fun border(context: Context) = getColor(context, R.attr.echoBorder)
    fun onPrimary(context: Context) = getColor(context, R.attr.echoOnPrimary)
    fun bubbleUser(context: Context) = getColor(context, R.attr.echoBubbleUser)
    fun bubbleAssistant(context: Context) = getColor(context, R.attr.echoBubbleAssistant)
    fun toolInfoBg(context: Context) = getColor(context, R.attr.echoToolInfoBg)

    /**
     * 返回基于当前主题的情绪图表调色板（8 色）。
     * 从 echoPrimary 和 echoAccent 衍生出渐变色调。
     */
    fun moodPalette(context: Context): IntArray {
        val primary = primary(context)
        val accent = accent(context)

        fun blend(front: Int, back: Int, ratio: Float): Int {
            val a = (front shr 24) and 0xFF
            val r = ((front shr 16) and 0xFF)
            val g = ((front shr 8) and 0xFF)
            val b = (front and 0xFF)
            val ba = (back shr 24) and 0xFF
            val br = ((back shr 16) and 0xFF)
            val bg = ((back shr 8) and 0xFF)
            val bb = (back and 0xFF)
            val inv = 1f - ratio
            return ((a * inv + ba * ratio).toInt() shl 24) or
                    ((r * inv + br * ratio).toInt() shl 16) or
                    ((g * inv + bg * ratio).toInt() shl 8) or
                    (b * inv + bb * ratio).toInt()
        }

        val white = 0xFFFFFFFF.toInt()
        return intArrayOf(
            primary,
            accent,
            blend(primary, white, 0.3f),
            blend(accent, white, 0.2f),
            blend(primary, white, 0.5f),
            blend(accent, white, 0.4f),
            blend(primary, white, 0.15f),
            blend(accent, white, 0.6f),
        )
    }
}
