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
        FONT_DEFAULT to "系统默认",
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
            else -> Typeface.DEFAULT  // FONT_DEFAULT 或未知 key
        }
    }

    /**
     * 递归遍历 Activity 的所有 View，为每个 TextView 设置字体。
     * 由 ThemedActivity.setContentView() 自动调用。
     */
    fun applyToActivity(activity: Activity, fontKey: String) {
        if (fontKey == FONT_DEFAULT) return  // 默认字体无需处理
        val typeface = getTypeface(fontKey)
        val root = activity.window.decorView
        applyRecursive(root, typeface)
    }

    private fun applyRecursive(view: View, typeface: Typeface) {
        if (view is TextView) {
            // 保留原有的 style (bold/italic)
            val style = view.typeface?.style ?: Typeface.NORMAL
            view.typeface = Typeface.create(typeface, style)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyRecursive(view.getChildAt(i), typeface)
            }
        }
    }
}
