package com.example.myapplication.ui.memory

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.model.UserProfileMemory
import com.google.android.material.switchmaterial.SwitchMaterial

class ProfileMemoryAdapter(
    private val onToggle: (UserProfileMemory) -> Unit
) : ListAdapter<UserProfileMemory, ProfileMemoryAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val view: android.view.View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile_memory, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val profile = getItem(position)

        val iconView = holder.view.findViewById<android.widget.TextView>(R.id.tv_category_icon)
        iconView.text = categoryEmoji(profile.category)

        val valueView = holder.view.findViewById<android.widget.TextView>(R.id.tv_value)
        valueView.text = profile.value

        val keyView = holder.view.findViewById<android.widget.TextView>(R.id.tv_key)
        keyView.text = "${categoryLabel(profile.category)} · ${profile.key}"

        val switch = holder.view.findViewById<SwitchMaterial>(R.id.switch_profile)
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = profile.enabled
        switch.setOnCheckedChangeListener { _, _ -> onToggle(profile) }
    }

    private fun categoryEmoji(cat: String): String = when (cat) {
        "preference" -> "💡"
        "habit" -> "🔄"
        "goal" -> "🎯"
        "identity" -> "🧑"
        "project" -> "📋"
        "relationship" -> "👥"
        else -> "📌"
    }

    private fun categoryLabel(cat: String): String = when (cat) {
        "preference" -> "偏好"
        "habit" -> "习惯"
        "goal" -> "目标"
        "identity" -> "身份"
        "project" -> "项目"
        "relationship" -> "关系"
        else -> cat
    }

    class DiffCallback : DiffUtil.ItemCallback<UserProfileMemory>() {
        override fun areItemsTheSame(oldItem: UserProfileMemory, newItem: UserProfileMemory): Boolean =
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: UserProfileMemory, newItem: UserProfileMemory): Boolean =
            oldItem == newItem
    }
}
