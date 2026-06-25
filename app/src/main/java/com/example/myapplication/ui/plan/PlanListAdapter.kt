package com.example.myapplication.ui.plan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.data.model.EchoPlan
import com.example.myapplication.ui.CardTextureManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlanListAdapter(
    private val onToggle: (EchoPlan) -> Unit,
    private val onClick: (EchoPlan) -> Unit
) : ListAdapter<EchoPlan, PlanListAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val card: MaterialCardView) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plan, parent, false) as MaterialCardView
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val plan = getItem(position)

        // 卡片纹理
        val app = holder.card.context.applicationContext as MyApplication
        CardTextureManager.apply(holder.card, app.appConfig.getCardTextureKey(CardTextureManager.PLAN), R.attr.echoSurface)

        // 类型图标
        val iconView = holder.card.findViewById<android.widget.TextView>(R.id.tv_type_icon)
        iconView.text = typeEmoji(plan.type)

        // 标题
        val titleView = holder.card.findViewById<android.widget.TextView>(R.id.tv_plan_title)
        titleView.text = plan.title

        // 时间
        val timeView = holder.card.findViewById<android.widget.TextView>(R.id.tv_time)
        timeView.text = formatTriggerTime(plan.triggerAt)

        // 重复规则
        val repeatView = holder.card.findViewById<android.widget.TextView>(R.id.tv_repeat)
        repeatView.text = plan.repeatRule ?: ""
        repeatView.visibility = if (plan.repeatRule != null)
            android.view.View.VISIBLE else android.view.View.GONE

        // 语音播报标记
        val speakView = holder.card.findViewById<android.widget.TextView>(R.id.tv_auto_speak)
        speakView.text = if (plan.autoSpeak) "🔊 语音播报" else ""
        speakView.visibility = if (plan.autoSpeak)
            android.view.View.VISIBLE else android.view.View.GONE

        // 启用开关
        val switch = holder.card.findViewById<SwitchMaterial>(R.id.switch_enabled)
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = plan.enabled
        switch.setOnCheckedChangeListener { _, _ -> onToggle(plan) }

        // 点击进入编辑
        holder.card.setOnClickListener { onClick(plan) }
    }

    private fun typeEmoji(type: String): String = when (type) {
        "task_reminder" -> "⏰"
        "companion_checkin" -> "👋"
        "memory_trigger" -> "💭"
        "auto_diary" -> "📝"
        else -> "📌"
    }

    private fun formatTriggerTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timestamp
        val todayStart = now - (now % 86_400_000)

        return if (timestamp < todayStart) {
            "已过期"
        } else if (timestamp < todayStart + 86_400_000) {
            "今天 ${sdf.format(Date(timestamp))}"
        } else {
            val dateSdf = SimpleDateFormat("M月d日 ", Locale.CHINESE)
            dateSdf.format(Date(timestamp)) + sdf.format(Date(timestamp))
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<EchoPlan>() {
        override fun areItemsTheSame(oldItem: EchoPlan, newItem: EchoPlan): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: EchoPlan, newItem: EchoPlan): Boolean =
            oldItem == newItem
    }
}
