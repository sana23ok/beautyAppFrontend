package com.example.beautyappfrontend.ui.screens

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.data.remote.RetrofitInstance
import com.example.beautyappfrontend.data.repository.ChatRepository
import com.example.beautyappfrontend.databinding.ActivityModerationBinding
import com.example.beautyappfrontend.domain.model.Conversation
import com.example.beautyappfrontend.domain.model.ModReview
import com.example.beautyappfrontend.domain.model.ModUser
import com.example.beautyappfrontend.ui.ConversationAdapter
import com.example.beautyappfrontend.utils.SessionManager
import kotlinx.coroutines.launch

class ModerationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityModerationBinding
    private lateinit var session: SessionManager

    // Moderation panel
    private lateinit var usersAdapter: ModUsersAdapter
    private lateinit var reviewsAdapter: ModReviewsAdapter
    private var allUsers: List<ModUser> = emptyList()
    private var allReviews: List<ModReview> = emptyList()
    private var showingUsers = true

    // Chat panel
    private lateinit var conversationsAdapter: ConversationAdapter
    private val chatRepository = ChatRepository()
    private var allConversations: List<Conversation> = emptyList()

    companion object {
        private const val PANEL_MODERATION = 1
        private const val PANEL_CHAT = 2
        private const val PANEL_PROFILE = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModerationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        setupModPanel()
        setupChatPanel()
        setupProfilePanel()
        setupBottomNav()
        showPanel(PANEL_MODERATION)
    }

    override fun onResume() {
        super.onResume()
        when {
            binding.panelModeration.visibility == View.VISIBLE -> loadUsers()
            binding.panelChat.visibility == View.VISIBLE -> loadConversations()
        }
    }

    // ── Bottom navigation ──────────────────────────────────────────────────────

    private fun setupBottomNav() {
        binding.bottomNavMod.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_mod_moderation -> {
                    binding.tvHeaderTitle.text = "Moderation"
                    showPanel(PANEL_MODERATION)
                    loadUsers()
                    true
                }
                R.id.nav_mod_chat -> {
                    binding.tvHeaderTitle.text = "Messages"
                    showPanel(PANEL_CHAT)
                    loadConversations()
                    true
                }
                R.id.nav_mod_profile -> {
                    binding.tvHeaderTitle.text = "My Profile"
                    showPanel(PANEL_PROFILE)
                    true
                }
                else -> false
            }
        }
    }

    private fun showPanel(panel: Int) {
        binding.panelModeration.visibility = if (panel == PANEL_MODERATION) View.VISIBLE else View.GONE
        binding.panelChat.visibility = if (panel == PANEL_CHAT) View.VISIBLE else View.GONE
        binding.panelProfile.visibility = if (panel == PANEL_PROFILE) View.VISIBLE else View.GONE
    }

    // ── Moderation panel ───────────────────────────────────────────────────────

    private fun setupModPanel() {
        usersAdapter = ModUsersAdapter(emptyList()) { user -> confirmDeleteUser(user) }
        reviewsAdapter = ModReviewsAdapter(emptyList()) { review -> confirmDeleteReview(review) }

        binding.rvUsers.layoutManager = LinearLayoutManager(this)
        binding.rvUsers.adapter = usersAdapter
        binding.rvUsers.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        binding.rvReviews.layoutManager = LinearLayoutManager(this)
        binding.rvReviews.adapter = reviewsAdapter
        binding.rvReviews.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        binding.btnTabUsers.setOnClickListener { switchModTab(users = true) }
        binding.btnTabReviews.setOnClickListener { switchModTab(users = false) }

        binding.etSearchMod.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim() ?: ""
                if (showingUsers) filterUsers(q) else filterReviews(q)
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        loadUsers()
    }

    private fun switchModTab(users: Boolean) {
        showingUsers = users
        binding.rvUsers.visibility = if (users) View.VISIBLE else View.GONE
        binding.rvReviews.visibility = if (users) View.GONE else View.VISIBLE

        val active = ContextCompat.getColor(this, R.color.peach_dark)
        val activeText = ContextCompat.getColor(this, R.color.white)
        val inactive = ContextCompat.getColor(this, android.R.color.white)
        val inactiveText = ContextCompat.getColor(this, R.color.text_primary)

        binding.btnTabUsers.setBackgroundColor(if (users) active else inactive)
        binding.btnTabUsers.setTextColor(if (users) activeText else inactiveText)
        binding.btnTabReviews.setBackgroundColor(if (users) inactive else active)
        binding.btnTabReviews.setTextColor(if (users) inactiveText else activeText)

        val q = binding.etSearchMod.text?.toString()?.trim() ?: ""
        if (users) {
            filterUsers(q)
            if (allUsers.isEmpty()) loadUsers()
        } else {
            filterReviews(q)
            if (allReviews.isEmpty()) loadReviews()
        }
    }

    private fun loadUsers() {
        val token = session.getToken() ?: return
        lifecycleScope.launch {
            try {
                val resp = RetrofitInstance.api.getModerationUsers("Bearer $token")
                if (resp.isSuccessful) {
                    allUsers = resp.body() ?: emptyList()
                    val q = binding.etSearchMod.text?.toString()?.trim() ?: ""
                    filterUsers(q)
                }
            } catch (e: Exception) {
                Toast.makeText(this@ModerationActivity, "Failed to load users", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadReviews() {
        val token = session.getToken() ?: return
        lifecycleScope.launch {
            try {
                val resp = RetrofitInstance.api.getModerationReviews("Bearer $token")
                if (resp.isSuccessful) {
                    allReviews = resp.body() ?: emptyList()
                    val q = binding.etSearchMod.text?.toString()?.trim() ?: ""
                    filterReviews(q)
                }
            } catch (e: Exception) {
                Toast.makeText(this@ModerationActivity, "Failed to load reviews", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun filterUsers(q: String) {
        val filtered = if (q.isBlank()) allUsers
        else allUsers.filter {
            it.email.contains(q, true) ||
                it.firstName.contains(q, true) ||
                it.lastName.contains(q, true)
        }
        usersAdapter.updateData(filtered)
    }

    private fun filterReviews(q: String) {
        val filtered = if (q.isBlank()) allReviews
        else allReviews.filter {
            it.comment.contains(q, true) ||
                it.authorEmail.contains(q, true) ||
                it.authorName.contains(q, true) ||
                it.masterName.contains(q, true)
        }
        reviewsAdapter.updateData(filtered)
    }

    private fun confirmDeleteUser(user: ModUser) {
        AlertDialog.Builder(this)
            .setTitle("Delete user")
            .setMessage("Delete \"${user.displayName}\" (${user.email})? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteUser(user.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteUser(id: Int) {
        val token = session.getToken() ?: return
        lifecycleScope.launch {
            try {
                val resp = RetrofitInstance.api.deleteModerationUser("Bearer $token", id)
                if (resp.isSuccessful) {
                    Toast.makeText(this@ModerationActivity, "User deleted", Toast.LENGTH_SHORT).show()
                    allUsers = allUsers.filter { it.id != id }
                    filterUsers(binding.etSearchMod.text?.toString()?.trim() ?: "")
                } else {
                    val msg = resp.errorBody()?.string()
                    Toast.makeText(this@ModerationActivity, "Error: $msg", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ModerationActivity, "Failed to delete user", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDeleteReview(review: ModReview) {
        AlertDialog.Builder(this)
            .setTitle("Delete review")
            .setMessage("Delete review by \"${review.authorName}\" for ${review.masterName}?")
            .setPositiveButton("Delete") { _, _ -> deleteReview(review.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteReview(id: Int) {
        val token = session.getToken() ?: return
        lifecycleScope.launch {
            try {
                val resp = RetrofitInstance.api.deleteModerationReview("Bearer $token", id)
                if (resp.isSuccessful) {
                    Toast.makeText(this@ModerationActivity, "Review deleted", Toast.LENGTH_SHORT).show()
                    allReviews = allReviews.filter { it.id != id }
                    filterReviews(binding.etSearchMod.text?.toString()?.trim() ?: "")
                } else {
                    val msg = resp.errorBody()?.string()
                    Toast.makeText(this@ModerationActivity, "Error: $msg", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ModerationActivity, "Failed to delete review", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Chat panel ─────────────────────────────────────────────────────────────

    private fun setupChatPanel() {
        conversationsAdapter = ConversationAdapter(emptyList()) { conv ->
            val intent = Intent(this, ChatConversationActivity::class.java)
            intent.putExtra("conversation_id", conv.id)
            intent.putExtra("participant_name", conv.participantName)
            intent.putExtra("participant_avatar", conv.participantAvatar)
            intent.putExtra("participant_id", conv.participantId)
            startActivity(intent)
        }
        binding.rvConversations.layoutManager = LinearLayoutManager(this)
        binding.rvConversations.adapter = conversationsAdapter
        binding.rvConversations.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        binding.etSearchChat.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim() ?: ""
                val filtered = if (q.isBlank()) allConversations
                else allConversations.filter { it.participantName.contains(q, true) }
                conversationsAdapter.updateData(filtered)
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
    }

    private fun loadConversations() {
        val token = session.getToken() ?: return
        lifecycleScope.launch {
            try {
                allConversations = chatRepository.getConversations(token)
                val q = binding.etSearchChat.text?.toString()?.trim() ?: ""
                val filtered = if (q.isBlank()) allConversations
                else allConversations.filter { it.participantName.contains(q, true) }
                conversationsAdapter.updateData(filtered)
            } catch (_: Exception) {
                Toast.makeText(this@ModerationActivity, "Failed to load messages", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Profile panel ──────────────────────────────────────────────────────────

    private fun setupProfilePanel() {
        binding.tvModName.text = session.getDisplayName()
        binding.tvModEmail.text = session.getEmail()

        val avatarUrl = session.getAvatarUrl()
        if (!avatarUrl.isNullOrBlank()) {
            binding.ivModAvatar.imageTintList = null
            binding.ivModAvatar.load(avatarUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_nav_profile)
                error(R.drawable.ic_nav_profile)
                transformations(CircleCropTransformation())
            }
            binding.ivModAvatar.background = null
            binding.ivModAvatar.setPadding(0, 0, 0, 0)
        }

        binding.btnModLogout.setOnClickListener {
            session.clearSession()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    // ── Inner adapters ─────────────────────────────────────────────────────────

    inner class ModUsersAdapter(
        private var items: List<ModUser>,
        private val onDelete: (ModUser) -> Unit,
    ) : RecyclerView.Adapter<ModUsersAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val avatar: ImageView = v.findViewById(R.id.iv_avatar)
            val name: TextView = v.findViewById(R.id.tv_name)
            val email: TextView = v.findViewById(R.id.tv_email)
            val role: TextView = v.findViewById(R.id.tv_role)
            val btnDelete: ImageButton = v.findViewById(R.id.btn_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_mod_user, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val user = items[position]
            holder.name.text = user.displayName
            holder.email.text = user.email
            holder.role.text = user.roleLabel

            if (!user.avatar.isNullOrBlank()) {
                holder.avatar.imageTintList = null
                holder.avatar.load(user.avatar) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                }
                holder.avatar.background = null
                holder.avatar.setPadding(0, 0, 0, 0)
            } else {
                holder.avatar.setImageResource(R.drawable.ic_nav_profile)
                holder.avatar.setBackgroundResource(R.drawable.bg_avatar_circle)
                val p = (10 * holder.itemView.resources.displayMetrics.density).toInt()
                holder.avatar.setPadding(p, p, p, p)
                holder.avatar.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(holder.itemView.context, R.color.white)
                )
            }

            if (user.isStaff) {
                holder.btnDelete.visibility = View.GONE
            } else {
                holder.btnDelete.visibility = View.VISIBLE
                holder.btnDelete.setOnClickListener { onDelete(user) }
            }
        }

        override fun getItemCount() = items.size

        fun updateData(newItems: List<ModUser>) {
            items = newItems
            notifyDataSetChanged()
        }
    }

    inner class ModReviewsAdapter(
        private var items: List<ModReview>,
        private val onDelete: (ModReview) -> Unit,
    ) : RecyclerView.Adapter<ModReviewsAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val master: TextView = v.findViewById(R.id.tv_master)
            val rating: TextView = v.findViewById(R.id.tv_rating)
            val author: TextView = v.findViewById(R.id.tv_author)
            val comment: TextView = v.findViewById(R.id.tv_comment)
            val btnDelete: ImageButton = v.findViewById(R.id.btn_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_mod_review, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val review = items[position]
            holder.master.text = review.masterName
            holder.rating.text = "${"★".repeat(review.rating)}${"☆".repeat(5 - review.rating)}"
            holder.author.text = "by ${review.authorName}"
            holder.comment.text = review.comment.ifBlank { "(no comment)" }
            holder.btnDelete.setOnClickListener { onDelete(review) }
        }

        override fun getItemCount() = items.size

        fun updateData(newItems: List<ModReview>) {
            items = newItems
            notifyDataSetChanged()
        }
    }
}
