package com.example.myapplication.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.data.store.DataExporter
import com.example.myapplication.data.store.EchoFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileActivity : ThemedActivity() {

    // 当前正在编辑纹理的类别（添加自定义纹理时用）
    private var pendingTextureCategory: String? = null
    private var pendingIsPageTexture: Boolean = false

    private val pickTextureImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val cat = pendingTextureCategory ?: return@registerForActivityResult
        pendingTextureCategory = null
        if (uri != null) {
            showNameDialog(cat, uri, pendingIsPageTexture)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        CardTextureManager.init(this)

        val app = application as MyApplication
        val config = app.appConfig

        // 显示当前主题名
        val tvThemeCurrent = findViewById<TextView>(R.id.tv_theme_current)
        tvThemeCurrent.text = ThemeManager.themeNames[config.themeKey] ?: getString(R.string.theme_warm_tea)

        // 显示当前字体名
        val tvFontCurrent = findViewById<TextView>(R.id.tv_font_current)
        tvFontCurrent.text = FontManager.fontNames[config.fontKey] ?: getString(R.string.font_default)

        findViewById<View>(R.id.entry_theme).setOnClickListener { showThemePickerDialog() }
        findViewById<View>(R.id.entry_page_texture).setOnClickListener { showPageCategoryPicker() }
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

    // ── 页面纹理选择 ──

    private fun showPageCategoryPicker() {
        val config = (application as MyApplication).appConfig
        val categories = PageTextureManager.ALL_CATEGORIES
        val labels = categories.map {
            val key = config.getPageTextureKey(it)
            "${PageTextureManager.categoryLabel(it)}  →  ${CardTextureManager.textureLabel(this, key)}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择页面")
            .setItems(labels) { _, which ->
                showPageTexturePicker(categories[which])
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPageTexturePicker(category: String) {
        val config = (application as MyApplication).appConfig
        val allTextures = CardTextureManager.allTextureKeys(this)
        val currentKey = config.getPageTextureKey(category)
        val currentIndex = allTextures.indexOfFirst { it.first == currentKey }.coerceAtLeast(0)
        val labels = allTextures.map { it.second }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("${PageTextureManager.categoryLabel(category)} — 纹理")
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val (newKey, _) = allTextures[which]
                if (newKey != currentKey) {
                    if (newKey == CardTextureManager.NONE || newKey.startsWith("texture_")) {
                        config.setPageTextureKey(category, newKey)
                        dialog.dismiss()
                        ThemeManager.pendingChange = true
                        finish()
                        startActivity(Intent(this@ProfileActivity, ProfileActivity::class.java))
                    } else {
                        dialog.dismiss()
                        showPageCustomActions(category, newKey)
                    }
                }
            }
            .setNeutralButton("＋ 添加纹理") { dialog, _ ->
                dialog.dismiss()
                pendingTextureCategory = category
                pendingIsPageTexture = true
                pickTextureImage.launch("image/*")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPageCustomActions(category: String, textureKey: String) {
        val config = (application as MyApplication).appConfig
        val name = CardTextureManager.textureLabel(this, textureKey)

        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(arrayOf("使用此纹理", "重命名", "删除")) { _, which ->
                when (which) {
                    0 -> {
                        config.setPageTextureKey(category, textureKey)
                        ThemeManager.pendingChange = true
                        finish()
                        startActivity(Intent(this@ProfileActivity, ProfileActivity::class.java))
                    }
                    1 -> showPageRenameDialog(category, textureKey, name)
                    2 -> showPageDeleteDialog(category, textureKey, name)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPageRenameDialog(category: String, textureKey: String, oldName: String) {
        val input = EditText(this).apply {
            setText(oldName)
            setSingleLine(true)
            setSelection(oldName.length)
        }
        AlertDialog.Builder(this)
            .setTitle("重命名纹理")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val newName = input.text.toString().trim().ifEmpty { return@setPositiveButton }
                CardTextureManager.renameCustom(this, textureKey, newName)
                showPageTexturePicker(category)
            }
            .setNegativeButton("取消") { _, _ -> showPageTexturePicker(category) }
            .show()
    }

    private fun showPageDeleteDialog(category: String, textureKey: String, name: String) {
        AlertDialog.Builder(this)
            .setTitle("删除「$name」")
            .setMessage("确定要删除这个自定义纹理吗？")
            .setPositiveButton("删除") { _, _ ->
                CardTextureManager.deleteCustom(this, textureKey)
                val config = (application as MyApplication).appConfig
                if (config.getPageTextureKey(category) == textureKey) {
                    config.setPageTextureKey(category, CardTextureManager.NONE)
                    ThemeManager.pendingChange = true
                    finish()
                    startActivity(Intent(this@ProfileActivity, ProfileActivity::class.java))
                } else {
                    showPageTexturePicker(category)
                }
            }
            .setNegativeButton("取消") { _, _ -> showPageTexturePicker(category) }
            .show()
    }

    // ── 卡片纹理选择 ──

    private fun showCardCategoryPicker() {
        val config = (application as MyApplication).appConfig
        val categories = CardTextureManager.ALL_CATEGORIES
        val labels = categories.map {
            val key = config.getCardTextureKey(it)
            "${CardTextureManager.categoryLabel(it)}  →  ${CardTextureManager.textureLabel(this, key)}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择卡片类型")
            .setItems(labels) { _, which ->
                showCardTexturePicker(categories[which])
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCardTexturePicker(category: String) {
        val config = (application as MyApplication).appConfig
        val allTextures = CardTextureManager.allTextureKeys(this)
        val currentKey = config.getCardTextureKey(category)
        val currentIndex = allTextures.indexOfFirst { it.first == currentKey }.coerceAtLeast(0)
        val labels = allTextures.map { it.second }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("${CardTextureManager.categoryLabel(category)} — 纹理")
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val (newKey, _) = allTextures[which]
                if (newKey != currentKey) {
                    // 自定义纹理：长按管理，短按选择
                    if (newKey == CardTextureManager.NONE || newKey.startsWith("texture_")) {
                        // 内置纹理 — 直接选择
                        config.setCardTextureKey(category, newKey)
                        dialog.dismiss()
                        ThemeManager.pendingChange = true
                        finish()
                        startActivity(Intent(this@ProfileActivity, ProfileActivity::class.java))
                    } else {
                        // 自定义纹理 — 弹操作选项
                        dialog.dismiss()
                        showCustomTextureActions(category, newKey)
                    }
                }
            }
            .setNeutralButton("＋ 添加纹理") { dialog, _ ->
                dialog.dismiss()
                pendingTextureCategory = category
                pendingIsPageTexture = false
                pickTextureImage.launch("image/*")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 自定义纹理的操作：使用 / 重命名 / 删除 */
    private fun showCustomTextureActions(category: String, textureKey: String) {
        val config = (application as MyApplication).appConfig
        val name = CardTextureManager.textureLabel(this, textureKey)

        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(arrayOf("使用此纹理", "重命名", "删除")) { _, which ->
                when (which) {
                    0 -> {
                        config.setCardTextureKey(category, textureKey)
                        ThemeManager.pendingChange = true
                        finish()
                        startActivity(Intent(this@ProfileActivity, ProfileActivity::class.java))
                    }
                    1 -> showRenameDialog(category, textureKey, name)
                    2 -> showDeleteDialog(category, textureKey, name)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 添加自定义纹理 — 输入名称 */
    private fun showNameDialog(category: String, uri: Uri, isPageTexture: Boolean) {
        val input = EditText(this).apply {
            hint = "输入纹理名称"
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("命名纹理")
            .setView(input)
            .setPositiveButton("添加") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "自定义图片" }
                val key = CardTextureManager.addCustom(this, name, uri)
                if (key != null) {
                    val config = (application as MyApplication).appConfig
                    if (isPageTexture) {
                        config.setPageTextureKey(category, key)
                    } else {
                        config.setCardTextureKey(category, key)
                    }
                    ThemeManager.pendingChange = true
                    finish()
                    startActivity(Intent(this@ProfileActivity, ProfileActivity::class.java))
                } else {
                    Toast.makeText(this, "添加失败，请重试", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRenameDialog(category: String, textureKey: String, oldName: String) {
        val input = EditText(this).apply {
            setText(oldName)
            setSingleLine(true)
            setSelection(oldName.length)
        }
        AlertDialog.Builder(this)
            .setTitle("重命名纹理")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val newName = input.text.toString().trim().ifEmpty { return@setPositiveButton }
                CardTextureManager.renameCustom(this, textureKey, newName)
                // 回到选择器
                showCardTexturePicker(category)
            }
            .setNegativeButton("取消") { _, _ -> showCardTexturePicker(category) }
            .show()
    }

    private fun showDeleteDialog(category: String, textureKey: String, name: String) {
        AlertDialog.Builder(this)
            .setTitle("删除「$name」")
            .setMessage("确定要删除这个自定义纹理吗？已使用此纹理的卡片将恢复为纯色。")
            .setPositiveButton("删除") { _, _ ->
                CardTextureManager.deleteCustom(this, textureKey)
                // 如果当前类别正在使用被删除的纹理，回退到 none
                val config = (application as MyApplication).appConfig
                if (config.getCardTextureKey(category) == textureKey) {
                    config.setCardTextureKey(category, CardTextureManager.NONE)
                    ThemeManager.pendingChange = true
                    finish()
                    startActivity(Intent(this@ProfileActivity, ProfileActivity::class.java))
                } else {
                    showCardTexturePicker(category)
                }
            }
            .setNegativeButton("取消") { _, _ -> showCardTexturePicker(category) }
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
