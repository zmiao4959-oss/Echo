package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import android.widget.TextView
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.data.store.DataExporter
import com.example.myapplication.data.store.EchoFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val app = application as MyApplication
        val config = app.appConfig

        // 显示当前主题名
        val tvThemeCurrent = findViewById<TextView>(R.id.tv_theme_current)
        tvThemeCurrent.text = ThemeManager.themeNames[config.themeKey] ?: getString(R.string.theme_warm_tea)

        // 显示当前字体名
        val tvFontCurrent = findViewById<TextView>(R.id.tv_font_current)
        tvFontCurrent.text = FontManager.fontNames[config.fontKey] ?: getString(R.string.font_default)

        findViewById<View>(R.id.entry_theme).setOnClickListener { showThemePickerDialog() }
        findViewById<View>(R.id.entry_card_texture).setOnClickListener { showCardCategoryPicker() }
        findViewById<View>(R.id.entry_font).setOnClickListener { showFontPickerDialog() }

        findViewById<View>(R.id.entry_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<View>(R.id.entry_workspace).setOnClickListener {
            startActivity(Intent(this, WorkspaceFilesActivity::class.java))
        }

        findViewById<View>(R.id.entry_schedules).setOnClickListener {
            startActivity(Intent(this, ScheduleListActivity::class.java))
        }

        findViewById<View>(R.id.entry_export).setOnClickListener {
            Toast.makeText(this, "正在导出…", Toast.LENGTH_SHORT).show()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val zipFile = DataExporter.exportAll(this@ProfileActivity)
                    withContext(Dispatchers.Main) {
                        DataExporter.shareZip(this@ProfileActivity, zipFile)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ProfileActivity, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        findViewById<View>(R.id.entry_clear_data).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("清空所有数据")
                .setMessage("确定要删除所有生活记录、日记、规划和记忆数据吗？\n\n此操作不可撤销。建议先导出备份。")
                .setPositiveButton("确认清空") { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            clearAllData()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ProfileActivity, "数据已清空", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ProfileActivity, "清空失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun showThemePickerDialog() {
        val config = (application as MyApplication).appConfig
        val themes = ThemeManager.themeNames.entries.toList()
        val currentKey = config.themeKey
        val currentIndex = themes.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)
        val names = themes.map { it.value }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择主题")
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                val newKey = themes[which].key
                if (newKey != currentKey) {
                    config.themeKey = newKey
                    ThemeManager.pendingChange = true
                    dialog.dismiss()
                    // 用 finish + startActivity 替代 recreate()，确保主题立即生效
                    finish()
                    startActivity(Intent(this@ProfileActivity, ProfileActivity::class.java))
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showFontPickerDialog() {
        val config = (application as MyApplication).appConfig
        val fonts = FontManager.fontNames.entries.toList()
        val currentKey = config.fontKey
        val currentIndex = fonts.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)
        val names = fonts.map { it.value }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择字体")
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                val newKey = fonts[which].key
                if (newKey != currentKey) {
                    config.fontKey = newKey
                    ThemeManager.pendingChange = true
                    dialog.dismiss()
                    finish()
                    startActivity(Intent(this@ProfileActivity, ProfileActivity::class.java))
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ── 卡片纹理选择 ──

    private fun showCardCategoryPicker() {
        val config = (application as MyApplication).appConfig
        val categories = CardTextureManager.ALL_CATEGORIES
        val labels = categories.map {
            val key = config.getCardTextureKey(it)
            "${CardTextureManager.categoryLabel(it)}  →  ${CardTextureManager.textureLabel(key)}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择卡片类型")
            .setItems(labels) { _, which ->
                val cat = categories[which]
                showCardTexturePicker(cat)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCardTexturePicker(category: String) {
        val config = (application as MyApplication).appConfig
        val textureKeys = listOf(CardTextureManager.NONE, "texture_1", "texture_2", "texture_3")
        val names = textureKeys.map { CardTextureManager.textureLabel(it) }.toTypedArray()
        val currentKey = config.getCardTextureKey(category)
        val currentIndex = textureKeys.indexOf(currentKey).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("${CardTextureManager.categoryLabel(category)} — 纹理")
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                val newKey = textureKeys[which]
                if (newKey != currentKey) {
                    config.setCardTextureKey(category, newKey)
                    ThemeManager.pendingChange = true
                    dialog.dismiss()
                    finish()
                    startActivity(Intent(this@ProfileActivity, ProfileActivity::class.java))
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun clearAllData() {
        // 清空所有 JSON 数据文件（写入空数组）
        val files = listOf(
            EchoFileStore.lifeRecordsFile,
            EchoFileStore.dailyDiariesFile,
            EchoFileStore.plansFile,
            EchoFileStore.memoryCardsFile,
            EchoFileStore.userProfileFile
        )
        for (file in files) {
            if (file.exists()) {
                file.writeText("""{"schemaVersion":1,"items":[]}""", Charsets.UTF_8)
            }
        }
    }
}
