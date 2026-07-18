package com.example.myapplication.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.ui.diary.DiaryViewModel
import com.example.myapplication.ui.diary.DiaryFragment
import com.example.myapplication.ui.memory.MemoryFragment
import com.example.myapplication.ui.plan.PlanFragment
import com.example.myapplication.ui.today.TodayFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ThemedActivity() {

    private lateinit var topBar: View
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var btnProfile: ImageButton
    private lateinit var btnCalendar: ImageButton

    private val diaryViewModel: DiaryViewModel by lazy {
        ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory(application as MyApplication))
            .get(DiaryViewModel::class.java)
    }

    // 持有 Fragment 实例，避免重复创建
    private var todayFragment: TodayFragment? = null
    private var diaryFragment: DiaryFragment? = null
    private var planFragment: PlanFragment? = null
    private var memoryFragment: MemoryFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val app = application as MyApplication

        BackgroundManager.apply(this, app.appConfig.backgroundKey)

        if (!app.appConfig.isLLMConfigured) {
            Toast.makeText(this, getString(R.string.toast_configure_llm_first), Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        topBar = findViewById(R.id.top_bar)

        btnCalendar = findViewById(R.id.btn_calendar)
        btnCalendar.setOnClickListener { openCalendar() }

        btnProfile = findViewById(R.id.btn_profile)
        btnProfile.setOnClickListener { openProfile(it) }
        applyAvatar()

        bottomNav = findViewById(R.id.bottom_navigation)
        applyBottomNavTint()
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_today -> showTodayFragment()
                R.id.nav_diary -> showDiaryFragment()
                R.id.nav_plan -> showPlanFragment()
                R.id.nav_memory -> showMemoryFragment()
                else -> false
            }
        }

        // 默认显示今日页
        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_today
        }

        // 定时闹钟已在 MyApplication.onCreate() 中统一恢复，此处不再重复

        // Edge-to-Edge: 处理系统栏 insets
        setupInsets()

        // 请求通知权限 (Android 13+)
        requestNotificationPermissionIfNeeded()
        applyPageTextures()
    }

    override fun onResume() {
        super.onResume()
        if (ThemeManager.pendingChange) {
            ThemeManager.pendingChange = false
            recreate()
            return
        }
        val app = application as MyApplication
        BackgroundManager.apply(this, app.appConfig.backgroundKey)
        applyAvatar()
        applyPageTextures()
    }

    private fun applyPageTextures() {
        val config = (application as MyApplication).appConfig
        PageTextureManager.apply(topBar, config.getPageTextureKey(PageTextureManager.TOP_BAR))
        PageTextureManager.apply(bottomNav, config.getPageTextureKey(PageTextureManager.BOTTOM_BAR))
    }

    /** Edge-to-Edge: 为顶栏/底栏/内容容器补充系统栏的 padding */
    private fun setupInsets() {
        val fragmentContainer: View = findViewById(R.id.fragment_container)

        // 顶栏：顶部留出状态栏高度，让 topBar 背景延伸到状态栏后方但内容不被遮挡
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        // 底部导航：底部留出导航栏高度
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = navBars.bottom)
            insets
        }

        // 内容容器：底部额外留出导航栏高度（已通过 XML paddingBottom="64dp" 留出 bottomNav 空间）
        ViewCompat.setOnApplyWindowInsetsListener(fragmentContainer) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = navBars.bottom + (64 * resources.displayMetrics.density).toInt())
            insets
        }
    }

    // ── Fragment 切换 ──

    private fun showTodayFragment(): Boolean {
        if (todayFragment == null) {
            todayFragment = TodayFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, todayFragment!!)
            .commit()
        return true
    }

    private fun showDiaryFragment(): Boolean {
        if (diaryFragment == null) {
            diaryFragment = DiaryFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, diaryFragment!!)
            .commit()
        return true
    }

    private fun showPlanFragment(): Boolean {
        if (planFragment == null) {
            planFragment = PlanFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, planFragment!!)
            .commit()
        return true
    }

    private fun showMemoryFragment(): Boolean {
        if (memoryFragment == null) {
            memoryFragment = MemoryFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, memoryFragment!!)
            .commit()
        return true
    }

    // ── 通知权限 ──

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_NOTIFICATIONS
                )
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_NOTIFICATIONS = 1001
    }

    // ── 顶部栏按钮 ──

    private fun applyBottomNavTint() {
        val checkedColor = ThemeColors.primary(this)
        val defaultColor = ThemeColors.textSecondary(this)
        val colorStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(checkedColor, defaultColor)
        )
        bottomNav.itemIconTintList = colorStateList
        bottomNav.itemTextColor = colorStateList
    }

    private fun applyAvatar() {
        val path = (application as MyApplication).appConfig.avatarPath
        AvatarManager.applyToImageView(btnProfile, path)
    }

    private fun openCalendar() {
        val dialog = CalendarDialog(this) { date ->
            diaryViewModel.generateDiaryForDate(date)
            lifecycleScope.launch {
                diaryViewModel.statusMessage.collectLatest { msg ->
                    msg?.let { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
                }
            }
        }
        dialog.show()
    }

    fun openProfile(view: View) {
        startActivity(Intent(this, ProfileActivity::class.java))
    }

}
