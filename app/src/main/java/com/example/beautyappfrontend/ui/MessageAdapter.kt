package com.example.beautyappfrontend.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.domain.model.ChatMessage

class MessageAdapter(
    private var messages: List<ChatMessage>,
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    companion object {
        private const val TYPE_SENT = 0
        private const val TYPE_RECEIVED = 1
    }

    abstract class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind(message: ChatMessage)
    }

    class SentMessageViewHolder(view: View) : MessageViewHolder(view) {
        private val tvMessage: TextView = view.findViewById(R.id.tv_message)
        private val tvTime: TextView = view.findViewById(R.id.tv_time)

        override fun bind(message: ChatMessage) {
            tvMessage.text = message.text
            tvTime.text = message.timestamp
        }
    }

    class ReceivedMessageViewHolder(view: View) : MessageViewHolder(view) {
        private val tvMessage: TextView = view.findViewById(R.id.tv_message)
        private val tvTime: TextView = view.findViewById(R.id.tv_time)

        override fun bind(message: ChatMessage) {
            tvMessage.text = message.text
            tvTime.text = message.timestamp
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isFromMe) TYPE_SENT else TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutId = if (viewType == TYPE_SENT) {
            R.layout.item_message_sent
        } else {
            R.layout.item_message_received
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return if (viewType == TYPE_SENT) SentMessageViewHolder(view) else ReceivedMessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    fun updateData(newMessages: List<ChatMessage>) {
        messages = newMessages
        notifyDataSetChanged()
    }

    fun addMessage(message: ChatMessage) {
        messages = messages + message
        notifyItemInserted(messages.size - 1)
    }
}
