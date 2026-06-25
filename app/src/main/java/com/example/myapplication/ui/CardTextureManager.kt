package com.example.myapplication.ui

import android.app.Activity
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
import com.example.myapplication.R
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.io.FileOutputStream

/**
 * 卡片纹理管理器 — 按类别为 MaterialCardView 设置图片纹理。
 *
 * 用自定义 CenterCropDrawable 设为 background，MaterialCardView 自动裁剪圆角。
 * 无 intrinsic size → 不影响卡片尺寸；center‑crop 缩放 → 图片不扭曲。
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

    private val DEFAULT_TEXTURES: Map<String, Int> = mapOf(
        "texture_1" to R.drawable.bg_default_1,
        "texture_2" to R.drawable.bg_default_2,
        "texture_3" to R.drawable.bg_default_3
    )

    fun textureLabel(key: String): String = when (key) {
        NONE -> "无纹理"
        "texture_1" -> "纹理 1"
        "texture_2" -> "纹理 2"
        "texture_3" -> "纹理 3"
        else -> if (key.startsWith("custom:")) "自定义图片" else key
    }

    private val bitmapCache: MutableMap<String, Bitmap?> = mutableMapOf()

    fun apply(card: MaterialCardView, textureKey: String, fallbackColorAttr: Int) {
        if (textureKey == NONE) {
            remove(card, fallbackColorAttr)
            return
        }

        val bitmap: Bitmap? = when {
            textureKey in DEFAULT_TEXTURES -> {
                val resId: Int = DEFAULT_TEXTURES[textureKey]!!
                bitmapCache[textureKey] ?: BitmapFactory.decodeResource(card.resources, resId).also {
                    bitmapCache[textureKey] = it
                }
            }
            textureKey.startsWith("custom:") -> {
                val path = textureKey.removePrefix("custom:")
                bitmapCache[textureKey] ?: BitmapFactory.decodeFile(path).also {
                    bitmapCache[textureKey] = it
                }
            }
            else -> null
        }

        if (bitmap == null) {
            remove(card, fallbackColorAttr)
            return
        }

        // CenterCropDrawable：无 intrinsic size（不撑卡片） + 等比缩放填满（不扭曲）
        card.background = CenterCropDrawable(bitmap)
        card.setCardBackgroundColor(ColorStateList.valueOf(Color.TRANSPARENT))
    }

    fun remove(card: MaterialCardView, fallbackColorAttr: Int) {
        card.background = null
        card.setCardBackgroundColor(restoreColor(card, fallbackColorAttr))
    }

    private fun restoreColor(card: MaterialCardView, attrRes: Int): ColorStateList {
        val tv = TypedValue()
        card.context.theme.resolveAttribute(attrRes, tv, true)
        return ColorStateList.valueOf(tv.data)
    }

    fun saveCustomImage(activity: Activity, sourceUri: Uri): String? {
        return try {
            val inputStream = activity.contentResolver.openInputStream(sourceUri) ?: return null
            val file = File(activity.filesDir, "card_texture_custom.jpg")
            FileOutputStream(file).use { output -> inputStream.copyTo(output) }
            inputStream.close()
            val key = "custom:${file.absolutePath}"
            bitmapCache.remove(key)
            key
        } catch (_: Exception) {
            null
        }
    }

    // ── 内部：center‑crop drawable ──

    private class CenterCropDrawable(private val bitmap: Bitmap) : Drawable() {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        private val srcRect = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
        private val dstRect = RectF()

        override fun draw(canvas: Canvas) {
            val viewW: Float = bounds.width().toFloat()
            val viewH: Float = bounds.height().toFloat()
            if (viewW <= 0f || viewH <= 0f) return

            val bmpW: Float = bitmap.width.toFloat()
            val bmpH: Float = bitmap.height.toFloat()
            val scale: Float = maxOf(viewW / bmpW, viewH / bmpH)
            val displayW: Float = bmpW * scale
            val displayH: Float = bmpH * scale
            dstRect.set(
                (viewW - displayW) / 2f,
                (viewH - displayH) / 2f,
                (viewW + displayW) / 2f,
                (viewH + displayH) / 2f
            )
            canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
