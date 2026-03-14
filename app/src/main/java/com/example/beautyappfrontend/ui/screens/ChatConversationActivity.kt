package com.example.beautyappfrontend.ui.screens

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.transform.CircleCropTransformation
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.data.repository.ChatRepository
import com.example.beautyappfrontend.databinding.ActivityChatConversationBinding
import com.example.beautyappfrontend.domain.model.ChatMessage
import com.example.beautyappfrontend.ui.MessageAdapter
import com.example.beautyappfrontend.utils.SessionManager
import kotlinx.coroutines.launch

class ChatConversationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatConversationBinding
    private lateinit var adapter: MessageAdapter
    private lateinit var session: SessionManager
    private val chatRepository = ChatRepository()

    private var conversationId: Int = 0
    private var participantName: String = ""
    private var participantAvatar: String = ""
    private var isOnline: Boolean = false

    private val refreshHandler = Handler(Looper.getMainLooper())
    private var isRefreshing = false
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshMessages()
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    companion object {
        private const val TAG = "ChatConversation"
        private const val REFRESH_INTERVAL_MS = 3000L
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_PARTICIPANT_NAME = "participant_name"
        const val EXTRA_PARTICIPANT_AVATAR = "participant_avatar"
        const val EXTRA_IS_ONLINE = "is_online"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        extractIntentData()
        setupHeader()
        setupRecyclerView()
        setupInputBar()
        setupBottomNav()

        loadMessages()
    }

    override fun onResume() {
        super.onResume()
        startAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        stopAutoRefresh()
    }

    private fun startAutoRefresh() {
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS)
    }

    private fun stopAutoRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    private fun refreshMessages() {
        if (isRefreshing) return
        isRefreshing = true

        val token = session.getToken()
        if (token.isNullOrBlank() || conversationId == 0) {
            isRefreshing = false
            return
        }

        lifecycleScope.launch {
            try {
                val messages = chatRepository.getMessages(token, conversationId)
                val currentCount = adapter.itemCount
                adapter.updateData(messages)
                if (messages.size > currentCount) {
                    binding.rvMessages.scrollToPosition(messages.size - 1)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing messages", e)
            } finally {
                isRefreshing = false
            }
        }
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
        val token = session.getToken()
        if (token.isNullOrBlank() || conversationId == 0) {
            Log.w(TAG, "No token or invalid conversation ID")
            return
        }

        lifecycleScope.launch {
            try {
                val messages = chatRepository.getMessages(token, conversationId)
                adapter.updateData(messages)
                if (messages.isNotEmpty()) {
                    binding.rvMessages.scrollToPosition(messages.size - 1)
                }
                Log.d(TAG, "Loaded ${messages.size} messages")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading messages", e)
            }
        }
    }

    private fun sendMessage(text: String) {
        val token = session.getToken()
        if (token.isNullOrBlank() || conversationId == 0) {
            Toast.makeText(this, "Cannot send message", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val message = chatRepository.sendMessage(token, conversationId, text)
                adapter.addMessage(message)
                binding.rvMessages.scrollToPosition(adapter.itemCount - 1)
                Log.d(TAG, "Message sent: ${message.text}")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message", e)
                Toast.makeText(
                    this@ChatConversationActivity,
                    "Failed to send message",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
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
