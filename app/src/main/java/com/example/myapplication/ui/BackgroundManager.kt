package com.example.myapplication.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import java.io.File
import java.io.FileOutputStream

/**
 * 背景管理器 — 将选定/上传的背景图片应用到 Activity 的根视图。
 */
object BackgroundManager {

    private const val CUSTOM_FILE = "bg_custom.jpg"
    private const val TAG = "BackgroundManager"

    /** 默认背景资源 ID 映射 */
    val DEFAULT_BG_IDS = mapOf(
        "bg_default_1" to com.example.myapplication.R.drawable.bg_default_1,
        "bg_default_2" to com.example.myapplication.R.drawable.bg_default_2,
        "bg_default_3" to com.example.myapplication.R.drawable.bg_default_3
    )

    /** 将背景应用到 Activity */
    fun apply(activity: Activity, backgroundKey: String) {
        val root = activity.window.decorView.findViewById<View>(android.R.id.content)
        // 如果是某个 layout 的子 view，找到实际根布局
        val bgView = (root as? android.view.ViewGroup)?.getChildAt(0) ?: root

        when {
            backgroundKey.startsWith("custom:") -> {
                val path = backgroundKey.removePrefix("custom:")
                val file = File(path)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(path)
                    if (bitmap != null) {
                        bgView.background = android.graphics.drawable.BitmapDrawable(activity.resources, bitmap)
                    }
                }
            }
            backgroundKey in DEFAULT_BG_IDS -> {
                val resId = DEFAULT_BG_IDS[backgroundKey]!!
                bgView.background = androidx.core.content.ContextCompat.getDrawable(activity, resId)
            }
        }
    }

    /** 保存自定义图片到内部存储，返回持久化路径 */
    fun saveCustomImage(activity: Activity, sourceUri: android.net.Uri): String? {
        return try {
            val inputStream = activity.contentResolver.openInputStream(sourceUri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap == null) return null

            val file = File(activity.filesDir, CUSTOM_FILE)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save custom image", e)
            null
        }
    }
}
