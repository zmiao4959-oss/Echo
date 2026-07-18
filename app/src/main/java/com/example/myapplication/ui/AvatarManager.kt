package com.example.myapplication.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream

/**
 * 头像管理器 — 用户自定义头像，应用内两处同步：
 * 1. 主页右上角按钮
 * 2. 个人中心顶部圆形头像
 */
object AvatarManager {

    private const val AVATAR_FILE = "avatar_custom.jpg"

    /** 保存选取的图片，返回持久化路径 */
    fun saveCustom(avatarFile: File, sourceUri: android.net.Uri, contentResolver: android.content.ContentResolver): String? {
        return try {
            val inputStream = contentResolver.openInputStream(sourceUri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap == null) return null

            // 裁剪为正方形（取中心）
            val size = minOf(bitmap.width, bitmap.height)
            val x = (bitmap.width - size) / 2
            val y = (bitmap.height - size) / 2
            val cropped = Bitmap.createBitmap(bitmap, x, y, size, size)

            FileOutputStream(avatarFile).use { out ->
                cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            cropped.recycle()
            bitmap.recycle()
            avatarFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("AvatarManager", "Failed to save custom avatar", e)
            null
        }
    }

    /** 将圆形头像设置到 ImageView（ImageView 需为正方形） */
    fun applyToImageView(view: ImageView, path: String?) {
        if (path == null) {
            applyDefaultAvatar(view)
            view.scaleType = ImageView.ScaleType.CENTER
            return
        }
        val file = File(path)
        if (!file.exists()) {
            applyDefaultAvatar(view)
            view.scaleType = ImageView.ScaleType.CENTER
            return
        }
        val bitmap = BitmapFactory.decodeFile(path)
        if (bitmap == null) {
            applyDefaultAvatar(view)
            view.scaleType = ImageView.ScaleType.CENTER
            return
        }
        view.imageTintList = null
        view.setImageBitmap(circleCrop(bitmap))
        view.scaleType = ImageView.ScaleType.FIT_CENTER
        bitmap.recycle()
    }

    private fun applyDefaultAvatar(view: ImageView) {
        view.setImageResource(com.example.myapplication.R.drawable.ic_profile_outline)
        view.imageTintList = ColorStateList.valueOf(ThemeColors.textSecondary(view.context))
    }

    /** 将圆形头像设置到任意 View 的背景 */
    fun applyToViewBg(view: View, path: String?) {
        if (path == null) {
            view.background = null
            return
        }
        val file = File(path)
        if (!file.exists()) {
            view.background = null
            return
        }
        val bitmap = BitmapFactory.decodeFile(path)
        if (bitmap != null) {
            val d = android.graphics.drawable.BitmapDrawable(view.resources, circleCrop(bitmap))
            view.background = d
            bitmap.recycle()
        }
    }

    private fun circleCrop(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = size / 2f

        // 画圆
        canvas.drawCircle(r, r, r, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

        // 居中裁剪
        val srcX = (bitmap.width - size) / 2
        val srcY = (bitmap.height - size) / 2
        val srcRect = Rect(srcX, srcY, srcX + size, srcY + size)
        val dstRect = Rect(0, 0, size, size)
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)

        return output
    }
}
