package com.example.myapplication.ui

import android.app.Activity
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * 字体管理器 — 4 种系统字体可选。
 *
 * 通过 ThemedActivity 的 setContentView 钩子自动应用到所有 TextView，
 * 无需手动调用。
 */
object FontManager {

    const val FONT_DEFAULT = "default"
    const val FONT_LIGHT = "light"
    const val FONT_SERIF = "serif"
    const val FONT_MONO = "mono"

    /** key → 显示名 */
    val fontNames = mapOf(
        FONT_DEFAULT to "跟随主题",
        FONT_LIGHT to "轻体",
        FONT_SERIF to "衬线",
        FONT_MONO to "等宽"
    )

    /** key → Typeface (懒加载，缓存) */
    private val typefaceCache = mutableMapOf<String, Typeface>()

    private fun getTypeface(key: String): Typeface = typefaceCache.getOrPut(key) {
        when (key) {
            FONT_LIGHT -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            FONT_SERIF -> Typeface.create("serif", Typeface.NORMAL)
            FONT_MONO -> Typeface.create("monospace", Typeface.NORMAL)
            FONT_DEFAULT -> Typeface.DEFAULT
            else -> Typeface.create(key, Typeface.NORMAL)
        }
    }

    /**
     * 递归遍历 Activity 的所有 View，为每个 TextView 设置字体。
     * 由 ThemedActivity.setContentView() 自动调用。
     */
    fun applyToActivity(activity: Activity, fontKey: String, themeKey: String) {
        applyToView(activity.window.decorView, fontKey, themeKey)
    }

    fun applyToView(root: View, fontKey: String, themeKey: String) {
        val typography = ThemeManager.specFor(themeKey).typography
        val profile = profileFor(typography)
        val resolvedKey = if (fontKey == FONT_DEFAULT) profile.family else fontKey
        applyRecursive(root, getTypeface(resolvedKey), profile)
    }

    private data class TypographyProfile(
        val family: String,
        val headingSpacing: Float,
        val bodySpacing: Float,
    )

    private fun profileFor(typography: ThemeManager.Typography): TypographyProfile = when (typography) {
        ThemeManager.Typography.EDITORIAL -> TypographyProfile("serif", 0.018f, 0.002f)
        ThemeManager.Typography.CARTOGRAPHIC -> TypographyProfile("sans-serif-condensed", 0.045f, 0.012f)
        ThemeManager.Typography.CINEMATIC -> TypographyProfile("sans-serif-light", 0.055f, 0.012f)
        ThemeManager.Typography.LITERARY -> TypographyProfile("serif", 0.025f, 0.004f)
        ThemeManager.Typography.AIRY -> TypographyProfile("sans-serif-light", 0.034f, 0.008f)
        ThemeManager.Typography.CELESTIAL -> TypographyProfile("sans-serif", 0.052f, 0.014f)
        ThemeManager.Typography.ORGANIC -> TypographyProfile("sans-serif-light", 0.022f, 0.004f)
        ThemeManager.Typography.INK -> TypographyProfile("serif", 0.038f, 0.006f)
    }

    private fun applyRecursive(view: View, typeface: Typeface, profile: TypographyProfile) {
        if (view is TextView) {
            // 保留原有的 style (bold/italic)
            val style = view.typeface?.style ?: Typeface.NORMAL
            view.typeface = Typeface.create(typeface, style)
            val metrics = view.resources.displayMetrics
            val sizeSp = view.textSize / (metrics.density * view.resources.configuration.fontScale)
            view.letterSpacing = if (sizeSp >= 17f) profile.headingSpacing else profile.bodySpacing
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyRecursive(view.getChildAt(i), typeface, profile)
            }
        }
    }
}
