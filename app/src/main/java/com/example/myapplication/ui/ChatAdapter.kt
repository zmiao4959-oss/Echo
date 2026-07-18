package com.example.myapplication.ui

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R

/**
 * 聊天消息 RecyclerView Adapter。
 */
class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageText: TextView = view.findViewById(R.id.message_text)
        val toolInfo: TextView = view.findViewById(R.id.tool_info)
        val roleIndicator: TextView = view.findViewById(R.id.role_indicator)
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position).role) {
            "user" -> 0
            else -> 1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutRes = if (viewType == 0) R.layout.item_message_user else R.layout.item_message_assistant
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = getItem(position)

        // 构建显示内容
        val sb = SpannableStringBuilder()

        if (msg.role == "user") {
            holder.roleIndicator.text = "你"
            sb.append(msg.content)
        } else {
            holder.roleIndicator.text = "小爪"
            sb.append(msg.content)

            if (msg.isStreaming) {
                sb.append(" ▌") // 流式光标
            }
        }

        holder.messageText.text = sb

        // 工具调用信息
        if (msg.toolCalls.isNotEmpty() || msg.toolResults.isNotEmpty()) {
            val toolSb = StringBuilder()
            for (tc in msg.toolCalls) {
                toolSb.appendLine("🔧 $tc")
            }
            for ((name, result) in msg.toolResults) {
                toolSb.appendLine("✅ $name: ${result.take(100)}")
            }
            holder.toolInfo.text = toolSb.toString().trimEnd()
            holder.toolInfo.visibility = View.VISIBLE
        } else {
            holder.toolInfo.visibility = View.GONE
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            // ChatMessage is a data class, so its generated equals() performs
            // structural comparison for content, streaming state and tool lists.
            return oldItem == newItem
        }
    }
}
