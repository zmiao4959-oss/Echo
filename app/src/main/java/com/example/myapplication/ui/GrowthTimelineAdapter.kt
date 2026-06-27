package com.example.myapplication.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for the growth timeline RecyclerView.
 * Each item displays type emoji + label + summary + time.
 */
class GrowthTimelineAdapter(
    private val onClick: (TimelineItem) -> Unit
) : ListAdapter<TimelineItem, GrowthTimelineAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val typeLabel: TextView = view.findViewById(R.id.timeline_type_label)
        val summary: TextView = view.findViewById(R.id.timeline_summary)
        val timeText: TextView = view.findViewById(R.id.timeline_time)
        val moodText: TextView = view.findViewById(R.id.timeline_mood)
        val pinnedDot: View = view.findViewById(R.id.timeline_pinned_dot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_growth_timeline, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val ctx = holder.itemView.context

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

        holder.itemView.setOnClickListener { onClick(item) }
    }

    class DiffCallback : DiffUtil.ItemCallback<TimelineItem>() {
        override fun areItemsTheSame(oldItem: TimelineItem, newItem: TimelineItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: TimelineItem, newItem: TimelineItem): Boolean =
            oldItem.timestamp == newItem.timestamp && oldItem.summaryText() == newItem.summaryText()
    }
}
