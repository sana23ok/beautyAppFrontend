package com.example.beautyappfrontend.ui.screens

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.data.repository.ChatRepository
import com.example.beautyappfrontend.databinding.ActivityChatBinding
import com.example.beautyappfrontend.domain.model.Conversation
import com.example.beautyappfrontend.ui.ConversationAdapter
import com.example.beautyappfrontend.utils.ChatBadgeHelper
import com.example.beautyappfrontend.utils.SessionManager
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: ConversationAdapter
    private lateinit var session: SessionManager
    private val chatRepository = ChatRepository()
    private var allConversations: List<Conversation> = emptyList()

    companion object {
        private const val TAG = "ChatActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        setupRecyclerView()
        setupSearch()
        setupProfileIcon()
        setupBottomNav()
    }

    override fun onResume() {
        super.onResume()
        loadConversations()
        ChatBadgeHelper.updateBadge(binding.bottomNav, session.getToken(), lifecycleScope)
    }

    private fun setupRecyclerView() {
        adapter = ConversationAdapter(emptyList()) { conversation ->
            openConversation(conversation)
        }
        binding.rvConversations.layoutManager = LinearLayoutManager(this)
        binding.rvConversations.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterConversations(s?.toString() ?: "")
            }
        })
    }

    private fun filterConversations(query: String) {
        val filtered = if (query.isBlank()) {
            allConversations
        } else {
            allConversations.filter {
                it.participantName.contains(query, ignoreCase = true) ||
                        it.lastMessage.contains(query, ignoreCase = true)
            }
        }
        adapter.updateData(filtered)
        updateEmptyState(filtered.isEmpty())
    }

    private fun loadConversations() {
        val token = session.getToken()
        if (token.isNullOrBlank()) {
            Log.w(TAG, "No auth token, showing empty state")
            updateEmptyState(true)
            return
        }

        lifecycleScope.launch {
            try {
                val conversations = chatRepository.getConversations(token)
                applyConversations(conversations)
                Log.d(TAG, "Loaded ${conversations.size} conversations")
            } catch (e: Exception) {
                handleLoadError(e)
            }
        }
    }

    private fun applyConversations(conversations: List<Conversation>) {
        allConversations = conversations
        adapter.updateData(conversations)
        updateEmptyState(conversations.isEmpty())
    }

    private fun handleLoadError(e: Exception) {
        Log.e(TAG, "Error loading conversations", e)
        if (e.message?.contains("401") == true) {
            Toast.makeText(
                this,
                "Session expired. Please sign out and sign in again.",
                Toast.LENGTH_LONG,
            ).show()
        }
        applyConversations(emptyList())
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.rvConversations.isVisible = !isEmpty
        binding.layoutEmpty.isVisible = isEmpty

        if (isEmpty) {
            binding.btnFindSpecialists.setOnClickListener {
                navigateTo(SearchPageActivity::class.java)
            }
        }
    }

    private fun openConversation(conversation: Conversation) {
        val intent = Intent(this, ChatConversationActivity::class.java).apply {
            putExtra(ChatConversationActivity.EXTRA_CONVERSATION_ID, conversation.id)
            putExtra(ChatConversationActivity.EXTRA_PARTICIPANT_ID, conversation.participantId)
            putExtra(ChatConversationActivity.EXTRA_PARTICIPANT_NAME, conversation.participantName)
            putExtra(ChatConversationActivity.EXTRA_PARTICIPANT_AVATAR, conversation.participantAvatar)
            putExtra(ChatConversationActivity.EXTRA_IS_ONLINE, conversation.isOnline)
            putExtra(ChatConversationActivity.EXTRA_PARTICIPANT_IS_STAFF, conversation.participantIsStaff)
        }
        startActivity(intent)
    }

    private fun setupProfileIcon() {
        binding.ivProfileIcon.setOnClickListener {
            navigateTo(ProfileActivity::class.java)
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_chat
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { navigateTo(HomeActivity::class.java); true }
                R.id.nav_search -> { navigateTo(SearchPageActivity::class.java); true }
                R.id.nav_chat -> true
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
