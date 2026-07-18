package com.example.myapplication.ui

import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.tts.TTSParser

/** Chat message list. TTS directives are retained in data but hidden from the UI. */
class ChatAdapter(
    private val onMessageLongClick: ((ChatMessage) -> Unit)? = null
) : ListAdapter<ChatMessage, ChatAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageText: TextView = view.findViewById(R.id.message_text)
        val toolInfo: TextView = view.findViewById(R.id.tool_info)
        val roleIndicator: TextView = view.findViewById(R.id.role_indicator)
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).role == "user") 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutRes = if (viewType == 0) {
            R.layout.item_message_user
        } else {
            R.layout.item_message_assistant
        }
        return ViewHolder(LayoutInflater.from(parent.context).inflate(layoutRes, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = getItem(position)
        val displayText = if (msg.role == "user") {
            holder.roleIndicator.setText(R.string.role_you)
            msg.content
        } else {
            holder.roleIndicator.setText(R.string.role_claw)
            TTSParser.toDisplayText(msg.content)
        }

        holder.messageText.text = SpannableStringBuilder(displayText).apply {
            if (msg.isStreaming) append(" ▌")
        }

        if (msg.toolCalls.isNotEmpty() || msg.toolResults.isNotEmpty()) {
            holder.toolInfo.text = buildString {
                msg.toolCalls.forEach { appendLine("🔧 $it") }
                msg.toolResults.forEach { (name, result) ->
                    appendLine("✓ $name: ${result.take(100)}")
                }
            }.trimEnd()
            holder.toolInfo.visibility = View.VISIBLE
        } else {
            holder.toolInfo.visibility = View.GONE
        }

        holder.itemView.setOnLongClickListener {
            if (msg.content.isBlank() || msg.isStreaming) return@setOnLongClickListener false
            onMessageLongClick?.invoke(msg)
            onMessageLongClick != null
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
            oldItem == newItem
    }
}
