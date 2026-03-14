package com.example.beautyappfrontend.data.repository

import com.example.beautyappfrontend.data.remote.RetrofitInstance
import com.example.beautyappfrontend.domain.model.ChatMessage
import com.example.beautyappfrontend.domain.model.Conversation
import com.example.beautyappfrontend.domain.model.ConversationDetailResponse
import com.example.beautyappfrontend.domain.model.SendMessageRequest
import com.example.beautyappfrontend.domain.model.StartConversationRequest

class ChatRepository {

    suspend fun getUnreadTotal(token: String): Int {
        val response = RetrofitInstance.api.getUnreadTotal("Bearer $token")
        if (response.isSuccessful) {
            return response.body()?.unreadTotal ?: 0
        }
        return 0
    }

    suspend fun markMessagesRead(token: String, conversationId: Int) {
        RetrofitInstance.api.markMessagesRead("Bearer $token", conversationId)
    }

    suspend fun getConversations(token: String): List<Conversation> {
        val response = RetrofitInstance.api.getConversations("Bearer $token")
        if (response.isSuccessful) {
            return response.body()?.map { Conversation.from(it) } ?: emptyList()
        }
        throw Exception("Failed to fetch conversations: ${response.code()}")
    }

    suspend fun startConversation(
        token: String,
        participantId: Int,
        initialMessage: String = "",
    ): ConversationDetailResponse {
        val request = StartConversationRequest(
            participantId = participantId,
            message = initialMessage,
        )
        val response = RetrofitInstance.api.startConversation("Bearer $token", request)
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Empty response")
        }
        throw Exception("Failed to start conversation: ${response.code()}")
    }

    suspend fun getConversation(token: String, conversationId: Int): ConversationDetailResponse {
        val response = RetrofitInstance.api.getConversation("Bearer $token", conversationId)
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Empty response")
        }
        throw Exception("Failed to fetch conversation: ${response.code()}")
    }

    suspend fun getMessages(token: String, conversationId: Int): List<ChatMessage> {
        val response = RetrofitInstance.api.getMessages("Bearer $token", conversationId)
        if (response.isSuccessful) {
            return response.body()?.map { ChatMessage.from(it) } ?: emptyList()
        }
        throw Exception("Failed to fetch messages: ${response.code()}")
    }

    suspend fun sendMessage(
        token: String,
        conversationId: Int,
        text: String,
    ): ChatMessage {
        val request = SendMessageRequest(text = text)
        val response = RetrofitInstance.api.sendMessage("Bearer $token", conversationId, request)
        if (response.isSuccessful) {
            return response.body()?.let { ChatMessage.from(it) }
                ?: throw Exception("Empty response")
        }
        throw Exception("Failed to send message: ${response.code()}")
    }
}
