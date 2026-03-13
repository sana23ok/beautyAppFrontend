package com.example.beautyappfrontend.ui.screens

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.databinding.ActivityChatBinding
import com.example.beautyappfrontend.domain.model.Conversation
import com.example.beautyappfrontend.ui.ConversationAdapter

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: ConversationAdapter
    private var allConversations: List<Conversation> = emptyList()

    companion object {
        private val SAMPLE_CONVERSATIONS = listOf(
            Conversation(
                id = 1,
                participantId = 1,
                participantName = "Anna Kovalenko",
                lastMessage = "Sure! I can fit you in at 3pm tomorrow.",
                lastMessageTime = "10:30",
                unreadCount = 2,
                isOnline = true,
            ),
            Conversation(
                id = 2,
                participantId = 2,
                participantName = "Maria Petrenko",
                lastMessage = "Thank you for booking!",
                lastMessageTime = "Yesterday",
                unreadCount = 0,
                isOnline = false,
            ),
            Conversation(
                id = 3,
                participantId = 3,
                participantName = "Olena Sydorenko",
                lastMessage = "Your appointment is confirmed.",
                lastMessageTime = "Mon",
                unreadCount = 0,
                isOnline = true,
            ),
            Conversation(
                id = 4,
                participantId = 4,
                participantName = "Iryna Marchenko",
                lastMessage = "See you on Friday!",
                lastMessageTime = "Sun",
                unreadCount = 1,
                isOnline = false,
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSearch()
        setupProfileIcon()
        setupBottomNav()

        loadConversations()
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
        allConversations = SAMPLE_CONVERSATIONS
        adapter.updateData(allConversations)
        updateEmptyState(allConversations.isEmpty())
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.rvConversations.isVisible = !isEmpty
        binding.layoutEmpty.isVisible = isEmpty
    }

    private fun openConversation(conversation: Conversation) {
        val intent = Intent(this, ChatConversationActivity::class.java).apply {
            putExtra(ChatConversationActivity.EXTRA_CONVERSATION_ID, conversation.id)
            putExtra(ChatConversationActivity.EXTRA_PARTICIPANT_NAME, conversation.participantName)
            putExtra(ChatConversationActivity.EXTRA_PARTICIPANT_AVATAR, conversation.participantAvatar)
            putExtra(ChatConversationActivity.EXTRA_IS_ONLINE, conversation.isOnline)
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
