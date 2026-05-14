package com.example.beautyappfrontend.ui.screens

import android.app.ActivityOptions
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.transform.CircleCropTransformation
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.data.repository.ChatRepository
import com.example.beautyappfrontend.databinding.ActivityChatConversationBinding
import com.example.beautyappfrontend.domain.model.ChatMessage
import com.example.beautyappfrontend.domain.model.ProfileReportRequest
import com.example.beautyappfrontend.ui.MessageAdapter
import com.example.beautyappfrontend.utils.ChatBadgeHelper
import com.example.beautyappfrontend.utils.SessionManager
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class ChatConversationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatConversationBinding
    private lateinit var adapter: MessageAdapter
    private lateinit var session: SessionManager
    private val chatRepository = ChatRepository()

    private var conversationId: Int = 0
    private var participantId: Int = 0
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

    private val pickChatMediaLauncher = registerForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) uploadAndSendMedia(uri)
    }

    companion object {
        private const val TAG = "ChatConversation"
        private const val REFRESH_INTERVAL_MS = 6000L
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_PARTICIPANT_NAME = "participant_name"
        const val EXTRA_PARTICIPANT_AVATAR = "participant_avatar"
        const val EXTRA_PARTICIPANT_ID = "participant_id"
        const val EXTRA_IS_ONLINE = "is_online"
        private const val MENU_REPORT_PROFILE = 1
        private const val MENU_DELETE_SELF = 2
        private const val MENU_DELETE_BOTH = 3
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

        markConversationAsRead()
        loadMessages()
    }

    override fun onResume() {
        super.onResume()
        startAutoRefresh()
        ChatBadgeHelper.updateBadge(binding.bottomNav, session.getToken(), lifecycleScope)
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
        if (!canRefresh()) return
        val token = session.getToken() ?: run { isRefreshing = false; return }
        isRefreshing = true

        lifecycleScope.launch {
            try {
                val messages = chatRepository.getMessages(token, conversationId)
                applyRefreshedMessages(messages)
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing messages", e)
            } finally {
                isRefreshing = false
            }
        }
    }

    private fun canRefresh(): Boolean {
        if (isRefreshing) return false
        val token = session.getToken()
        return !token.isNullOrBlank() && conversationId != 0
    }

    private fun applyRefreshedMessages(messages: List<ChatMessage>) {
        val currentCount = adapter.itemCount
        adapter.updateData(messages)
        if (messages.size > currentCount) {
            binding.rvMessages.scrollToPosition(messages.size - 1)
        }
    }

    private fun extractIntentData() {
        conversationId = intent.getIntExtra(EXTRA_CONVERSATION_ID, 0)
        participantId = intent.getIntExtra(EXTRA_PARTICIPANT_ID, 0)
        participantName = intent.getStringExtra(EXTRA_PARTICIPANT_NAME) ?: "Unknown"
        participantAvatar = intent.getStringExtra(EXTRA_PARTICIPANT_AVATAR) ?: ""
        isOnline = intent.getBooleanExtra(EXTRA_IS_ONLINE, false)
    }

    private fun setupHeader() {
        renderHeaderText()
        renderHeaderAvatar()
        setupHeaderButtons()
    }

    private fun renderHeaderText() {
        binding.tvName.text = participantName
        binding.tvStatus.text = if (isOnline) "Online" else "Offline"
        binding.tvStatus.setTextColor(
            getColor(if (isOnline) R.color.green_primary else R.color.guava_sage)
        )
    }

    private fun renderHeaderAvatar() {
        if (participantAvatar.isNotBlank()) {
            binding.ivAvatar.imageTintList = null
            binding.ivAvatar.load(participantAvatar) {
                crossfade(true)
                placeholder(R.drawable.ic_nav_profile)
                error(R.drawable.ic_nav_profile)
                transformations(CircleCropTransformation())
            }
            binding.ivAvatar.background = null
            binding.ivAvatar.setPadding(0, 0, 0, 0)
        } else {
            binding.ivAvatar.setImageResource(R.drawable.ic_nav_profile)
            binding.ivAvatar.setBackgroundResource(R.drawable.bg_avatar_circle)
            val p = (8 * resources.displayMetrics.density).toInt()
            binding.ivAvatar.setPadding(p, p, p, p)
            binding.ivAvatar.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.white),
            )
        }
    }

    private fun setupHeaderButtons() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnMore.setOnClickListener { anchor ->
            if (conversationId <= 0) {
                Toast.makeText(this, getString(R.string.chat_conversation_unavailable), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val popup = PopupMenu(this, anchor)
            if (participantId > 0) {
                popup.menu.add(0, MENU_REPORT_PROFILE, 0, getString(R.string.chat_report_profile))
            }
            popup.menu.add(0, MENU_DELETE_SELF, 1, getString(R.string.chat_delete_for_me))
            popup.menu.add(0, MENU_DELETE_BOTH, 2, getString(R.string.chat_delete_for_both))
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_REPORT_PROFILE -> showProfileReportDialog(participantId)
                    MENU_DELETE_SELF -> confirmDeleteConversation(scope = "self")
                    MENU_DELETE_BOTH -> confirmDeleteConversation(scope = "both")
                }
                true
            }
            popup.show()
        }
    }

    private fun confirmDeleteConversation(scope: String) {
        val deleteForBoth = scope == "both"
        AlertDialog.Builder(this)
            .setTitle(
                getString(
                    if (deleteForBoth) R.string.chat_delete_confirm_both_title
                    else R.string.chat_delete_confirm_self_title,
                ),
            )
            .setMessage(
                getString(
                    if (deleteForBoth) R.string.chat_delete_confirm_both_message
                    else R.string.chat_delete_confirm_self_message,
                ),
            )
            .setNegativeButton(R.string.chat_delete_cancel, null)
            .setPositiveButton(R.string.chat_delete_confirm_action) { _, _ ->
                deleteConversation(scope)
            }
            .show()
    }

    private fun deleteConversation(scope: String) {
        val token = session.getToken()
        if (token.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.chat_delete_failed), Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                chatRepository.deleteConversation(token, conversationId, scope)
                Toast.makeText(
                    this@ChatConversationActivity,
                    getString(
                        if (scope == "both") R.string.chat_delete_success_both
                        else R.string.chat_delete_success_self,
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete conversation", e)
                Toast.makeText(
                    this@ChatConversationActivity,
                    getString(R.string.chat_delete_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun showProfileReportDialog(targetUserId: Int) {
        val reasons = listOf(
            "spam" to "Spam",
            "fake_profile" to "Fake profile",
            "offensive" to "Offensive content",
            "harassment" to "Harassment / bullying",
            "other" to "Other",
        )
        val density = resources.displayMetrics.density
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (16 * density).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }
        val radioGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        reasons.forEachIndexed { index, (_, label) ->
            radioGroup.addView(RadioButton(this).apply {
                id = index + 1
                text = label
                setTextColor(android.graphics.Color.parseColor("#3D5A1E"))
                textSize = 14f
            })
        }
        radioGroup.check(1)
        val etDetails = EditText(this).apply {
            hint = "Additional details (optional)"
            maxLines = 3
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = (10 * density).toInt() }
        }
        container.addView(radioGroup)
        container.addView(etDetails)

        AlertDialog.Builder(this)
            .setTitle("Report profile")
            .setView(container)
            .setPositiveButton("Submit") { _, _ ->
                val checkedId = radioGroup.checkedRadioButtonId
                val reasonKey = if (checkedId in 1..reasons.size) reasons[checkedId - 1].first else "other"
                val text = etDetails.text?.toString()?.trim().orEmpty()
                submitProfileReport(targetUserId, reasonKey, text)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitProfileReport(targetUserId: Int, reason: String, text: String) {
        val token = session.getToken() ?: return
        lifecycleScope.launch {
            try {
                val resp = com.example.beautyappfrontend.data.remote.RetrofitInstance.api.reportUser(
                    "Bearer $token",
                    targetUserId,
                    ProfileReportRequest(reason = reason, text = text),
                )
                when (resp.code()) {
                    201 -> Toast.makeText(this@ChatConversationActivity, "Report submitted. Thank you!", Toast.LENGTH_SHORT).show()
                    409 -> Toast.makeText(this@ChatConversationActivity, "You have already reported this profile.", Toast.LENGTH_SHORT).show()
                    400 -> Toast.makeText(this@ChatConversationActivity, "Cannot report this profile.", Toast.LENGTH_SHORT).show()
                    403 -> Toast.makeText(this@ChatConversationActivity, "You can report only profiles you have interacted with.", Toast.LENGTH_SHORT).show()
                    else -> Toast.makeText(this@ChatConversationActivity, "Failed to submit report.", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this@ChatConversationActivity, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
            }
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
            pickChatMediaLauncher.launch(
                PickVisualMediaRequest(PickVisualMedia.ImageAndVideo),
            )
        }
    }

    private fun markConversationAsRead() {
        val token = session.getToken()
        if (token.isNullOrBlank() || conversationId == 0) return
        lifecycleScope.launch {
            try {
                chatRepository.markMessagesRead(token, conversationId)
            } catch (e: Exception) {
                Log.e(TAG, "Error marking messages read", e)
            }
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

    private fun uploadAndSendMedia(uri: Uri) {
        val token = session.getToken()
        if (token.isNullOrBlank() || conversationId == 0) {
            Toast.makeText(this, "Cannot send media", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val mime = chatMimeFor(uri)
                if (mime.isBlank()) {
                    Toast.makeText(
                        this@ChatConversationActivity,
                        "Unsupported file type",
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@launch
                }
                Toast.makeText(
                    this@ChatConversationActivity,
                    R.string.chat_media_uploading,
                    Toast.LENGTH_SHORT,
                ).show()
                val part = buildChatMediaPart(uri, mime)
                val uploaded = chatRepository.uploadChatMedia(token, conversationId, part)
                val caption = binding.etMessage.text?.toString()?.trim().orEmpty()
                val message = chatRepository.sendMessage(
                    token,
                    conversationId,
                    caption,
                    uploaded.messageType,
                    uploaded.url,
                )
                adapter.addMessage(message)
                binding.etMessage.text?.clear()
                binding.rvMessages.scrollToPosition(adapter.itemCount - 1)
            } catch (e: Exception) {
                Log.e(TAG, "Upload/send media failed", e)
                Toast.makeText(
                    this@ChatConversationActivity,
                    e.message ?: getString(R.string.chat_media_upload_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun chatMimeFor(uri: Uri): String {
        val t = contentResolver.getType(uri)?.trim().orEmpty()
        val allowedImg = setOf(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/heic",
        )
        val allowedVid = setOf(
            "video/mp4",
            "video/quicktime",
            "video/webm",
            "video/3gpp",
        )
        return when {
            t in allowedImg || t in allowedVid -> t
            t.startsWith("image/") -> "image/jpeg"
            t.startsWith("video/") -> "video/mp4"
            else -> ""
        }
    }

    private fun chatExtFor(mime: String) = when (mime) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic" -> "heic"
        "video/webm" -> "webm"
        "video/3gpp" -> "3gp"
        "video/quicktime", "video/mp4" -> "mp4"
        else -> if (mime.startsWith("video/")) "mp4" else "jpg"
    }

    private fun buildChatMediaPart(uri: Uri, mime: String): MultipartBody.Part {
        val ext = chatExtFor(mime)
        val file = File(cacheDir, "chat_media_${System.currentTimeMillis()}.$ext")
        contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not read file")
        val body = file.asRequestBody(mime.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("file", file.name, body)
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
