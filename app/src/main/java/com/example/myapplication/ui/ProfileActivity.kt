package com.example.myapplication.ui

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.data.store.DataExporter
import com.example.myapplication.data.store.EchoFileStore
import com.example.myapplication.ui.widget.ThemeSurfaceDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileActivity : ThemedActivity() {

    // 当前正在编辑纹理的类别（添加自定义纹理时用）
    private var pendingTextureCategory: String? = null
    private var pendingIsPageTexture: Boolean = false
    private var pendingLifeRecordPeriod: String? = null  // 分时段纹理：指定时段
    private var pendingIsTextLayerTexture: Boolean = false  // true=文字层纹理，false=正面纹理

    private val pickAvatarImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val avatarFile = java.io.File(filesDir, "avatar_custom.jpg")
            val path = AvatarManager.saveCustom(avatarFile, uri, contentResolver)
            if (path != null) {
                (application as MyApplication).appConfig.avatarPath = path
                refreshAvatar()
            } else {
                Toast.makeText(this, "头像设置失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

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

        // 头像
        val avatarView = findViewById<android.widget.ImageView>(R.id.avatar)
        refreshAvatar(avatarView, config)
        avatarView.setOnClickListener { pickAvatarImage.launch("image/*") }

        // 显示当前主题名
        val tvThemeCurrent = findViewById<TextView>(R.id.tv_theme_current)
        val themeSpec = ThemeManager.specFor(config.themeKey)
        tvThemeCurrent.text = themeSpec.name
        findViewById<TextView>(R.id.tv_archive_theme).text =
            "当前装帧 · ${themeSpec.name}  /  ${themeSpec.description}"

        // 显示当前字体名
        val tvFontCurrent = findViewById<TextView>(R.id.tv_font_current)
        tvFontCurrent.text = FontManager.fontNames[config.fontKey] ?: getString(R.string.font_default)

        findViewById<View>(R.id.entry_theme).setOnClickListener { showThemePickerDialog() }
        findViewById<View>(R.id.entry_page_texture).setOnClickListener { showPageCategoryPicker() }
        findViewById<View>(R.id.entry_card_texture).setOnClickListener { showCardCategoryPicker() }
        findViewById<View>(R.id.entry_card_shape).setOnClickListener { showShapePickerDialog() }
        // 显示当前形状名
        val tvShapeCurrent = findViewById<TextView>(R.id.tv_card_shape_current)
        tvShapeCurrent.text = shapeLabel(config.cardCornerRadiusDp)

        findViewById<View>(R.id.entry_font).setOnClickListener { showFontPickerDialog() }

        // 卡片透明度 SeekBar
        val seekBarOpacity = findViewById<SeekBar>(R.id.seekbar_card_opacity)
        val tvOpacityValue = findViewById<TextView>(R.id.tv_card_opacity_value)
        seekBarOpacity.progress = config.cardOpacity
        tvOpacityValue.text = "${config.cardOpacity}%"
        seekBarOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                config.cardOpacity = progress
                tvOpacityValue.text = "${progress}%"
                if (fromUser) ThemeManager.pendingChange = true
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        findViewById<View>(R.id.entry_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<View>(R.id.entry_workspace).setOnClickListener {
            startActivity(Intent(this, WorkspaceFilesActivity::class.java))
        }

        findViewById<View>(R.id.entry_schedules).setOnClickListener {
            startActivity(Intent(this, ScheduleListActivity::class.java))
        }

        findViewById<View>(R.id.entry_memory_manage).setOnClickListener {
            startActivity(Intent(this, com.example.myapplication.ui.memory.MemoryManageActivity::class.java))
        }

        findViewById<View>(R.id.entry_growth_timeline).setOnClickListener {
            startActivity(Intent(this, GrowthTimelineActivity::class.java))
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

    private fun refreshAvatar(view: android.widget.ImageView? = null, cfg: com.example.myapplication.config.AppConfig? = null) {
        val config = cfg ?: (application as MyApplication).appConfig
        val iv = view ?: findViewById<android.widget.ImageView>(R.id.avatar)
        AvatarManager.applyToImageView(iv, config.avatarPath)
    }

    private fun showThemePickerDialog() {
        val config = (application as MyApplication).appConfig
        val themes = ThemeManager.themes
        val currentKey = ThemeManager.specFor(config.themeKey).key

        AlertDialog.Builder(this)
            .setTitle("Echo 主题收藏")
            .setAdapter(ThemeChoiceAdapter(themes, currentKey)) { dialog, which ->
                val newKey = themes[which].key
                if (newKey != currentKey) {
                    config.themeKey = newKey
                    config.backgroundKey = BackgroundManager.THEME_BACKGROUND
                    ThemeManager.pendingChange = true
                    dialog.dismiss()
                    finish()
                    startActivity(Intent(this@ProfileActivity, ProfileActivity::class.java))
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private inner class ThemeChoiceAdapter(
        private val items: List<ThemeManager.ThemeSpec>,
        private val selectedKey: String,
    ) : BaseAdapter() {

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): ThemeManager.ThemeSpec = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(this@ProfileActivity)
                .inflate(R.layout.item_theme_choice, parent, false)
            val spec = getItem(position)
            val preview = view.findViewById<ImageView>(R.id.theme_preview)
            val radius = 16f * resources.displayMetrics.density
            val outline = GradientDrawable().apply {
                cornerRadius = radius
                setColor(spec.previewColors.first())
            }
            preview.background = outline
            preview.clipToOutline = true
            if (spec.previewArtworkRes != null) {
                preview.setImageResource(spec.previewArtworkRes)
            } else {
                preview.setImageDrawable(
                    ThemeSurfaceDrawable(
                        motion = spec.motion,
                        primary = spec.previewColors[1],
                        accent = spec.previewColors[2],
                        density = resources.displayMetrics.density,
                    ),
                )
            }

            view.findViewById<TextView>(R.id.theme_name).text = spec.name
            view.findViewById<TextView>(R.id.theme_description).text = spec.description
            view.findViewById<TextView>(R.id.theme_kind).text =
                if (spec.kind == ThemeManager.Kind.STATIC) "静态" else "动态"
            view.findViewById<View>(R.id.theme_check).visibility =
                if (spec.key == selectedKey) View.VISIBLE else View.INVISIBLE

            val swatches = view.findViewById<LinearLayout>(R.id.theme_swatches)
            swatches.removeAllViews()
            val size = (12 * resources.displayMetrics.density).toInt()
            val gap = (5 * resources.displayMetrics.density).toInt()
            spec.previewColors.forEach { color ->
                swatches.addView(View(this@ProfileActivity).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                    }
                    layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = gap }
                })
            }
            return view
        }
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
            val label = if (it == CardTextureManager.LIFE_RECORD && config.lifeRecordUseTimeTexture) {
                "⏰ ${CardTextureManager.categoryLabel(it)}  →  分时段"
            } else {
                "${CardTextureManager.categoryLabel(it)}  →  ${CardTextureManager.textureLabel(this, key)}"
            }
            label
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择卡片类型")
            .setItems(labels) { _, which ->
                val cat = categories[which]
                if (cat == CardTextureManager.LIFE_RECORD) {
                    showLifeRecordTextureConfig()
                } else {
                    showCardTexturePicker(cat)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ── 生活记录分时段纹理 ──

    /** 生活记录纹理配置：开关 + 默认纹理 / 各时段单独设置 */
    private fun showLifeRecordTextureConfig() {
        val config = (application as MyApplication).appConfig
        val usePeriod = config.lifeRecordUseTimeTexture

        val items = mutableListOf<String>()
        // 选项 0：开关
        items.add(if (usePeriod) "✅ 分时段纹理（已启用）" else "☐ 分时段纹理（已关闭）")
        // 选项 1：默认纹理（始终可用）
        val defaultKey = config.getCardTextureKey(CardTextureManager.LIFE_RECORD)
        items.add("默认纹理  →  ${CardTextureManager.textureLabel(this, defaultKey)}")
        // 选项 2+：各时段（仅启用时显示，点击进入子菜单选正面/文字层）
        if (usePeriod) {
            for (period in CardTextureManager.TIME_PERIODS) {
                val frontKey = config.getLifeRecordPeriodTextureKey(period)
                val frontLabel = if (frontKey == CardTextureManager.NONE) "（继承默认）" else CardTextureManager.textureLabel(this, frontKey)
                val textKey = config.getLifeRecordTextLayerPeriodTextureKey(period)
                val textLabel = if (textKey == CardTextureManager.NONE) "（继承默认）" else CardTextureManager.textureLabel(this, textKey)
                items.add("${CardTextureManager.periodLabel(period)}  正面:$frontLabel  文字:$textLabel")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("生活记录 — 纹理配置")
            .setItems(items.toTypedArray()) { _, which ->
                when {
                    which == 0 -> {
                        config.lifeRecordUseTimeTexture = !usePeriod
                        ThemeManager.pendingChange = true
                        showLifeRecordTextureConfig()
                    }
                    which == 1 -> {
                        showCardTexturePicker(CardTextureManager.LIFE_RECORD)
                    }
                    usePeriod && which >= 2 -> {
                        val periodIdx = which - 2
                        showPeriodLayerPicker(CardTextureManager.TIME_PERIODS[periodIdx])
                    }
                }
            }
            .setNegativeButton("返回", null)
            .show()
    }

    /** 选择配置正面纹理还是文字层纹理 */
    private fun showPeriodLayerPicker(period: String) {
        AlertDialog.Builder(this)
            .setTitle("${CardTextureManager.periodLabel(period)}")
            .setItems(arrayOf("正面纹理（卡片）", "文字层纹理")) { _, which ->
                when (which) {
                    0 -> showPeriodTexturePicker(period)
                    1 -> showTextLayerPeriodTexturePicker(period)
                }
            }
            .setNegativeButton("返回") { _, _ -> showLifeRecordTextureConfig() }
            .show()
    }

    /** 为指定时间段选择文字层纹理 */
    private fun showTextLayerPeriodTexturePicker(period: String) {
        val config = (application as MyApplication).appConfig
        val allTextures = CardTextureManager.allTextureKeys(this)
        val currentKey = config.getLifeRecordTextLayerPeriodTextureKey(period)
        val currentIndex = allTextures.indexOfFirst { it.first == currentKey }.coerceAtLeast(0)
        val labels = allTextures.map { it.second }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("${CardTextureManager.periodLabel(period)} — 文字层纹理")
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val (newKey, _) = allTextures[which]
                if (newKey != currentKey) {
                    if (newKey == CardTextureManager.NONE || newKey.startsWith("texture_")) {
                        config.setLifeRecordTextLayerPeriodTextureKey(period, newKey)
                        dialog.dismiss()
                        ThemeManager.pendingChange = true
                        showLifeRecordTextureConfig()
                    } else {
                        dialog.dismiss()
                        showTextLayerPeriodCustomActions(period, newKey)
                    }
                }
            }
            .setNeutralButton("＋ 添加纹理") { dialog, _ ->
                dialog.dismiss()
                pendingTextureCategory = CardTextureManager.LIFE_RECORD
                pendingIsPageTexture = false
                pendingLifeRecordPeriod = period
                pendingIsTextLayerTexture = true
                pickTextureImage.launch("image/*")
            }
            .setNegativeButton("返回") { _, _ -> showLifeRecordTextureConfig() }
            .show()
    }

    /** 文字层时间段自定义纹理的操作 */
    private fun showTextLayerPeriodCustomActions(period: String, textureKey: String) {
        val name = CardTextureManager.textureLabel(this, textureKey)

        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(arrayOf("使用此纹理", "重命名", "删除")) { _, which ->
                when (which) {
                    0 -> {
                        (application as MyApplication).appConfig.setLifeRecordTextLayerPeriodTextureKey(period, textureKey)
                        ThemeManager.pendingChange = true
                        finish()
                        startActivity(Intent(this@ProfileActivity, ProfileActivity::class.java))
                    }
                    1 -> {
                        showTextLayerPeriodRenameDialog(period, textureKey, name)
                    }
                    2 -> {
                        showTextLayerPeriodDeleteDialog(period, textureKey, name)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showTextLayerPeriodRenameDialog(period: String, textureKey: String, oldName: String) {
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
                showTextLayerPeriodTexturePicker(period)
            }
            .setNegativeButton("取消") { _, _ -> showTextLayerPeriodTexturePicker(period) }
            .show()
    }

    private fun showTextLayerPeriodDeleteDialog(period: String, textureKey: String, name: String) {
        AlertDialog.Builder(this)
            .setTitle("删除「$name」")
            .setMessage("确定要删除这个自定义纹理吗？")
            .setPositiveButton("删除") { _, _ ->
                CardTextureManager.deleteCustom(this, textureKey)
                val config = (application as MyApplication).appConfig
                if (config.getLifeRecordTextLayerPeriodTextureKey(period) == textureKey) {
                    config.setLifeRecordTextLayerPeriodTextureKey(period, CardTextureManager.NONE)
                }
                ThemeManager.pendingChange = true
                finish()
                startActivity(Intent(this@ProfileActivity, ProfileActivity::class.java))
            }
            .setNegativeButton("取消") { _, _ -> showTextLayerPeriodTexturePicker(period) }
            .show()
    }

    /** 为指定时间段选择纹理 */
    private fun showPeriodTexturePicker(period: String) {
        val config = (application as MyApplication).appConfig
        val allTextures = CardTextureManager.allTextureKeys(this)
        val currentKey = config.getLifeRecordPeriodTextureKey(period)
        val currentIndex = allTextures.indexOfFirst { it.first == currentKey }.coerceAtLeast(0)
        val labels = allTextures.map { it.second }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("${CardTextureManager.periodLabel(period)} — 纹理")
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val (newKey, _) = allTextures[which]
                if (newKey != currentKey) {
                    if (newKey == CardTextureManager.NONE || newKey.startsWith("texture_")) {
                        config.setLifeRecordPeriodTextureKey(period, newKey)
                        dialog.dismiss()
                        ThemeManager.pendingChange = true
                        showLifeRecordTextureConfig()
                    } else {
                        dialog.dismiss()
                        showPeriodCustomActions(period, newKey)
                    }
                }
            }
            .setNeutralButton("＋ 添加纹理") { dialog, _ ->
                dialog.dismiss()
                pendingTextureCategory = CardTextureManager.LIFE_RECORD
                pendingIsPageTexture = false
                pendingLifeRecordPeriod = period
                pickTextureImage.launch("image/*")
            }
            .setNegativeButton("返回") { _, _ -> showLifeRecordTextureConfig() }
            .show()
    }

    /** 时间段自定义纹理的操作 */
    private fun showPeriodCustomActions(period: String, textureKey: String) {
        val name = CardTextureManager.textureLabel(this, textureKey)

        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(arrayOf("使用此纹理", "重命名", "删除")) { _, which ->
                when (which) {
                    0 -> {
                        (application as MyApplication).appConfig.setLifeRecordPeriodTextureKey(period, textureKey)
                        ThemeManager.pendingChange = true
                        finish()
                        startActivity(Intent(this@ProfileActivity, ProfileActivity::class.java))
                    }
                    1 -> {
                        showPeriodRenameDialog(period, textureKey, name)
                    }
                    2 -> {
                        showPeriodDeleteDialog(period, textureKey, name)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPeriodRenameDialog(period: String, textureKey: String, oldName: String) {
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
                showPeriodTexturePicker(period)
            }
            .setNegativeButton("取消") { _, _ -> showPeriodTexturePicker(period) }
            .show()
    }

    private fun showPeriodDeleteDialog(period: String, textureKey: String, name: String) {
        AlertDialog.Builder(this)
            .setTitle("删除「$name」")
            .setMessage("确定要删除这个自定义纹理吗？")
            .setPositiveButton("删除") { _, _ ->
                CardTextureManager.deleteCustom(this, textureKey)
                val config = (application as MyApplication).appConfig
                if (config.getLifeRecordPeriodTextureKey(period) == textureKey) {
                    config.setLifeRecordPeriodTextureKey(period, CardTextureManager.NONE)
                }
                ThemeManager.pendingChange = true
                finish()
                startActivity(Intent(this@ProfileActivity, ProfileActivity::class.java))
            }
            .setNegativeButton("取消") { _, _ -> showPeriodTexturePicker(period) }
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
        val period = pendingLifeRecordPeriod
        val isTextLayer = pendingIsTextLayerTexture
        pendingLifeRecordPeriod = null
        pendingIsTextLayerTexture = false

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
                    } else if (period != null && isTextLayer) {
                        config.setLifeRecordTextLayerPeriodTextureKey(period, key)
                    } else if (period != null) {
                        config.setLifeRecordPeriodTextureKey(period, key)
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

    // ── 卡片形状选择 ──

    /** 预设形状：name → dp 半径 */
    private val cardShapes = linkedMapOf(
        "直角" to 0f,
        "微圆角" to 8f,
        "标准圆角" to 20f,
        "大圆角" to 32f,
        "全圆" to 100f
    )

    private fun shapeLabel(dp: Float): String {
        return cardShapes.entries.find { it.value == dp }?.key ?: "${dp.toInt()}dp"
    }

    private fun showShapePickerDialog() {
        val config = (application as MyApplication).appConfig
        val names = cardShapes.keys.toTypedArray()
        val currentDp = config.cardCornerRadiusDp
        val currentIndex = cardShapes.values.indexOf(currentDp).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("选择卡片形状")
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                val newDp = cardShapes.values.elementAt(which)
                if (newDp != currentDp) {
                    config.cardCornerRadiusDp = newDp
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
