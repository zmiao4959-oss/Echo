package com.example.myapplication.ui

import android.app.Activity
import com.example.myapplication.R

/**
 * Echo 主题管理器 — 提供 5 套可选主题的运行时切换。
 *
 * 用法：
 * - Activity 中在 super.onCreate() 之前调用 applyTheme()
 * - 通过 ThemedActivity 基类自动处理
 */
object ThemeManager {

    const val THEME_WARM_TEA = "warm_tea"
    const val THEME_FOREST = "forest"
    const val THEME_OCEAN = "ocean"
    const val THEME_TWILIGHT = "twilight"
    const val THEME_DARK = "dark"

    /** key → 主题显示名 */
    val themeNames = mapOf(
        THEME_WARM_TEA to "暖茶",
        THEME_FOREST to "森林绿",
        THEME_OCEAN to "深海蓝",
        THEME_TWILIGHT to "暮色紫",
        THEME_DARK to "暗夜"
    )

    private val themeResMap = mapOf(
        THEME_WARM_TEA to R.style.Theme_MyApplication,
        THEME_FOREST to R.style.Theme_MyApplication_Forest,
        THEME_OCEAN to R.style.Theme_MyApplication_Ocean,
        THEME_TWILIGHT to R.style.Theme_MyApplication_Twilight,
        THEME_DARK to R.style.Theme_MyApplication_Dark,
    )

    fun getThemeRes(themeKey: String): Int =
        themeResMap[themeKey] ?: R.style.Theme_MyApplication

    /**
     * 在 Activity.setContentView() 之前应用主题。
     * 需在 super.onCreate() 之前调用。
     */
    fun applyTheme(activity: Activity, themeKey: String) {
        activity.setTheme(getThemeRes(themeKey))
    }

    /**
     * 当用户在 ProfileActivity 中切换主题后设置为 true。
     * MainActivity.onResume() 检测到此标记后会 recreate 自身以刷新主题。
     */
    var pendingChange: Boolean = false
}
