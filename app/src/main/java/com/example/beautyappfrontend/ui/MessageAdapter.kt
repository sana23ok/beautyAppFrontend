package com.example.beautyappfrontend.ui

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.load
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

    private fun bindBubble(
        mediaContainer: FrameLayout,
        ivMedia: ImageView,
        ivPlay: ImageView,
        tvMessage: TextView,
        tvTime: TextView,
        message: ChatMessage,
    ) {
        tvTime.text = message.timestamp
        val caption = message.text.trim()
        if (message.hasMedia) {
            mediaContainer.isVisible = true
            ivPlay.isVisible = message.isVideo

            mediaContainer.setOnClickListener {
                runCatching {
                    it.context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(message.mediaUrl)),
                    )
                }
            }

            if (message.isVideo) {
                ivMedia.setImageDrawable(null)
                ivMedia.setBackgroundResource(R.drawable.bg_chat_media_placeholder)
            } else {
                ivMedia.background = null
                ivMedia.load(message.mediaUrl) {
                    crossfade(true)
                    placeholder(R.drawable.bg_chat_media_placeholder)
                    error(R.drawable.bg_chat_media_placeholder)
                }
            }

            tvMessage.isVisible = caption.isNotBlank()
            tvMessage.text = caption
        } else {
            mediaContainer.isVisible = false
            ivPlay.isVisible = false
            ivMedia.setImageDrawable(null)
            ivMedia.background = null
            mediaContainer.setOnClickListener(null)
            tvMessage.isVisible = true
            tvMessage.text = message.text
        }
    }

    inner class SentMessageViewHolder(private val root: View) : MessageViewHolder(root) {
        private val tvMessage: TextView = root.findViewById(R.id.tv_message)
        private val tvTime: TextView = root.findViewById(R.id.tv_time)
        private val mediaContainer: FrameLayout = root.findViewById(R.id.media_container)
        private val ivMedia: ImageView = root.findViewById(R.id.iv_media)
        private val ivPlay: ImageView = root.findViewById(R.id.iv_play)

        override fun bind(message: ChatMessage) {
            bindBubble(mediaContainer, ivMedia, ivPlay, tvMessage, tvTime, message)
        }
    }

    inner class ReceivedMessageViewHolder(private val root: View) : MessageViewHolder(root) {
        private val tvMessage: TextView = root.findViewById(R.id.tv_message)
        private val tvTime: TextView = root.findViewById(R.id.tv_time)
        private val mediaContainer: FrameLayout = root.findViewById(R.id.media_container)
        private val ivMedia: ImageView = root.findViewById(R.id.iv_media)
        private val ivPlay: ImageView = root.findViewById(R.id.iv_play)

        override fun bind(message: ChatMessage) {
            bindBubble(mediaContainer, ivMedia, ivPlay, tvMessage, tvTime, message)
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (messages[position].isFromMe) TYPE_SENT else TYPE_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutId = if (viewType == TYPE_SENT) {
            R.layout.item_message_sent
        } else {
            R.layout.item_message_received
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return if (viewType == TYPE_SENT) SentMessageViewHolder(view)
        else ReceivedMessageViewHolder(view)
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
