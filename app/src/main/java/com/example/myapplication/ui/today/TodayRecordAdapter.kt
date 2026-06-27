package com.example.myapplication.ui.today

import android.media.MediaPlayer
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.ui.CardTextureManager
import com.google.android.material.chip.Chip
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TodayRecordAdapter(
    private val onDelete: ((String) -> Unit)? = null
) : ListAdapter<LifeRecord, TodayRecordAdapter.ViewHolder>(DiffCallback()) {

    private var mediaPlayer: MediaPlayer? = null

    class ViewHolder(val view: com.google.android.material.card.MaterialCardView) :
        RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_life_record, parent, false) as com.google.android.material.card.MaterialCardView
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = getItem(position)
        val ctx = holder.itemView.context

        // 卡片纹理
        val app = ctx.applicationContext as MyApplication
        CardTextureManager.apply(holder.view, app.appConfig.getCardTextureKey(CardTextureManager.LIFE_RECORD), R.attr.echoSurface)

        // 时间
        val timeView = holder.view.findViewById<android.widget.TextView>(R.id.tv_time)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        timeView.text = sdf.format(Date(record.createdAt))

        // 情绪 / 来源图标
        val moodView = holder.view.findViewById<android.widget.TextView>(R.id.tv_mood)
        moodView.text = record.mood ?: sourceEmoji(record.source)
        moodView.visibility = if (record.mood != null || record.source.isNotEmpty())
            android.view.View.VISIBLE else android.view.View.GONE

        // 内容
        val contentView = holder.view.findViewById<android.widget.TextView>(R.id.tv_content)
        contentView.text = record.content

        // 语音播放按钮
        val playBtn = holder.view.findViewById<ImageButton>(R.id.btn_play_audio)
        val audioPath = record.audioPath
        if (record.source == "voice" && audioPath != null && File(audioPath).exists()) {
            playBtn.visibility = android.view.View.VISIBLE
            playBtn.setImageResource(android.R.drawable.ic_media_play)
            playBtn.setOnClickListener {
                togglePlayback(audioPath, playBtn)
            }
        } else {
            playBtn.visibility = android.view.View.GONE
            playBtn.setOnClickListener(null)
        }

        // 标签
        val chipGroup = holder.view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chip_tags)
        chipGroup.removeAllViews()
        if (record.tags.isNotEmpty()) {
            chipGroup.visibility = android.view.View.VISIBLE
            for (tag in record.tags) {
                val chip = Chip(ctx)
                chip.text = tag
                chip.chipStrokeWidth = 1f
                chip.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                chip.isCheckable = false
                chip.isClickable = false
                chipGroup.addView(chip)
            }
        } else {
            chipGroup.visibility = android.view.View.GONE
        }

        // 长按删除
        holder.view.setOnLongClickListener {
            onDelete?.invoke(record.id)
            true
        }
    }

    private fun togglePlayback(audioPath: String, playBtn: ImageButton) {
        val ctx = playBtn.context

        // 如果正在播放，停止
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            playBtn.setImageResource(android.R.drawable.ic_media_play)
            return
        }

        // 释放旧的
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

    private fun sourceEmoji(source: String): String = when (source) {
        "voice" -> "🎙️"
        "chat" -> "💬"
        "checkin" -> "👋"
        else -> "✏️"
    }

    class DiffCallback : DiffUtil.ItemCallback<LifeRecord>() {
        override fun areItemsTheSame(oldItem: LifeRecord, newItem: LifeRecord): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: LifeRecord, newItem: LifeRecord): Boolean =
            oldItem == newItem
    }
}
