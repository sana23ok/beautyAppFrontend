package com.example.beautyappfrontend.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.domain.model.Conversation

class ConversationAdapter(
    private var conversations: List<Conversation>,
    private val onClick: (Conversation) -> Unit,
) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.iv_avatar)
        val name: TextView = view.findViewById(R.id.tv_name)
        val lastMessage: TextView = view.findViewById(R.id.tv_last_message)
        val time: TextView = view.findViewById(R.id.tv_time)
        val unreadCount: TextView = view.findViewById(R.id.tv_unread_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = conversations[position]

        holder.name.text = conversation.participantName
        holder.lastMessage.text = conversation.lastMessage.ifBlank { "No messages yet" }
        holder.time.text = conversation.lastMessageTime

        if (conversation.participantAvatar.isNotBlank()) {
            holder.avatar.imageTintList = null
            holder.avatar.load(conversation.participantAvatar) {
                crossfade(true)
                placeholder(R.drawable.ic_nav_profile)
                error(R.drawable.ic_nav_profile)
                transformations(CircleCropTransformation())
            }
            holder.avatar.background = null
            holder.avatar.setPadding(0, 0, 0, 0)
        } else {
            holder.avatar.setImageResource(R.drawable.ic_nav_profile)
            holder.avatar.setBackgroundResource(R.drawable.bg_avatar_circle)
            val padding = (10 * holder.itemView.resources.displayMetrics.density).toInt()
            holder.avatar.setPadding(padding, padding, padding, padding)
            holder.avatar.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(holder.itemView.context, R.color.white),
            )
        }

        if (conversation.unreadCount > 0) {
            holder.unreadCount.isVisible = true
            holder.unreadCount.text = if (conversation.unreadCount > 9) "9+" else conversation.unreadCount.toString()
        } else {
            holder.unreadCount.isVisible = false
        }

        holder.itemView.setOnClickListener { onClick(conversation) }
    }

    override fun getItemCount() = conversations.size

    fun updateData(newConversations: List<Conversation>) {
        conversations = newConversations
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        notifyDataSetChanged()
    }
}
