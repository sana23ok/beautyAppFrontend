package com.example.beautyappfrontend.domain.model

data class Conversation(
    val id: Int,
    val participantId: Int,
    val participantName: String,
    val participantAvatar: String = "",
    val lastMessage: String = "",
    val lastMessageTime: String = "",
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
)

data class ChatMessage(
    val id: Int,
    val conversationId: Int,
    val senderId: Int,
    val text: String,
    val timestamp: String,
    val isFromMe: Boolean,
)
