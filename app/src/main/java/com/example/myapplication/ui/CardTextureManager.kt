package com.example.myapplication.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 卡片纹理管理器 — 按类别为 MaterialCardView 设置图片纹理。
 *
 * 内置 3 个默认纹理，用户可添加、命名、重命名、删除自定义纹理。
 */
object CardTextureManager {

    const val NONE = "none"

    const val LIFE_RECORD = "life_record"
    const val DIARY = "diary"
    const val PLAN = "plan"
    const val MEMORY = "memory"
    const val CHAT = "chat"

    val ALL_CATEGORIES: List<String> = listOf(LIFE_RECORD, DIARY, PLAN, MEMORY, CHAT)

    fun categoryLabel(cat: String): String = when (cat) {
        LIFE_RECORD -> "生活记录"
        DIARY -> "日记"
        PLAN -> "规划"
        MEMORY -> "记忆"
        CHAT -> "对话互动"
        else -> cat
    }

    // ── 自定义纹理数据 ──
    data class CustomTexture(val id: String, var name: String, val filePath: String)

    private val gson = Gson()
    private var customTextures: MutableList<CustomTexture> = mutableListOf()
    private var storeDir: File? = null

    /** 初始化存储目录并加载自定义纹理列表 */
    fun init(context: Context) {
        storeDir = File(context.filesDir, "card_textures")
        storeDir?.mkdirs()
        loadCustomList(context)
    }

    private fun customListFile(context: Context): File =
        File(context.filesDir, "card_textures.json")

    private fun loadCustomList(context: Context) {
        val file = customListFile(context)
        if (file.exists()) {
            try {
                val type = object : TypeToken<List<CustomTexture>>() {}.type
                customTextures = gson.fromJson(file.readText(), type) ?: mutableListOf()
            } catch (_: Exception) {
                customTextures = mutableListOf()
            }
        }
    }

    private fun saveCustomList(context: Context) {
        try {
            customListFile(context).writeText(gson.toJson(customTextures), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    /** 获取所有可用纹理 key（内置 + 自定义） */
    fun allTextureKeys(context: Context): List<Pair<String, String>> {
        if (customTextures.isEmpty()) loadCustomList(context)
        val list = mutableListOf<Pair<String, String>>()
        list.add(NONE to "无纹理")
        list.add("texture_1" to "纹理 1")
        list.add("texture_2" to "纹理 2")
        list.add("texture_3" to "纹理 3")
        for (ct in customTextures) {
            list.add("custom:${ct.id}" to ct.name)
        }
        return list
    }

    /** 纹理 key → 显示名 */
    fun textureLabel(context: Context, key: String): String {
        return when (key) {
            NONE -> "无纹理"
            "texture_1" -> "纹理 1"
            "texture_2" -> "纹理 2"
            "texture_3" -> "纹理 3"
            else -> {
                if (key.startsWith("custom:")) {
                    val id = key.removePrefix("custom:")
                    customTextures.find { it.id == id }?.name ?: "自定义图片"
                } else key
            }
        }
    }

    // ── 自定义纹理操作 ──

    /** 添加自定义纹理，返回其 key */
    fun addCustom(context: Context, name: String, sourceUri: Uri): String? {
        init(context)
        val id = UUID.randomUUID().toString()
        val file = File(storeDir, "$id.jpg")
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            } ?: return null
        } catch (_: Exception) {
            return null
        }
        val ct = CustomTexture(id, name, file.absolutePath)
        customTextures.add(ct)
        saveCustomList(context)
        bitmapCache.remove("custom:$id")
        return "custom:$id"
    }

    fun renameCustom(context: Context, key: String, newName: String) {
        val id = key.removePrefix("custom:")
        customTextures.find { it.id == id }?.let { ct ->
            ct.name = newName
            saveCustomList(context)
        }
    }

    fun deleteCustom(context: Context, key: String) {
        val id = key.removePrefix("custom:")
        customTextures.removeAll { it.id == id }
        saveCustomList(context)
        bitmapCache.remove("custom:$id")
        // 删除图片文件
        storeDir?.let { dir -> File(dir, "$id.jpg").delete() }
    }

    fun getCustomName(key: String): String? {
        val id = key.removePrefix("custom:")
        return customTextures.find { it.id == id }?.name
    }

    // ── 内置纹理资源 ──
    private val BUILTIN_TEXTURES: Map<String, Int> = mapOf(
        "texture_1" to R.drawable.bg_default_1,
        "texture_2" to R.drawable.bg_default_2,
        "texture_3" to R.drawable.bg_default_3
    )

    private val bitmapCache: MutableMap<String, Bitmap?> = mutableMapOf()

    /** 按 key 加载 Bitmap（页面纹理等复用）。需要 Resources 来加载内置资源。 */
    fun loadBitmap(res: android.content.res.Resources, textureKey: String): Bitmap? {
        return when {
            textureKey in BUILTIN_TEXTURES -> {
                val resId: Int = BUILTIN_TEXTURES[textureKey]!!
                bitmapCache[textureKey] ?: BitmapFactory.decodeResource(res, resId).also {
                    bitmapCache[textureKey] = it
                }
            }
            textureKey.startsWith("custom:") -> {
                val id = textureKey.removePrefix("custom:")
                val path = customTextures.find { it.id == id }?.filePath
                if (path != null) {
                    bitmapCache[textureKey] ?: BitmapFactory.decodeFile(path).also {
                        bitmapCache[textureKey] = it
                    }
                } else null
            }
            else -> null
        }
    }

    /** 对所有卡片统一应用当前圆角设置 */
    fun applyShape(card: MaterialCardView) {
        val app = card.context.applicationContext as? MyApplication ?: return
        val dp = app.appConfig.cardCornerRadiusDp
        val px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, card.resources.displayMetrics
        )
        card.radius = px
    }

    fun apply(card: MaterialCardView, textureKey: String, fallbackColorAttr: Int) {
        applyShape(card)

        if (textureKey == NONE) {
            remove(card, fallbackColorAttr)
            return
        }

        val bitmap = loadBitmap(card.resources, textureKey)
        if (bitmap == null) {
            remove(card, fallbackColorAttr)
            return
        }

        // 从主题色 + 用户配置的透明度计算蒙版色
        val app = card.context.applicationContext as? MyApplication
        val opacity = app?.appConfig?.cardOpacity ?: 30
        val overlay = overlayColor(card, fallbackColorAttr, opacity)

        // MaterialCardView 不允许外部 setBackground()，改为插入普通子 View 承载纹理。
        // 普通 View 不受 MaterialCardView 限制，setBackground() 可以正常生效。
        val bgView = ensureBgView(card)
        bgView.background = CenterCropDrawable(bitmap, overlay)

        // 卡片底色用蒙版色（非透明），避免 contentPadding 区域露出页面背景
        card.setCardBackgroundColor(ColorStateList.valueOf(overlay))
    }

    /** 根据透明度百分比计算覆盖色：取 fallback 主题色的 alpha 缩放版本 */
    private fun overlayColor(card: MaterialCardView, attrRes: Int, opacity: Int): Int {
        if (opacity <= 0) return Color.TRANSPARENT
        val tv = TypedValue()
        card.context.theme.resolveAttribute(attrRes, tv, true)
        val base = tv.data
        if (opacity >= 100) return base or (0xFF shl 24)  // 确保完全不透明
        val alpha = (255 * opacity / 100)
        return (alpha shl 24) or (base and 0x00FFFFFF)
    }

    /** 查找或创建卡片内的纹理背景 View（tag = "card_texture_bg"） */
    private fun ensureBgView(card: MaterialCardView): View {
        for (i in 0 until card.childCount) {
            val child = card.getChildAt(i)
            if ("card_texture_bg" == child.tag) return child
        }
        val bg = View(card.context).apply {
            tag = "card_texture_bg"
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        card.addView(bg, 0)  // 插入到最底层
        return bg
    }

    /** 移除纹理背景子 View 并恢复卡片颜色 */
    private fun removeBgView(card: MaterialCardView) {
        for (i in 0 until card.childCount) {
            val child = card.getChildAt(i)
            if ("card_texture_bg" == child.tag) {
                card.removeViewAt(i)
                break
            }
        }
    }

    fun remove(card: MaterialCardView, fallbackColorAttr: Int) {
        removeBgView(card)
        card.setCardBackgroundColor(restoreColor(card, fallbackColorAttr))
    }

    private fun restoreColor(card: MaterialCardView, attrRes: Int): ColorStateList {
        val tv = TypedValue()
        card.context.theme.resolveAttribute(attrRes, tv, true)
        return ColorStateList.valueOf(tv.data)
    }

    // ── CenterCropDrawable ──

    class CenterCropDrawable(val bitmap: Bitmap, overlayColor: Int = 0) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        private val srcRect = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
        private val dstRect = RectF()
        private val overlayPaint: Paint? = if (overlayColor != 0) {
            Paint().apply { color = overlayColor }
        } else null

        override fun draw(canvas: Canvas) {
            val w: Float = bounds.width().toFloat()
            val h: Float = bounds.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val bmpW: Float = bitmap.width.toFloat()
            val bmpH: Float = bitmap.height.toFloat()
            val scale: Float = maxOf(w / bmpW, h / bmpH)
            val dw: Float = bmpW * scale
            val dh: Float = bmpH * scale
            dstRect.set((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
            canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
            overlayPaint?.let { canvas.drawRect(dstRect, it) }
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            overlayPaint?.alpha = alpha
        }
        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            overlayPaint?.colorFilter = colorFilter
        }
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
