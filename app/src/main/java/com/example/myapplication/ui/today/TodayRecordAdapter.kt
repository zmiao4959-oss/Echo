package com.example.myapplication.ui.today

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.ui.CardTextureManager
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TodayRecordAdapter(
    private val onDelete: ((String) -> Unit)? = null
) : ListAdapter<LifeRecord, TodayRecordAdapter.ViewHolder>(DiffCallback()) {

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

        // 情绪
        val moodView = holder.view.findViewById<android.widget.TextView>(R.id.tv_mood)
        moodView.text = record.mood ?: sourceEmoji(record.source)
        moodView.visibility = if (record.mood != null || record.source.isNotEmpty())
            android.view.View.VISIBLE else android.view.View.GONE

        // 内容
        val contentView = holder.view.findViewById<android.widget.TextView>(R.id.tv_content)
        contentView.text = record.content

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
