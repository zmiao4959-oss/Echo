package com.example.myapplication.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.memory.Session
import java.text.SimpleDateFormat
import java.util.*

class ConversationAdapter(
    private val onClick: (String) -> Unit
) : ListAdapter<Session, ConversationAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val previewText: TextView = view.findViewById(R.id.text_preview)
        val timeText: TextView = view.findViewById(R.id.text_time)
        val countText: TextView = view.findViewById(R.id.text_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = getItem(position)
        val context = holder.itemView.context

        // 预览文本：最后一条用户消息
        val lastUserMsg = session.messages.findLast { it.role == "user" }
        holder.previewText.text = lastUserMsg?.content?.take(80)?.replace("\n", " ") ?: "(空对话)"

        // 时间
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        holder.timeText.text = sdf.format(Date(session.lastActive))

        // 消息数
        val count = session.messages.size
        holder.countText.text = if (count > 0) "$count 条消息" else ""

        holder.itemView.setOnClickListener {
            onClick(session.chatId)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Session>() {
        override fun areItemsTheSame(oldItem: Session, newItem: Session): Boolean {
            return oldItem.sessionId == newItem.sessionId
        }

        override fun areContentsTheSame(oldItem: Session, newItem: Session): Boolean {
            return oldItem.lastActive == newItem.lastActive
                    && oldItem.messages.size == newItem.messages.size
        }
    }
}
