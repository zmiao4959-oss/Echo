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
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.ui.diary.DiaryViewModel
import com.example.myapplication.ui.diary.DiaryFragment
import com.example.myapplication.ui.memory.MemoryFragment
import com.example.myapplication.ui.plan.PlanFragment
import com.example.myapplication.ui.today.QuickRecordRoute
import com.example.myapplication.ui.today.TodayFragment
import com.example.myapplication.ui.widget.EchoEnvironmentView
import com.example.myapplication.ui.widget.EchoFeedback
import com.example.myapplication.ui.widget.EchoJourneyView
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ThemedActivity() {

    private lateinit var topBar: View
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var btnProfile: ImageButton
    private lateinit var btnCalendar: ImageButton
    private lateinit var echoEnvironment: EchoEnvironmentView
    private lateinit var echoJourney: EchoJourneyView
    private var currentStage = STAGE_TODAY

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

        currentStage = savedInstanceState?.getInt(KEY_CURRENT_STAGE, STAGE_TODAY) ?: STAGE_TODAY
        restoreFragments()

        val app = application as MyApplication

        BackgroundManager.apply(this, app.appConfig.backgroundKey)

        if (!app.appConfig.isLLMConfigured) {
            Toast.makeText(this, getString(R.string.toast_configure_llm_first), Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        topBar = findViewById(R.id.top_bar)
        echoEnvironment = findViewById(R.id.echo_environment)
        echoJourney = findViewById(R.id.echo_journey)

        btnCalendar = findViewById(R.id.btn_calendar)
        btnCalendar.setOnClickListener { openCalendar() }

        btnProfile = findViewById(R.id.btn_profile)
        btnProfile.setOnClickListener { openProfile(it) }
        applyAvatar()

        bottomNav = findViewById(R.id.bottom_navigation)
        applyBottomNavTint()
        bottomNav.setOnItemSelectedListener { item ->
            val targetStage = when (item.itemId) {
                R.id.nav_today -> STAGE_TODAY
                R.id.nav_diary -> STAGE_DIARY
                R.id.nav_plan -> STAGE_PLAN
                R.id.nav_memory -> STAGE_MEMORY
                else -> currentStage
            }
            if (targetStage != currentStage) {
                echoJourney.travel(currentStage, targetStage)
                echoEnvironment.setStage(targetStage)
                EchoFeedback.play(bottomNav, EchoFeedback.Kind.CONNECT)
                currentStage = targetStage
            }
            when (item.itemId) {
                R.id.nav_today -> showTodayFragment()
                R.id.nav_diary -> showDiaryFragment()
                R.id.nav_plan -> showPlanFragment()
                R.id.nav_memory -> showMemoryFragment()
                else -> false
            }
        }

        // 提醒入口优先；普通冷启动仍默认显示今日页。
        val openedQuickRecord = handleQuickRecordIntent(intent)
        if (!openedQuickRecord && savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_today
        }

        // 定时闹钟已在 MyApplication.onCreate() 中统一恢复，此处不再重复

        // Edge-to-Edge: 处理系统栏 insets
        setupInsets()

        // 请求通知权限 (Android 13+)
        requestNotificationPermissionIfNeeded()
        applyPageTextures()

        if (savedInstanceState == null) {
            // Build the heaviest tab after the launch frame has settled. Its first real tap then
            // only reveals an already measured view, so the nav indicator keeps its own frames.
            findViewById<View>(R.id.fragment_container).postDelayed(
                { prewarmDiaryFragment() },
                DIARY_PREWARM_DELAY_MS
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickRecordIntent(intent)
    }

    private fun handleQuickRecordIntent(sourceIntent: Intent?): Boolean {
        if (!QuickRecordRoute.isQuickRecordAction(sourceIntent?.action)) return false

        val currentFragment = currentVisibleFragment()
        if (bottomNav.selectedItemId != R.id.nav_today) {
            bottomNav.selectedItemId = R.id.nav_today
        } else if (currentFragment !is TodayFragment) {
            showTodayFragment()
        }

        supportFragmentManager.setFragmentResult(
            QuickRecordRoute.RESULT_FOCUS_QUICK_INPUT,
            Bundle.EMPTY
        )
        // 一次点击只消费一次，避免配置变化后再次抢占输入焦点。
        sourceIntent?.setAction(null)
        return true
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
        val topTexture = config.getPageTextureKey(PageTextureManager.TOP_BAR)
        val bottomTexture = config.getPageTextureKey(PageTextureManager.BOTTOM_BAR)
        if (topTexture == PageTextureManager.NONE) {
            topBar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        } else {
            PageTextureManager.apply(topBar, topTexture)
        }
        if (bottomTexture == PageTextureManager.NONE) {
            bottomNav.background = ContextCompat.getDrawable(this, R.drawable.bg_bottom_navigation)
        } else {
            PageTextureManager.apply(bottomNav, bottomTexture)
        }
    }

    /** Edge-to-Edge: 为顶栏/底栏/内容容器补充系统栏的 padding */
    private fun setupInsets() {
        val fragmentContainer: View = findViewById(R.id.fragment_container)

        // 顶栏：在原有 56dp 内容高度之外增加安全区，而不是把安全区塞进固定高度。
        // 这样有刘海/强制 edge-to-edge 的设备不会裁切标题，无额外 inset 的设备保持原高度。
        val topBarContentHeight = topBar.layoutParams.height
        val topBarInitialPaddingTop = topBar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val cutoutTop = insets.displayCutout?.safeInsetTop ?: 0
            val safeTop = maxOf(statusBars.top, cutoutTop)
            v.updatePadding(top = topBarInitialPaddingTop + safeTop)
            val desiredHeight = topBarContentHeight + safeTop
            if (v.layoutParams.height != desiredHeight) {
                v.layoutParams = v.layoutParams.apply { height = desiredHeight }
            }
            insets
        }
        ViewCompat.requestApplyInsets(topBar)

        // 底部导航：底部留出导航栏高度
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = navBars.bottom)
            insets
        }

        // 内容容器：底部额外留出导航栏高度（已通过 XML paddingBottom="64dp" 留出 bottomNav 空间）
        ViewCompat.setOnApplyWindowInsetsListener(fragmentContainer) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = navBars.bottom + (88 * resources.displayMetrics.density).toInt())
            insets
        }
    }

    // ── Fragment 切换 ──

    private fun showTodayFragment(): Boolean {
        val target = todayFragment ?: TodayFragment().also { todayFragment = it }
        return showFragment(target, TAG_TODAY)
    }

    private fun showDiaryFragment(): Boolean {
        val target = diaryFragment ?: DiaryFragment().also { diaryFragment = it }
        return showFragment(target, TAG_DIARY)
    }

    /** Called by the Today page after the user explicitly chooses to build today's diary. */
    fun openDiaryAndGenerateToday() {
        bottomNav.selectedItemId = R.id.nav_diary
        diaryViewModel.generateTodayDiary()
    }

    private fun showPlanFragment(): Boolean {
        val target = planFragment ?: PlanFragment().also { planFragment = it }
        return showFragment(target, TAG_PLAN)
    }

    private fun showMemoryFragment(): Boolean {
        val target = memoryFragment ?: MemoryFragment().also { memoryFragment = it }
        return showFragment(target, TAG_MEMORY)
    }

    /**
     * Keep each tab's view hierarchy alive. `replace()` removed and rebuilt the outgoing page on
     * every tap, which made the bottom navigation animation compete with diary inflation/binding.
     */
    private fun showFragment(target: Fragment, tag: String): Boolean {
        if (target.isAdded && !target.isHidden && target == currentVisibleFragment()) return true

        val transaction = supportFragmentManager.beginTransaction().setReorderingAllowed(true)
        supportFragmentManager.fragments.forEach { fragment ->
            if (fragment != target && fragment.tag in MAIN_FRAGMENT_TAGS && fragment.isAdded && !fragment.isHidden) {
                transaction.hide(fragment)
                transaction.setMaxLifecycle(fragment, Lifecycle.State.STARTED)
            }
        }

        if (target.isAdded) {
            transaction.show(target)
        } else {
            transaction.add(R.id.fragment_container, target, tag)
        }
        transaction.setMaxLifecycle(target, Lifecycle.State.RESUMED)
        transaction.commit()
        return true
    }

    private fun restoreFragments() {
        todayFragment = supportFragmentManager.findFragmentByTag(TAG_TODAY) as? TodayFragment
        diaryFragment = supportFragmentManager.findFragmentByTag(TAG_DIARY) as? DiaryFragment
        planFragment = supportFragmentManager.findFragmentByTag(TAG_PLAN) as? PlanFragment
        memoryFragment = supportFragmentManager.findFragmentByTag(TAG_MEMORY) as? MemoryFragment
    }

    private fun prewarmDiaryFragment() {
        if (
            isFinishing || isDestroyed || supportFragmentManager.isStateSaved ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) return
        val target = diaryFragment ?: DiaryFragment().also { diaryFragment = it }
        if (target.isAdded || currentVisibleFragment() is DiaryFragment) return

        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .add(R.id.fragment_container, target, TAG_DIARY)
            .hide(target)
            .setMaxLifecycle(target, Lifecycle.State.STARTED)
            .commitNow()
        // Fragment hide uses GONE, which skips measurement. INVISIBLE keeps the prewarmed page
        // non-interactive and non-drawing while allowing its six-card first batch to be measured.
        target.requireView().visibility = View.INVISIBLE
        // Populate and bind the first batch while the page is hidden.
        diaryViewModel.loadDiaries()
    }

    private fun currentVisibleFragment(): Fragment? =
        supportFragmentManager.fragments.lastOrNull { it.isAdded && !it.isHidden && it.tag in MAIN_FRAGMENT_TAGS }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_CURRENT_STAGE, currentStage)
        super.onSaveInstanceState(outState)
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
        private const val KEY_CURRENT_STAGE = "main_current_stage"
        private const val DIARY_PREWARM_DELAY_MS = 900L
        private const val TAG_TODAY = "main:today"
        private const val TAG_DIARY = "main:diary"
        private const val TAG_PLAN = "main:plan"
        private const val TAG_MEMORY = "main:memory"
        private val MAIN_FRAGMENT_TAGS = setOf(TAG_TODAY, TAG_DIARY, TAG_PLAN, TAG_MEMORY)
        const val STAGE_TODAY = 0
        const val STAGE_DIARY = 1
        const val STAGE_PLAN = 2
        const val STAGE_MEMORY = 3
    }

    fun updateEnvironment(weatherDescription: String) {
        echoEnvironment.setWeather(weatherDescription)
    }

    fun revealRelation(fromStage: Int, toStage: Int, source: View? = null) {
        echoJourney.travel(fromStage, toStage)
        EchoFeedback.play(source ?: bottomNav, EchoFeedback.Kind.CONNECT)
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
        bottomNav.itemActiveIndicatorColor = ColorStateList.valueOf(ThemeColors.surfaceVariant(this))
    }

    private fun applyAvatar() {
        val path = (application as MyApplication).appConfig.avatarPath
        AvatarManager.applyToImageView(btnProfile, path)
    }

    private fun openCalendar() {
        EchoFeedback.play(btnCalendar, EchoFeedback.Kind.OPEN)
        ThemeExperience.dateChange(btnCalendar)
        val dialog = CalendarDialog(this) { date ->
            EchoFeedback.play(btnCalendar, EchoFeedback.Kind.CONNECT)
            ThemeExperience.dateChange(btnCalendar)
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
        EchoFeedback.play(view, EchoFeedback.Kind.OPEN)
        startActivity(Intent(this, ProfileActivity::class.java))
    }

}
