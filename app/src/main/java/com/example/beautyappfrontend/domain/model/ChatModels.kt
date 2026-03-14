package com.example.beautyappfrontend.domain.model

import com.google.gson.annotations.SerializedName

data class ConversationParticipant(
    val id: Int,
    val username: String = "",
    @SerializedName("first_name") val firstName: String = "",
    @SerializedName("last_name") val lastName: String = "",
    val avatar: String = "",
    @SerializedName("is_online") val isOnline: Boolean = false,
    @SerializedName("display_name") val displayNameFromServer: String = "",
) {
    val displayName: String
        get() = when {
            displayNameFromServer.isNotBlank() -> displayNameFromServer
            firstName.isNotBlank() || lastName.isNotBlank() -> "$firstName $lastName".trim()
            username.isNotBlank() -> username
            else -> "User $id"
        }
}

data class ConversationResponse(
    val id: Int,
    val participant: ConversationParticipant?,
    @SerializedName("last_message") val lastMessage: String = "",
    @SerializedName("last_message_time") val lastMessageTime: String = "",
    @SerializedName("unread_count") val unreadCount: Int = 0,
    @SerializedName("updated_at") val updatedAt: String = "",
)

data class ConversationDetailResponse(
    val id: Int,
    val participant: ConversationParticipant?,
    val messages: List<MessageResponse> = emptyList(),
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = "",
)

data class MessageResponse(
    val id: Int,
    val conversation: Int,
    @SerializedName("sender_id") val senderId: Int,
    val text: String,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("is_read") val isRead: Boolean = false,
    @SerializedName("is_from_me") val isFromMe: Boolean = false,
)

data class StartConversationRequest(
    @SerializedName("participant_id") val participantId: Int,
    val message: String = "",
)

data class SendMessageRequest(
    val text: String,
)

data class UnreadTotalResponse(
    @SerializedName("unread_total") val unreadTotal: Int = 0,
)

data class MarkReadResponse(
    @SerializedName("marked_read") val markedRead: Int = 0,
)

data class Conversation(
    val id: Int,
    val participantId: Int,
    val participantName: String,
    val participantAvatar: String = "",
    val lastMessage: String = "",
    val lastMessageTime: String = "",
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
) {
    companion object {
        fun from(response: ConversationResponse): Conversation {
            return Conversation(
                id = response.id,
                participantId = response.participant?.id ?: 0,
                participantName = response.participant?.displayName ?: "Unknown",
                participantAvatar = response.participant?.avatar ?: "",
                lastMessage = response.lastMessage,
                lastMessageTime = response.lastMessageTime,
                unreadCount = response.unreadCount,
                isOnline = response.participant?.isOnline ?: false,
            )
        }
    }
}

data class ChatMessage(
    val id: Int,
    val conversationId: Int,
    val senderId: Int,
    val text: String,
    val timestamp: String,
    val isFromMe: Boolean,
) {
    companion object {
        fun from(response: MessageResponse): ChatMessage {
            val time = response.createdAt.substringAfter("T").take(5)
            return ChatMessage(
                id = response.id,
                conversationId = response.conversation,
                senderId = response.senderId,
                text = response.text,
                timestamp = time.ifBlank { "Now" },
                isFromMe = response.isFromMe,
            )
        }
    }
}
