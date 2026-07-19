package com.example.myapplication.ui

import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.View
import com.example.myapplication.R

/**
 * 主页纹理管理器 — 为四个底栏页面设置背景纹理。
 * 复用 [CardTextureManager] 的纹理图片池（内置 + 自定义）。
 */
object PageTextureManager {

    const val NONE = "none"

    const val TODAY_PAGE = "today_page"
    const val DIARY_PAGE = "diary_page"
    const val PLAN_PAGE = "plan_page"
    const val MEMORY_PAGE = "memory_page"
    const val TOP_BAR = "top_bar"
    const val BOTTOM_BAR = "bottom_bar"

    val ALL_CATEGORIES: List<String> = listOf(TODAY_PAGE, DIARY_PAGE, PLAN_PAGE, MEMORY_PAGE, TOP_BAR, BOTTOM_BAR)

    fun categoryLabel(cat: String): String = when (cat) {
        TODAY_PAGE -> "今日页"
        DIARY_PAGE -> "日记页"
        PLAN_PAGE -> "规划页"
        MEMORY_PAGE -> "回忆页"
        TOP_BAR -> "顶部栏"
        BOTTOM_BAR -> "底部导航"
        else -> cat
    }

    /**
     * 为页面根 View 设置纹理背景。textureKey == NONE 时恢复主题色。
     */
    fun apply(view: View, textureKey: String, transparentWhenNone: Boolean = false) {
        if (textureKey == NONE) {
            remove(view, transparentWhenNone)
            return
        }
        if (textureKey == CardTextureManager.PURE) {
            remove(view, false)
            return
        }
        val bitmap = CardTextureManager.loadBitmap(view.resources, textureKey) ?: run {
            remove(view, transparentWhenNone)
            return
        }
        view.background = CardTextureManager.CenterCropDrawable(bitmap)
    }

    /**
     * 移除纹理，恢复 XML 中定义的主题背景色（echoBackground）。
     */
    fun remove(view: View, transparent: Boolean = false) {
        view.background = null
        if (transparent) {
            view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            return
        }
        val tv = TypedValue()
        if (view.context.theme.resolveAttribute(R.attr.echoBackground, tv, true)) {
            view.setBackgroundColor(tv.data)
        }
    }
}
