package com.example.myapplication.ui.today

import android.media.MediaPlayer
import android.view.LayoutInflater
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

        // ── 左侧：时间段图标 + 时间 ──
        val cal = Calendar.getInstance().apply { timeInMillis = record.createdAt }
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        val timeIconView = holder.view.findViewById<TextView>(R.id.tv_time_icon)
        timeIconView.text = timeEmoji(hour)

        val timeView = holder.view.findViewById<TextView>(R.id.tv_time)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        timeView.text = sdf.format(Date(record.createdAt))

        // ── 右侧：内容首行 ──
        val contentView = holder.view.findViewById<TextView>(R.id.tv_content)
        // 取第一行内容
        contentView.text = record.content.trim().lines().firstOrNull() ?: ""

        // ── 语音播放按钮（仅语音便签可见） ──
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

        // ── 点击查看详情 ──
        holder.view.setOnClickListener {
            onClick?.invoke(record)
        }

        // ── 长按删除 ──
        holder.view.setOnLongClickListener {
            onDelete?.invoke(record.id)
            true
        }
    }

    // ── 时间段 → emoji 映射 ──

    private fun timeEmoji(hour: Int): String = when (hour) {
        in 5..6   -> "🌅"   // 清晨
        in 7..8   -> "🌤️"   // 早晨
        in 9..11  -> "☀️"   // 上午
        in 12..13 -> "🌞"   // 中午
        in 14..16 -> "🌤️"   // 下午
        in 17..18 -> "🌅"   // 傍晚
        in 19..21 -> "🌙"   // 晚上
        else      -> "🌃"   // 深夜 (22-4)
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
