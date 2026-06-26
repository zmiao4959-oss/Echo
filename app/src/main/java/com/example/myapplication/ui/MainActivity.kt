package com.example.myapplication.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.ui.diary.DiaryFragment
import com.example.myapplication.ui.memory.MemoryFragment
import com.example.myapplication.ui.plan.PlanFragment
import com.example.myapplication.ui.today.TodayFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MainActivity : ThemedActivity() {

    private lateinit var topBar: View
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var weatherBar: View
    private lateinit var weatherIcon: TextView
    private lateinit var weatherInfo: TextView

    private val weatherClient = com.example.myapplication.net.HttpClient.instance.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

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
        weatherBar = findViewById(R.id.weather_bar)
        weatherIcon = findViewById(R.id.weather_icon)
        weatherInfo = findViewById(R.id.weather_info)

        findViewById<ImageButton>(R.id.btn_profile).setOnClickListener { openProfile(it) }

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
        fetchDailyWeather()
        applyPageTextures()
    }

    private fun applyPageTextures() {
        val config = (application as MyApplication).appConfig
        PageTextureManager.apply(topBar, config.getPageTextureKey(PageTextureManager.TOP_BAR))
        PageTextureManager.apply(bottomNav, config.getPageTextureKey(PageTextureManager.BOTTOM_BAR))
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

    fun openProfile(view: View) {
        startActivity(Intent(this, ProfileActivity::class.java))
    }

    // ── 天气条 ──

    private fun fetchDailyWeather() {
        val prefs = getSharedPreferences("clawspeaker_config", MODE_PRIVATE)
        val cacheTime = prefs.getLong("weather_cache_time", 0L)
        val cacheAge = System.currentTimeMillis() - cacheTime
        val app = application as MyApplication
        val currentCity = app.appConfig.weatherCity
        val cachedCity = prefs.getString("weather_cache_city", null)
        val cacheValid = cacheAge in 0..30 * 60 * 1000L
                && cachedCity == currentCity  // 城市变了则失效缓存

        if (cacheValid) {
            val icon = prefs.getString("weather_icon", null) ?: return
            val info = prefs.getString("weather_info", null) ?: return
            weatherIcon.text = icon
            weatherInfo.text = info
            weatherBar.visibility = View.VISIBLE
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val manualCity = currentCity
                val url = if (manualCity.isNotBlank()) {
                    "https://wttr.in/${java.net.URLEncoder.encode(manualCity, "UTF-8")}?format=j1"
                } else {
                    "https://wttr.in/?format=j1"
                }

                // 用 JSON API 获取天气 emoji + 温度 + 城市名
                val request = Request.Builder()
                    .url(url)
                    .build()
                val response = weatherClient.newCall(request).execute()
                val body = response.body?.string()?.trim() ?: return@launch

                val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                val current = json.getAsJsonArray("current_condition")
                    ?.get(0)?.asJsonObject ?: return@launch

                val tempC = current.get("temp_C")?.asString ?: ""
                val desc = current.getAsJsonArray("weatherDesc")
                    ?.get(0)?.asJsonObject?.get("value")?.asString ?: ""

                // 手动设置了城市则直接用它，否则从 nearest_area 提取
                val city = if (manualCity.isNotBlank()) {
                    manualCity
                } else {
                    json.getAsJsonArray("nearest_area")
                        ?.get(0)?.asJsonObject
                        ?.getAsJsonArray("areaName")
                        ?.get(0)?.asJsonObject
                        ?.get("value")?.asString ?: ""
                }

                // 映射天气描述到 emoji
                val emoji = weatherEmoji(desc)

                val infoText = buildString {
                    append("${tempC}°C")
                    if (city.isNotEmpty()) append("  $city")
                }

                withContext(Dispatchers.Main) {
                    prefs.edit()
                        .putLong("weather_cache_time", System.currentTimeMillis())
                        .putString("weather_cache_city", currentCity)
                        .putString("weather_icon", emoji)
                        .putString("weather_info", infoText)
                        .apply()

                    weatherIcon.text = emoji
                    weatherInfo.text = infoText
                    weatherBar.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                com.example.myapplication.diagnostics.ServiceHealth.record("Weather", e.message ?: "unknown")
            }
        }
    }

    /** 简单天气描述 → emoji 映射 */
    private fun weatherEmoji(desc: String): String {
        val d = desc.lowercase()
        return when {
            "sunny" in d || "clear" in d -> "☀️"
            "cloud" in d && ("sunny" in d || "clear" in d) -> "⛅"
            "cloud" in d -> "☁️"
            "overcast" in d -> "☁️"
            "rain" in d && "light" in d -> "🌦"
            "rain" in d -> "🌧"
            "drizzle" in d -> "🌦"
            "thunder" in d -> "⛈"
            "snow" in d -> "🌨"
            "fog" in d || "mist" in d -> "🌫"
            "haze" in d -> "🌫"
            else -> "🌡"
        }
    }
}
