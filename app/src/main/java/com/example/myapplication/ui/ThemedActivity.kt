package com.example.myapplication.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.myapplication.MyApplication

/**
 * 所有 Activity 的基类 — 自动应用用户选择的主题和字体 + Edge-to-Edge 全屏。
 *
 * - 在 super.onCreate() 之前调用 setTheme()
 * - 在 setContentView() 之后递归应用字体到所有 TextView
 * - 自动启用 Edge-to-Edge，内容延伸到状态栏和导航栏后方
 *
 * 子类只需 extends ThemedActivity 即可。
 */
abstract class ThemedActivity : AppCompatActivity() {

    private val config get() = (application as MyApplication).appConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this, config.themeKey)
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    fragment: Fragment,
                    view: View,
                    savedInstanceState: Bundle?,
                ) {
                    FontManager.applyToView(view, config.fontKey, config.themeKey)
                    CardTextureManager.applyThemeDefaults(view)
                    ThemeExperience.apply(view)
                    view.post { ThemeExperience.enter(view) }
                }
            },
            true,
        )
    }

    /** 启用 Edge-to-Edge：内容延伸到系统栏后方，栏位透明 */
    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        val isDark = ThemeManager.isDark(config.themeKey)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        FontManager.applyToActivity(this, config.fontKey, config.themeKey)
        CardTextureManager.applyThemeDefaults(window.decorView)
        ThemeExperience.apply(window.decorView)
    }

    override fun setContentView(view: View) {
        super.setContentView(view)
        FontManager.applyToActivity(this, config.fontKey, config.themeKey)
        CardTextureManager.applyThemeDefaults(window.decorView)
        ThemeExperience.apply(window.decorView)
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        FontManager.applyToActivity(this, config.fontKey, config.themeKey)
        CardTextureManager.applyThemeDefaults(window.decorView)
        ThemeExperience.apply(window.decorView)
    }
}
