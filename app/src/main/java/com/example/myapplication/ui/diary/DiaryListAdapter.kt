package com.example.myapplication.ui.diary

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.ui.CardTextureManager
import com.example.myapplication.ui.ThemeColors
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiaryListAdapter(
    private val onClick: (DailyDiary) -> Unit
) : ListAdapter<DailyDiary, DiaryListAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val card: MaterialCardView) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_diary, parent, false) as MaterialCardView
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val diary = getItem(position)
        val ctx = holder.card.context

        // 卡片纹理
        val app = ctx.applicationContext as MyApplication
        CardTextureManager.apply(holder.card, app.appConfig.getCardTextureKey(CardTextureManager.DIARY), R.attr.echoSurface)

        // 日期
        val dateView = holder.card.findViewById<android.widget.TextView>(R.id.tv_date)
        dateView.text = formatDate(diary.date)

        // 情绪
        val moodView = holder.card.findViewById<android.widget.TextView>(R.id.tv_mood)
        moodView.text = diary.mood

        // 标题
        val titleView = holder.card.findViewById<android.widget.TextView>(R.id.tv_title)
        titleView.text = diary.title

        // 摘要
        val summaryView = holder.card.findViewById<android.widget.TextView>(R.id.tv_summary)
        summaryView.text = diary.summary.ifEmpty { diary.diaryText.take(100) }

        // 标签
        val chipGroup = holder.card.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chip_tags)
        chipGroup.removeAllViews()
        if (diary.tags.isNotEmpty()) {
            chipGroup.visibility = android.view.View.VISIBLE
            for (tag in diary.tags.take(4)) {
                val chip = Chip(ctx)
                chip.text = tag
                chip.chipStrokeWidth = 0f
                chip.chipBackgroundColor = ColorStateList.valueOf(ThemeColors.surfaceVariant(ctx))
                chip.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                chip.setTextColor(ThemeColors.textPrimary(ctx))
                chip.isCheckable = false
                chip.isClickable = false
                chipGroup.addView(chip)
            }
        } else {
            chipGroup.visibility = android.view.View.GONE
        }

        holder.card.setOnClickListener { onClick(diary) }
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

    class DiffCallback : DiffUtil.ItemCallback<DailyDiary>() {
        override fun areItemsTheSame(oldItem: DailyDiary, newItem: DailyDiary): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: DailyDiary, newItem: DailyDiary): Boolean =
            oldItem == newItem
    }
}
