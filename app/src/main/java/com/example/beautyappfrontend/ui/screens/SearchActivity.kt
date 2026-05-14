package com.example.beautyappfrontend.ui.screens

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.data.remote.RetrofitInstance
import com.example.beautyappfrontend.data.repository.ChatRepository
import com.example.beautyappfrontend.data.repository.FavoriteMastersRepository
import com.example.beautyappfrontend.data.repository.SpecialistRepository
import com.example.beautyappfrontend.domain.model.Specialist
import com.example.beautyappfrontend.ui.MainViewModel
import com.example.beautyappfrontend.ui.MainViewModelFactory
import com.example.beautyappfrontend.ui.SpecialistAdapter
import com.example.beautyappfrontend.utils.SessionManager
import kotlinx.coroutines.launch

/**
 * Legacy / demo search screen (same list + VIEW / MESSAGE behaviour as [SearchPageActivity]).
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: SpecialistAdapter
    private lateinit var session: SessionManager
    private val chatRepository = ChatRepository()

    companion object {
        private const val TAG = "SearchActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        session = SessionManager(this)

        val recyclerView = findViewById<RecyclerView>(R.id.rvItems)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = SpecialistAdapter(
            specialists = emptyList(),
            favoriteCheck = { FavoriteMastersRepository.isFavorite(it) },
            onFavoriteClick = { s ->
                if (!session.isLoggedIn()) {
                    Toast.makeText(this, "Please log in to save favorites", Toast.LENGTH_SHORT).show()
                } else {
                    lifecycleScope.launch {
                        FavoriteMastersRepository.toggle(s.id)
                            .onSuccess { adapter.notifyDataSetChanged() }
                            .onFailure { e ->
                                Toast.makeText(
                                    this@SearchActivity,
                                    e.message ?: "Could not update favorites",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                    }
                }
            },
            onViewClick = { openMasterProfile(it) },
            onMessageClick = { startConversationWith(it) },
        )
        recyclerView.adapter = adapter

        val repository = SpecialistRepository(RetrofitInstance.api)
        val factory = MainViewModelFactory(repository)

        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        viewModel.specialists.observe(this) { list ->
            adapter.updateData(list)
        }
    }

    override fun onResume() {
        super.onResume()
        if (session.isLoggedIn()) {
            lifecycleScope.launch {
                FavoriteMastersRepository.sync()
                adapter.notifyDataSetChanged()
            }
        } else {
            FavoriteMastersRepository.clearCache()
            adapter.notifyDataSetChanged()
        }
    }

    private fun openMasterProfile(specialist: Specialist) {
        if (specialist.id <= 0) {
            Toast.makeText(this, "Invalid master profile", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(this, MasterDetailActivity::class.java).apply {
                putExtra(MasterDetailActivity.EXTRA_MASTER_ID, specialist.id)
            },
        )
    }

    private fun startConversationWith(specialist: Specialist) {
        val token = session.getToken()
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Please log in to send messages", Toast.LENGTH_SHORT).show()
            return
        }

        val participantUserId = specialist.userId
        if (participantUserId == null) {
            Toast.makeText(this, "Cannot message this specialist", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Specialist ${specialist.name} has no user_id")
            return
        }

        lifecycleScope.launch {
            try {
                Toast.makeText(this@SearchActivity, "Starting conversation...", Toast.LENGTH_SHORT).show()

                val response = chatRepository.startConversation(
                    token = token,
                    participantId = participantUserId,
                )

                val intent = Intent(this@SearchActivity, ChatConversationActivity::class.java).apply {
                    putExtra(ChatConversationActivity.EXTRA_CONVERSATION_ID, response.id)
                    putExtra(ChatConversationActivity.EXTRA_PARTICIPANT_ID, response.participant?.id ?: participantUserId)
                    putExtra(
                        ChatConversationActivity.EXTRA_PARTICIPANT_NAME,
                        response.participant?.displayName ?: specialist.name,
                    )
                    putExtra(
                        ChatConversationActivity.EXTRA_PARTICIPANT_AVATAR,
                        response.participant?.avatar ?: specialist.imageUrl,
                    )
                    putExtra(
                        ChatConversationActivity.EXTRA_IS_ONLINE,
                        response.participant?.isOnline ?: false,
                    )
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting conversation", e)
                Toast.makeText(
                    this@SearchActivity,
                    "Failed to start conversation: ${e.message}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}
