package com.example.myapplication.ui.today

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.media.MediaPlayer
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.ui.CardTextureManager
import com.example.myapplication.ui.ThemeColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TodayRecordAdapter(
    private val onClick: ((LifeRecord) -> Unit)? = null,
    private val onDelete: ((String) -> Unit)? = null
) : ListAdapter<LifeRecord, TodayRecordAdapter.ViewHolder>(DiffCallback()) {

    private var mediaPlayer: MediaPlayer? = null

    // 记录哪些卡片已展开（显示文字层）
    private val revealedIds = mutableSetOf<String>()

    // 滑动阈值
    private var swipeThreshold = 60f

    class ViewHolder(val view: com.google.android.material.card.MaterialCardView) :
        RecyclerView.ViewHolder(view) {
        var isAnimating = false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_life_record, parent, false) as com.google.android.material.card.MaterialCardView

        swipeThreshold = 60f * parent.context.resources.displayMetrics.density

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = getItem(position)
        val ctx = holder.itemView.context
        val card = holder.view

        val textureLayer = card.findViewById<View>(R.id.texture_layer)
        val textLayer = card.findViewById<View>(R.id.text_layer)

        // ── 重置 / 恢复状态 ──
        holder.isAnimating = false

        val wasRevealed = revealedIds.contains(record.id)
        if (wasRevealed) {
            val cw = card.width.toFloat()
            if (cw > 0f) {
                textureLayer.translationX = -cw
            } else {
                textureLayer.translationX = -1000f
                textureLayer.post { textureLayer.translationX = -card.width.toFloat() }
            }
            textLayer.visibility = View.VISIBLE
            textLayer.alpha = 1f
        } else {
            textureLayer.translationX = 0f
            textureLayer.alpha = 1f
            textLayer.alpha = 0f
            textLayer.visibility = View.INVISIBLE
        }

        // ── 时间段 ──
        val cal = Calendar.getInstance().apply { timeInMillis = record.createdAt }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val period = CardTextureManager.periodForHour(hour)

        // 卡片纹理：支持分时段
        val app = ctx.applicationContext as MyApplication
        val config = app.appConfig
        val textureKey = if (config.lifeRecordUseTimeTexture) {
            val periodKey = config.getLifeRecordPeriodTextureKey(period)
            if (periodKey != CardTextureManager.NONE) periodKey
            else config.getCardTextureKey(CardTextureManager.LIFE_RECORD)
        } else {
            config.getCardTextureKey(CardTextureManager.LIFE_RECORD)
        }
        CardTextureManager.apply(holder.view, textureKey, R.attr.echoSurface)

        // ── 文字层：独立的分时段纹理（未设置时 fallback 到卡片纹理） ──
        val textLayerKey = if (config.lifeRecordUseTimeTexture) {
            val periodTextKey = config.getLifeRecordTextLayerPeriodTextureKey(period)
            if (periodTextKey != CardTextureManager.NONE) periodTextKey
            else textureKey  // fallback 到卡片纹理
        } else {
            textureKey
        }
        val surfaceColor = ThemeColors.surface(ctx)
        CardTextureManager.applyTextureToView(textLayer, textLayerKey, surfaceColor, config.cardOpacity)

        // ── 文字层：绑定完整内容 ──
        val timeIconView = textLayer.findViewById<TextView>(R.id.tv_time_icon)
        timeIconView.text = timeEmoji(hour)

        val timeView = textLayer.findViewById<TextView>(R.id.tv_time)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        timeView.text = sdf.format(Date(record.createdAt))

        val contentView = textLayer.findViewById<TextView>(R.id.tv_content)
        contentView.text = record.content.trim().lines().firstOrNull() ?: ""

        val playBtn = textLayer.findViewById<ImageButton>(R.id.btn_play_audio)
        val audioPath = record.audioPath
        if (record.source == "voice" && audioPath != null && File(audioPath).exists()) {
            playBtn.visibility = View.VISIBLE
            playBtn.setImageResource(android.R.drawable.ic_media_play)
            playBtn.setOnClickListener { togglePlayback(audioPath, playBtn) }
        } else {
            playBtn.visibility = View.GONE
            playBtn.setOnClickListener(null)
        }

        // ── 滑动手势 ──
        card.setOnTouchListener(SwipeListener(card, textureLayer, textLayer, record.id, holder))

        // ── 点击 → 查看详情（仅文字层可见时生效） ──
        card.setOnClickListener {
            if (revealedIds.contains(record.id)) {
                onClick?.invoke(record)
            }
        }
    }

    // ── 滑动监听器 ──

    private inner class SwipeListener(
        private val card: View,
        private val textureLayer: View,
        private val textLayer: View,
        private val recordId: String,
        private val holder: ViewHolder
    ) : View.OnTouchListener {

        private var startX = 0f
        private var startY = 0f
        private var isTracking = false
        private var isHorizontal = false
        private val cardWidth: Int get() = card.width

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (holder.isAnimating) return false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    isTracking = true
                    isHorizontal = false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!isTracking) return false
                    val dx = event.x - startX
                    val dy = event.y - startY

                    if (!isHorizontal && (Math.abs(dx) > 10f || Math.abs(dy) > 10f)) {
                        isHorizontal = Math.abs(dx) > Math.abs(dy)
                    }

                    if (isHorizontal) {
                        val isRevealed = revealedIds.contains(recordId)
                        if (isRevealed) {
                            val newTx = (-cardWidth + dx).coerceIn(-cardWidth.toFloat(), 0f)
                            textureLayer.translationX = newTx
                            textLayer.alpha = 1f - Math.abs(newTx) / cardWidth
                        } else {
                            val newTx = dx.coerceIn(-cardWidth.toFloat(), 0f)
                            textureLayer.translationX = newTx
                            textLayer.alpha = Math.abs(newTx) / cardWidth
                            if (textLayer.visibility != View.VISIBLE && Math.abs(newTx) > 0f) {
                                textLayer.visibility = View.VISIBLE
                            }
                        }
                        // 横向滑动时阻止 RecyclerView 纵向滚动和长按
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isTracking = false
                    if (!isHorizontal) {
                        isHorizontal = false
                        return false
                    }
                    isHorizontal = false
                    v.parent?.requestDisallowInterceptTouchEvent(false)

                    val dx = event.x - startX
                    val isRevealed = revealedIds.contains(recordId)

                    if (isRevealed) {
                        if (dx > swipeThreshold) {
                            animateToTexture(card, textureLayer, textLayer, recordId, holder)
                        } else {
                            animateToText(card, textureLayer, textLayer, recordId, holder)
                        }
                    } else {
                        if (dx < -swipeThreshold) {
                            animateToText(card, textureLayer, textLayer, recordId, holder)
                        } else {
                            animateToTexture(card, textureLayer, textLayer, recordId, holder)
                        }
                    }
                    return true
                }
            }
            return false
        }
    }

    // ── 动画 ──

    private fun animateToText(card: View, textureLayer: View, textLayer: View, recordId: String, holder: ViewHolder) {
        val cardWidth = card.width.toFloat()
        if (cardWidth <= 0f) return
        holder.isAnimating = true

        textureLayer.animate()
            .translationX(-cardWidth)
            .setDuration(200)
            .setListener(null)

        textLayer.visibility = View.VISIBLE
        textLayer.animate()
            .alpha(1f)
            .setDuration(200)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    holder.isAnimating = false
                }
            })

        revealedIds.add(recordId)
    }

    private fun animateToTexture(card: View, textureLayer: View, textLayer: View, recordId: String, holder: ViewHolder) {
        holder.isAnimating = true

        textureLayer.animate()
            .translationX(0f)
            .setDuration(200)
            .setListener(null)

        textLayer.animate()
            .alpha(0f)
            .setDuration(200)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    textLayer.visibility = View.INVISIBLE
                    holder.isAnimating = false
                }
            })

        revealedIds.remove(recordId)
    }

    // ── 时间段 → emoji 映射 ──

    private fun timeEmoji(hour: Int): String = when (hour) {
        in 5..6   -> "🌅"
        in 7..8   -> "🌤️"
        in 9..11  -> "☀️"
        in 12..13 -> "🌞"
        in 14..16 -> "🌤️"
        in 17..18 -> "🌅"
        in 19..21 -> "🌙"
        else      -> "🌃"
    }

    // ── 语音播放 ──

    private fun togglePlayback(audioPath: String, playBtn: ImageButton) {
        val ctx = playBtn.context

        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            playBtn.setImageResource(android.R.drawable.ic_media_play)
            return
        }

        mediaPlayer?.release()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioPath)
                prepare()
                start()
                setOnCompletionListener {
                    playBtn.setImageResource(android.R.drawable.ic_media_play)
                    release()
                    this@TodayRecordAdapter.mediaPlayer = null
                }
            }
            playBtn.setImageResource(android.R.drawable.ic_media_pause)
        } catch (e: Exception) {
            Toast.makeText(ctx, "无法播放语音", Toast.LENGTH_SHORT).show()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        mediaPlayer?.release()
        mediaPlayer = null
    }

    class DiffCallback : DiffUtil.ItemCallback<LifeRecord>() {
        override fun areItemsTheSame(oldItem: LifeRecord, newItem: LifeRecord): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: LifeRecord, newItem: LifeRecord): Boolean =
            oldItem == newItem
    }
}
