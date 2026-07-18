package com.example.myapplication.ui.memory

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.ui.CardTextureManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MemoryCardAdapter(
    private val onClick: ((MemoryCard, View) -> Unit)? = null,
    private val onLongClick: ((MemoryCard, View) -> Unit)? = null
) : ListAdapter<MemoryCard, MemoryCardAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val card: MaterialCardView) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_memory_card, parent, false) as MaterialCardView
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val card = getItem(position)
        val ctx = holder.card.context

        // 卡片纹理
        val app = ctx.applicationContext as MyApplication
        CardTextureManager.apply(holder.card, app.appConfig.getCardTextureKey(CardTextureManager.MEMORY), R.attr.echoSurface)

        val quoteView = holder.card.findViewById<android.widget.TextView>(R.id.tv_quote)
        quoteView.text = "「${card.quote}」"

        val noteView = holder.card.findViewById<android.widget.TextView>(R.id.tv_note)
        noteView.text = card.note
        noteView.visibility = if (card.note.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE

        val dateView = holder.card.findViewById<android.widget.TextView>(R.id.tv_date)
        dateView.text = formatDate(card.memoryDate)

        val moodView = holder.card.findViewById<android.widget.TextView>(R.id.tv_mood)
        moodView.text = card.mood ?: ""
        moodView.visibility = if (card.mood != null) android.view.View.VISIBLE else android.view.View.GONE

        val pinView = holder.card.findViewById<android.widget.ImageView>(R.id.iv_pinned)
        pinView.visibility = if (card.pinned) android.view.View.VISIBLE else android.view.View.GONE

        // 标签
        val chipGroup = holder.card.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chip_tags)
        chipGroup.removeAllViews()
        if (card.tags.isNotEmpty()) {
            chipGroup.visibility = android.view.View.VISIBLE
            for (tag in card.tags.take(3)) {
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

        holder.card.setOnLongClickListener {
            onLongClick?.invoke(card, holder.card)
            true
        }
        holder.card.setOnClickListener { onClick?.invoke(card, holder.card) }
    }

    private fun formatDate(isoDate: String): String {
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(isoDate)
            val sdf = SimpleDateFormat("M月d日", Locale.CHINESE)
            parsed?.let { sdf.format(it) } ?: isoDate
        } catch (_: Exception) {
            isoDate
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<MemoryCard>() {
        override fun areItemsTheSame(oldItem: MemoryCard, newItem: MemoryCard): Boolean =
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MemoryCard, newItem: MemoryCard): Boolean =
            oldItem == newItem
    }
}
