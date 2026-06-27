package com.example.myapplication.ui

import android.media.MediaPlayer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for the growth timeline RecyclerView.
 * Each item displays type emoji + label + summary + time.
 * Voice records show a play button.
 */
class GrowthTimelineAdapter(
    private val onClick: (TimelineItem) -> Unit
) : ListAdapter<TimelineItem, GrowthTimelineAdapter.ViewHolder>(DiffCallback()) {

    private var mediaPlayer: MediaPlayer? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val typeLabel: TextView = view.findViewById(R.id.timeline_type_label)
        val summary: TextView = view.findViewById(R.id.timeline_summary)
        val timeText: TextView = view.findViewById(R.id.timeline_time)
        val moodText: TextView = view.findViewById(R.id.timeline_mood)
        val pinnedDot: View = view.findViewById(R.id.timeline_pinned_dot)
        val playBtn: ImageButton = view.findViewById(R.id.timeline_play_audio)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_growth_timeline, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        // Type label with emoji
        holder.typeLabel.text = "${item.iconEmoji} ${item.typeLabel}"

        // Summary content
        holder.summary.text = item.summaryText()

        // Time
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        holder.timeText.text = sdf.format(Date(item.timestamp))

        // Mood (if applicable)
        val mood = item.moodText()
        holder.moodText.visibility = if (mood != null) {
            holder.moodText.text = mood
            View.VISIBLE
        } else View.GONE

        // Pinned dot
        holder.pinnedDot.visibility = if (item.isPinned) View.VISIBLE else View.GONE

        // Voice play button
        if (item is TimelineItem.LifeRecordItem && item.source == "voice" && item.audioPath != null && File(item.audioPath).exists()) {
            holder.playBtn.visibility = View.VISIBLE
            holder.playBtn.setImageResource(android.R.drawable.ic_media_play)
            holder.playBtn.setOnClickListener {
                togglePlayback(item.audioPath, holder.playBtn)
            }
        } else {
            holder.playBtn.visibility = View.GONE
            holder.playBtn.setOnClickListener(null)
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }

    private fun togglePlayback(audioPath: String, playBtn: ImageButton) {
        // Stop if already playing
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
                    this@GrowthTimelineAdapter.mediaPlayer = null
                }
            }
            playBtn.setImageResource(android.R.drawable.ic_media_pause)
        } catch (e: Exception) {
            Toast.makeText(playBtn.context, "无法播放语音", Toast.LENGTH_SHORT).show()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        mediaPlayer?.release()
        mediaPlayer = null
    }

    class DiffCallback : DiffUtil.ItemCallback<TimelineItem>() {
        override fun areItemsTheSame(oldItem: TimelineItem, newItem: TimelineItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: TimelineItem, newItem: TimelineItem): Boolean =
            oldItem.timestamp == newItem.timestamp && oldItem.summaryText() == newItem.summaryText()
    }
}
