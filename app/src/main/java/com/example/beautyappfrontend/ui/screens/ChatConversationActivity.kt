package com.example.beautyappfrontend.ui.screens

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.transform.CircleCropTransformation
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.databinding.ActivityChatConversationBinding
import com.example.beautyappfrontend.domain.model.ChatMessage
import com.example.beautyappfrontend.ui.MessageAdapter

class ChatConversationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatConversationBinding
    private lateinit var adapter: MessageAdapter

    private var conversationId: Int = 0
    private var participantName: String = ""
    private var participantAvatar: String = ""
    private var isOnline: Boolean = false

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_PARTICIPANT_NAME = "participant_name"
        const val EXTRA_PARTICIPANT_AVATAR = "participant_avatar"
        const val EXTRA_IS_ONLINE = "is_online"

        private fun getSampleMessages(conversationId: Int): List<ChatMessage> {
            return when (conversationId) {
                1 -> listOf(
                    ChatMessage(1, 1, 0, "Hello! I would like to book an appointment for tomorrow.", "10:15", true),
                    ChatMessage(2, 1, 1, "Hi! Of course, what time works best for you?", "10:18", false),
                    ChatMessage(3, 1, 0, "Is 3pm available?", "10:20", true),
                    ChatMessage(4, 1, 1, "Sure! I can fit you in at 3pm tomorrow.", "10:30", false),
                )
                2 -> listOf(
                    ChatMessage(1, 2, 0, "I'd like to get a haircut and color.", "Yesterday", true),
                    ChatMessage(2, 2, 2, "Great! I have availability next week. Would Tuesday work?", "Yesterday", false),
                    ChatMessage(3, 2, 0, "Tuesday at 2pm would be perfect!", "Yesterday", true),
                    ChatMessage(4, 2, 2, "Thank you for booking!", "Yesterday", false),
                )
                3 -> listOf(
                    ChatMessage(1, 3, 0, "Can I reschedule my appointment to next Friday?", "Mon", true),
                    ChatMessage(2, 3, 3, "Of course! I'll move you to Friday at 11am.", "Mon", false),
                    ChatMessage(3, 3, 0, "Perfect, thank you!", "Mon", true),
                    ChatMessage(4, 3, 3, "Your appointment is confirmed.", "Mon", false),
                )
                4 -> listOf(
                    ChatMessage(1, 4, 0, "Looking forward to my appointment!", "Sun", true),
                    ChatMessage(2, 4, 4, "See you on Friday!", "Sun", false),
                )
                else -> emptyList()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        extractIntentData()
        setupHeader()
        setupRecyclerView()
        setupInputBar()
        setupBottomNav()

        loadMessages()
    }

    private fun extractIntentData() {
        conversationId = intent.getIntExtra(EXTRA_CONVERSATION_ID, 0)
        participantName = intent.getStringExtra(EXTRA_PARTICIPANT_NAME) ?: "Unknown"
        participantAvatar = intent.getStringExtra(EXTRA_PARTICIPANT_AVATAR) ?: ""
        isOnline = intent.getBooleanExtra(EXTRA_IS_ONLINE, false)
    }

    private fun setupHeader() {
        binding.tvName.text = participantName
        binding.tvStatus.text = if (isOnline) "Online" else "Offline"
        binding.tvStatus.setTextColor(
            getColor(if (isOnline) R.color.green_primary else R.color.guava_sage)
        )

        if (participantAvatar.isNotBlank()) {
            binding.ivAvatar.load(participantAvatar) {
                crossfade(true)
                placeholder(R.drawable.ic_nav_profile)
                error(R.drawable.ic_nav_profile)
                transformations(CircleCropTransformation())
            }
            binding.ivAvatar.background = null
            binding.ivAvatar.setPadding(0, 0, 0, 0)
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnMore.setOnClickListener {
            Toast.makeText(this, "More options", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(emptyList())
        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvMessages.layoutManager = layoutManager
        binding.rvMessages.adapter = adapter
    }

    private fun setupInputBar() {
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                binding.etMessage.text.clear()
            }
        }

        binding.btnAttach.setOnClickListener {
            Toast.makeText(this, "Attach file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadMessages() {
        val messages = getSampleMessages(conversationId)
        adapter.updateData(messages)
        if (messages.isNotEmpty()) {
            binding.rvMessages.scrollToPosition(messages.size - 1)
        }
    }

    private fun sendMessage(text: String) {
        val newMessage = ChatMessage(
            id = System.currentTimeMillis().toInt(),
            conversationId = conversationId,
            senderId = 0,
            text = text,
            timestamp = "Now",
            isFromMe = true,
        )
        adapter.addMessage(newMessage)
        binding.rvMessages.scrollToPosition(adapter.itemCount - 1)
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_chat
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { navigateTo(HomeActivity::class.java); true }
                R.id.nav_search -> { navigateTo(SearchPageActivity::class.java); true }
                R.id.nav_chat -> { navigateTo(ChatActivity::class.java); true }
                R.id.nav_profile -> { navigateTo(ProfileActivity::class.java); true }
                else -> false
            }
        }
    }

    private fun <T> navigateTo(cls: Class<T>) {
        val intent = Intent(this, cls)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val options = ActivityOptions.makeCustomAnimation(this, 0, 0)
        startActivity(intent, options.toBundle())
    }
}
