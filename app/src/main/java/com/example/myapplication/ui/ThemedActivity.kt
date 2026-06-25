package com.example.myapplication.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.MyApplication

/**
 * 所有 Activity 的基类 — 自动应用用户选择的主题和字体。
 *
 * - 在 super.onCreate() 之前调用 setTheme()
 * - 在 setContentView() 之后递归应用字体到所有 TextView
 *
 * 子类只需 extends ThemedActivity 即可。
 */
abstract class ThemedActivity : AppCompatActivity() {

    private val config get() = (application as MyApplication).appConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this, config.themeKey)
        super.onCreate(savedInstanceState)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        FontManager.applyToActivity(this, config.fontKey)
    }

    override fun setContentView(view: View) {
        super.setContentView(view)
        FontManager.applyToActivity(this, config.fontKey)
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        FontManager.applyToActivity(this, config.fontKey)
    }
}
