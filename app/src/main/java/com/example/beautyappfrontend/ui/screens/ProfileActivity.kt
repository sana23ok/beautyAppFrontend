package com.example.beautyappfrontend.ui.screens

import android.app.ActivityOptions
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.data.repository.AuthRepository
import com.example.beautyappfrontend.data.repository.MasterRepository
import com.example.beautyappfrontend.databinding.ActivityProfileBinding
import com.example.beautyappfrontend.databinding.DialogMasterProfileEditBinding
import com.example.beautyappfrontend.databinding.DialogMasterScheduleEditBinding
import com.example.beautyappfrontend.databinding.DialogUserProfileEditBinding
import com.example.beautyappfrontend.domain.model.MasterProfileDraft
import com.example.beautyappfrontend.domain.model.MasterProfileRequest
import com.example.beautyappfrontend.domain.model.MasterProfileResponse
import com.example.beautyappfrontend.domain.model.MasterServiceItem
import com.example.beautyappfrontend.domain.model.MasterServiceRequest
import com.example.beautyappfrontend.domain.model.MasterScheduleData
import com.example.beautyappfrontend.domain.model.MasterWorkPhotoRequest
import com.example.beautyappfrontend.domain.model.normalizeScheduleWeeks
import com.example.beautyappfrontend.domain.model.UserProfileUpdateRequest
import com.example.beautyappfrontend.utils.ChatBadgeHelper
import com.example.beautyappfrontend.utils.MasterScheduleUi
import com.example.beautyappfrontend.utils.SessionManager
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var session: SessionManager
    private val authRepository = AuthRepository()
    private val masterRepository = MasterRepository()

    private var pendingAvatarEditText: EditText? = null

    /** 0 = current week … 3 = fourth week ahead (4 weeks total). */
    private var scheduleWeekOffset: Int = 0

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        val token = session.getToken() ?: return@registerForActivityResult
        val editText = pendingAvatarEditText
        lifecycleScope.launch {
            try {
                val mimeType = resolveImageMimeType(uri)
                val file = copyUriToCacheFile(uri, mimeType)
                val part = MultipartBody.Part.createFormData(
                    "photo",
                    file.name,
                    file.asRequestBody(mimeType.toMediaTypeOrNull())
                )
                val url = authRepository.uploadAvatar(token, part)
                editText?.setText(url)
                session.saveAvatarUrl(url)
                if (session.isMaster()) session.saveMasterProfilePhoto(url)
                populateUserData()
                syncProfileFromBackend()
                Toast.makeText(this@ProfileActivity, "Photo uploaded", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, e.message ?: "Upload failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** OkHttp must send a concrete MIME (e.g. image/jpeg); wildcards are rejected by Django. */
    private fun resolveImageMimeType(uri: Uri): String {
        val raw = contentResolver.getType(uri) ?: return "image/jpeg"
        val allowed = setOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/heic")
        return if (raw in allowed) raw else "image/jpeg"
    }

    private fun copyUriToCacheFile(uri: Uri, mimeType: String): File {
        val ext = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/heic" -> "heic"
            else -> "jpg"
        }
        val file = File(cacheDir, "upload_${System.currentTimeMillis()}.$ext")
        contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return file
    }

    companion object {
        private const val TAG = "ProfileActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        populateUserData()
        setupClickListeners()
        setupBottomNav()
        syncProfileFromBackend()
    }

    override fun onResume() {
        super.onResume()
        ChatBadgeHelper.updateBadge(binding.bottomNav, session.getToken(), lifecycleScope)
    }

    private fun populateUserData() {
        val name = session.getDisplayName()
        val email = session.getEmail()
        val phone = session.getPhoneNumber()
        val token = session.getToken()
        val isMaster = session.isMaster()

        Log.d(TAG, "── Profile data from storage ──")
        Log.d(TAG, "  displayName : '$name'")
        Log.d(TAG, "  email       : '$email'")
        Log.d(TAG, "  phone       : '$phone'")
        Log.d(TAG, "  token empty : ${token.isNullOrEmpty()}")

        binding.tvRole.text = if (isMaster) "Master" else "Client"
        binding.layoutMasterSection.visibility = if (isMaster) View.VISIBLE else View.GONE
        binding.layoutAppointmentsSection.visibility = if (isMaster) View.GONE else View.VISIBLE
        binding.btnMessage.visibility = if (isMaster) View.VISIBLE else View.GONE

        if (isMaster) {
            renderMasterProfile(
                draft = session.getMasterDraft(),
                fallbackName = name,
                fallbackEmail = email,
                fallbackPhone = phone,
            )
        } else {
            renderClientProfile(name, email, phone, session.getAvatarUrl().orEmpty())
        }
    }

    private fun syncProfileFromBackend() {
        val token = session.getToken() ?: return

        lifecycleScope.launch {
            try {
                val user = authRepository.getCurrentUser(token)
                session.saveUserInfo(user)
                session.saveIsMaster(user.isMaster == true)

                if (user.isMaster == true) {
                    try {
                        val master = masterRepository.getMyMasterProfile(token)
                        session.saveMasterProfile(master)
                        session.saveMasterDraft(master.toDraft())
                    } catch (e: Exception) {
                        if (!isNotFoundError(e)) {
                            throw e
                        }
                    }
                }

                populateUserData()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync profile", e)
            }
        }
    }

    private fun setupClickListeners() {
        binding.ivAvatar.setOnClickListener {
            if (session.getToken().isNullOrBlank()) {
                Toast.makeText(this, "Please sign in again", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pendingAvatarEditText = null
            pickImageLauncher.launch("image/*")
        }
        binding.btnEdit.setOnClickListener {
            if (session.isMaster()) {
                openMasterEditDialog()
            } else {
                openUserEditDialog()
            }
        }
        binding.btnMessage.setOnClickListener {
            Toast.makeText(this, "Messaging feature coming soon", Toast.LENGTH_SHORT).show()
        }
        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Log out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log out") { _, _ ->
                    session.clearSession()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        binding.btnSaveMasterProfile.setOnClickListener {
            openMasterEditDialog()
        }
        binding.appointment1.root.setOnClickListener {
            Toast.makeText(this, "Appointment details coming soon", Toast.LENGTH_SHORT).show()
        }
        binding.appointment2.root.setOnClickListener {
            Toast.makeText(this, "Appointment details coming soon", Toast.LENGTH_SHORT).show()
        }
        binding.appointment3.root.setOnClickListener {
            Toast.makeText(this, "Appointment details coming soon", Toast.LENGTH_SHORT).show()
        }
        binding.frameWorkAdd.setOnClickListener {
            if (session.isMaster()) {
                openMasterEditDialog()
            }
        }
        binding.btnSchedulePrev.setOnClickListener {
            if (scheduleWeekOffset > 0) {
                scheduleWeekOffset--
                renderSchedule(session.getMasterDraft())
            }
        }
        binding.btnScheduleNext.setOnClickListener {
            if (scheduleWeekOffset < 3) {
                scheduleWeekOffset++
                renderSchedule(session.getMasterDraft())
            }
        }
        binding.btnEditSchedule.setOnClickListener {
            openScheduleEditDialog()
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_profile
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    navigateTo(HomeActivity::class.java)
                    true
                }
                R.id.nav_search -> {
                    navigateTo(SearchPageActivity::class.java)
                    true
                }
                R.id.nav_chat -> {
                    navigateTo(ChatActivity::class.java)
                    true
                }
                R.id.nav_profile -> true
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

    private fun renderClientProfile(name: String, email: String, phone: String, avatarUrl: String) {
        binding.tvName.text = name.ifBlank { "Beauty client" }
        binding.tvSpecialization.text = "Beauty client"
        binding.tvLocation.text = "Saved in your account database profile"
        binding.tvDescription.text =
            "Your user profile is synced with the backend. Edit it anytime to keep your account details up to date."
        binding.tvEmail.text = email.ifBlank { "—" }
        binding.tvPhone.text = phone
        binding.tvPhone.visibility = if (phone.isBlank()) View.GONE else View.VISIBLE
        binding.btnEdit.text = "Edit Information"
        binding.tvMasterHelper.visibility = View.GONE
        binding.btnSaveMasterProfile.visibility = View.GONE
        renderAvatar(avatarUrl)
    }

    private fun renderMasterProfile(
        draft: MasterProfileDraft,
        fallbackName: String,
        fallbackEmail: String,
        fallbackPhone: String,
    ) {
        val displayName = draft.name.ifBlank { fallbackName.ifBlank { "Master profile" } }
        val location = listOf(draft.city, draft.address).filter { it.isNotBlank() }.joinToString(", ")

        binding.tvName.text = displayName
        binding.tvSpecialization.text = draft.specialization.ifBlank { "Master profile is not completed yet" }
        binding.tvLocation.text = location.ifBlank { "Add your city and address in Edit Information" }
        binding.tvDescription.text = draft.description.ifBlank {
            "Add a detailed description so clients can clearly see your style, services, and experience."
        }
        binding.tvEmail.text = fallbackEmail.ifBlank { "—" }
        binding.tvPhone.text = fallbackPhone.ifBlank { "" }
        binding.tvPhone.visibility = if (fallbackPhone.isNotBlank()) View.VISIBLE else View.GONE
        binding.btnEdit.text = if (draft.masterId == null) "Fill Information" else "Edit Information"
        binding.tvMasterHelper.visibility = if (draft.masterId == null) View.VISIBLE else View.GONE
        binding.btnSaveMasterProfile.visibility = if (draft.masterId == null) View.VISIBLE else View.GONE
        binding.btnSaveMasterProfile.text = "Create master profile"

        binding.btnEditSchedule.visibility = if (draft.masterId != null) View.VISIBLE else View.GONE

        renderAvatar(draft.profilePhoto.ifBlank { session.getAvatarUrl().orEmpty() })
        renderWorkGallery(draft)
        renderSchedule(draft)
    }

    private fun renderAvatar(profilePhotoUrl: String) {
        if (profilePhotoUrl.isBlank()) {
            binding.ivAvatar.setImageResource(R.drawable.ic_nav_profile)
            binding.ivAvatar.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.text_hint)
            )
            return
        }

        binding.ivAvatar.imageTintList = null
        binding.ivAvatar.load(profilePhotoUrl) {
            crossfade(true)
        }
    }

    private fun renderWorkGallery(draft: MasterProfileDraft) {
        val profileUrl = draft.profilePhoto.ifBlank { session.getAvatarUrl().orEmpty() }.trim()
        val workUrls = draft.workPhotoUrls
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(profileUrl, ignoreCase = true) }
            .distinct()
            .take(8)

        val padded = workUrls.toMutableList<String?>()
        while (padded.size < 8) padded.add(null)

        val cells = listOf(
            binding.ivWork1,
            binding.ivWork2,
            binding.ivWork3,
            binding.ivWork4,
            binding.ivWork5,
            binding.ivWork6,
            binding.ivWork7,
            binding.ivWork8,
        )
        cells.zip(padded).forEach { (imageView, url) ->
            bindGalleryCell(imageView, url)
        }

        // Last tile: blurred background + plus (never uses profile photo).
        val addTileBg = workUrls.lastOrNull() ?: workUrls.firstOrNull()
        bindAddPhotoTile(addTileBg)
    }

    private fun bindGalleryCell(imageView: ImageView, url: String?) {
        if (url.isNullOrBlank()) {
            imageView.setImageResource(R.drawable.ic_nav_profile)
            imageView.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.text_hint)
            )
            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
            return
        }

        imageView.imageTintList = null
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.load(url) {
            crossfade(true)
        }
    }

    private fun bindAddPhotoTile(backgroundUrl: String?) {
        binding.ivWork9Plus.visibility = View.VISIBLE
        clearAddTileBlur()
        if (backgroundUrl.isNullOrBlank()) {
            binding.ivWork9Bg.setImageDrawable(null)
            binding.ivWork9Bg.setBackgroundResource(R.drawable.bg_photo_add_empty)
            binding.ivWork9Bg.alpha = 1f
            return
        }
        binding.ivWork9Bg.background = null
        binding.ivWork9Bg.alpha = 1f
        binding.ivWork9Bg.scaleType = ImageView.ScaleType.CENTER_CROP
        binding.ivWork9Bg.load(backgroundUrl) {
            crossfade(true)
            listener(
                onSuccess = { _, _ -> applyBlurToAddTile() },
                onError = { _, _ ->
                    clearAddTileBlur()
                    binding.ivWork9Bg.setImageDrawable(null)
                    binding.ivWork9Bg.setBackgroundResource(R.drawable.bg_photo_add_empty)
                    binding.ivWork9Bg.alpha = 1f
                },
            )
        }
    }

    private fun clearAddTileBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.ivWork9Bg.setRenderEffect(null)
        }
    }

    private fun applyBlurToAddTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.ivWork9Bg.setRenderEffect(
                RenderEffect.createBlurEffect(22f, 22f, Shader.TileMode.CLAMP),
            )
            binding.ivWork9Bg.alpha = 1f
        } else {
            binding.ivWork9Bg.alpha = 0.72f
        }
    }

    private fun renderSchedule(draft: MasterProfileDraft) {
        binding.tvScheduleWeekLabel.text = MasterScheduleUi.weekRangeLabel(scheduleWeekOffset)
        binding.btnSchedulePrev.isEnabled = scheduleWeekOffset > 0
        binding.btnSchedulePrev.alpha = if (scheduleWeekOffset > 0) 1f else 0.35f
        binding.btnScheduleNext.isEnabled = scheduleWeekOffset < 3
        binding.btnScheduleNext.alpha = if (scheduleWeekOffset < 3) 1f else 0.35f
        MasterScheduleUi.populateGrid(
            binding.layoutScheduleGrid,
            scheduleWeekOffset,
            normalizeScheduleWeeks(draft.scheduleWeeks),
        )
    }

    private fun openScheduleEditDialog() {
        if (!session.isMaster()) return
        if (session.getToken().isNullOrBlank()) {
            Toast.makeText(this, "Please sign in again", Toast.LENGTH_SHORT).show()
            return
        }
        val draft = session.getMasterDraft()
        if (draft.masterId == null) {
            Toast.makeText(this, "Create your master profile first", Toast.LENGTH_SHORT).show()
            return
        }

        val bind = DialogMasterScheduleEditBinding.inflate(layoutInflater)
        var editWeek = scheduleWeekOffset.coerceIn(0, MasterScheduleData.WEEK_COUNT - 1)

        val weeks = normalizeScheduleWeeks(draft.scheduleWeeks).map { week ->
            week.map { hours -> hours.toMutableList() }.toMutableList()
        }.toMutableList()

        fun weekFromGrid(): MutableList<MutableList<Int>> {
            return bind.scheduleGridEdit.getWeek().map { it.toMutableList() }.toMutableList()
        }

        fun applyWeekToGrid() {
            bind.tvScheduleEditWeek.text = MasterScheduleUi.weekRangeLabel(editWeek)
            bind.btnScheduleEditPrev.isEnabled = editWeek > 0
            bind.btnScheduleEditPrev.alpha = if (editWeek > 0) 1f else 0.35f
            bind.btnScheduleEditNext.isEnabled = editWeek < MasterScheduleData.WEEK_COUNT - 1
            bind.btnScheduleEditNext.alpha =
                if (editWeek < MasterScheduleData.WEEK_COUNT - 1) 1f else 0.35f
            bind.scheduleGridEdit.setWeek(weeks[editWeek].map { it.toList() })
        }

        applyWeekToGrid()

        bind.btnScheduleEditPrev.setOnClickListener {
            weeks[editWeek] = weekFromGrid()
            if (editWeek > 0) {
                editWeek--
                applyWeekToGrid()
            }
        }
        bind.btnScheduleEditNext.setOnClickListener {
            weeks[editWeek] = weekFromGrid()
            if (editWeek < MasterScheduleData.WEEK_COUNT - 1) {
                editWeek++
                applyWeekToGrid()
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.schedule_edit_dialog_title)
            .setView(bind.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.schedule_edit_save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                weeks[editWeek] = weekFromGrid()
                val normalized = normalizeScheduleWeeks(weeks.map { w -> w.map { it.toList() } })
                val updated = draft.copy(scheduleWeeks = normalized)
                session.saveMasterDraft(updated)
                populateUserData()
                dialog.dismiss()
                Toast.makeText(this, R.string.schedule_saved_local, Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun openUserEditDialog() {
        val token = session.getToken()
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Please sign in again", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogUserProfileEditBinding.inflate(layoutInflater)
        dialogBinding.etFullName.setText(session.getDisplayName())
        dialogBinding.etPhone.setText(session.getPhoneNumber())
        dialogBinding.etAvatar.setText(session.getAvatarUrl().orEmpty())

        dialogBinding.btnUploadAvatar.setOnClickListener {
            pendingAvatarEditText = dialogBinding.etAvatar
            pickImageLauncher.launch("image/*")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit profile")
            .setView(dialogBinding.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val fullName = dialogBinding.etFullName.text.toString().trim()
                val phone = dialogBinding.etPhone.text.toString().trim()
                val avatar = dialogBinding.etAvatar.text.toString().trim()

                if (fullName.isBlank()) {
                    Toast.makeText(this, "Enter your full name", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val (firstName, lastName) = splitName(fullName)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false

                lifecycleScope.launch {
                    try {
                        val user = authRepository.updateCurrentUser(
                            token = token,
                            request = UserProfileUpdateRequest(
                                firstName = firstName,
                                lastName = lastName,
                                phoneNumber = phone,
                                avatar = avatar.ifBlank { null },
                            ),
                        )
                        session.saveUserInfo(user)
                        populateUserData()
                        dialog.dismiss()
                        Toast.makeText(
                            this@ProfileActivity,
                            "Profile updated successfully",
                            Toast.LENGTH_SHORT,
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@ProfileActivity,
                            e.message ?: "Failed to update profile",
                            Toast.LENGTH_LONG,
                        ).show()
                    } finally {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                    }
                }
            }
        }

        dialog.show()
    }

    private fun openMasterEditDialog() {
        if (!session.isMaster()) {
            return
        }

        val token = session.getToken()
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Please sign in again", Toast.LENGTH_SHORT).show()
            return
        }

        val existingDraft = session.getMasterDraft()
        val dialogBinding = DialogMasterProfileEditBinding.inflate(layoutInflater)
        populateMasterDialogFields(dialogBinding, existingDraft)

        dialogBinding.btnUploadProfilePhoto.setOnClickListener {
            pendingAvatarEditText = dialogBinding.etMasterProfilePhoto
            pickImageLauncher.launch("image/*")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existingDraft.masterId == null) "Create master profile" else "Edit master profile")
            .setView(dialogBinding.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val updatedDraft = readMasterDraft(dialogBinding, existingDraft.masterId) ?: return@setOnClickListener
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false

                lifecycleScope.launch {
                    try {
                        val (firstName, lastName) = splitName(updatedDraft.name)
                        val user = authRepository.updateCurrentUser(
                            token = token,
                            request = UserProfileUpdateRequest(
                                firstName = firstName,
                                lastName = lastName,
                                avatar = updatedDraft.profilePhoto.ifBlank { null },
                            ),
                        )
                        session.saveUserInfo(user)
                        session.saveIsMaster(true)

                        val request = updatedDraft.toRequest()
                        val master = if (updatedDraft.masterId == null) {
                            masterRepository.createMasterProfile(token, request)
                        } else {
                            masterRepository.updateMyMasterProfile(token, request)
                        }

                        val savedDraft = master.toDraft()
                        session.saveMasterProfile(master)
                        session.saveMasterDraft(savedDraft)
                        populateUserData()
                        dialog.dismiss()
                        Toast.makeText(
                            this@ProfileActivity,
                            if (updatedDraft.masterId == null) {
                                "Master profile created successfully"
                            } else {
                                "Master profile updated successfully"
                            },
                            Toast.LENGTH_LONG,
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@ProfileActivity,
                            e.message ?: "Failed to save master profile",
                            Toast.LENGTH_LONG,
                        ).show()
                    } finally {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                    }
                }
            }
        }

        dialog.show()
    }

    private fun populateMasterDialogFields(
        dialogBinding: DialogMasterProfileEditBinding,
        draft: MasterProfileDraft,
    ) {
        dialogBinding.etMasterName.setText(draft.name.ifBlank { session.getDisplayName() })
        dialogBinding.etMasterSpecialization.setText(draft.specialization)
        dialogBinding.etMasterCity.setText(draft.city)
        dialogBinding.etMasterAddress.setText(draft.address)
        dialogBinding.etMasterExperience.setText(
            if (draft.experienceYears > 0) draft.experienceYears.toString() else ""
        )
        dialogBinding.etMasterDescription.setText(draft.description)
        dialogBinding.etMasterProfilePhoto.setText(draft.profilePhoto.ifBlank { session.getAvatarUrl().orEmpty() })
        dialogBinding.etMasterWorkPhoto.setText(draft.workPhotoUrl)
        dialogBinding.etMasterWorkPhotoCaption.setText(draft.workPhotoCaption)
        dialogBinding.etMondayHours.setText(draft.mondayHours)
        dialogBinding.etTuesdayHours.setText(draft.tuesdayHours)
        dialogBinding.etWednesdayHours.setText(draft.wednesdayHours)
        dialogBinding.etThursdayHours.setText(draft.thursdayHours)
        dialogBinding.etFridayHours.setText(draft.fridayHours)
        dialogBinding.etSaturdayHours.setText(draft.saturdayHours)
        dialogBinding.etSundayHours.setText(draft.sundayHours)
    }

    private fun readMasterDraft(
        dialogBinding: DialogMasterProfileEditBinding,
        masterId: Int?,
    ): MasterProfileDraft? {
        val name = dialogBinding.etMasterName.text.toString().trim()
        val specialization = dialogBinding.etMasterSpecialization.text.toString().trim()
        val city = dialogBinding.etMasterCity.text.toString().trim()
        val address = dialogBinding.etMasterAddress.text.toString().trim()
        val experienceText = dialogBinding.etMasterExperience.text.toString().trim()
        val description = dialogBinding.etMasterDescription.text.toString().trim()
        val profilePhoto = dialogBinding.etMasterProfilePhoto.text.toString().trim()
        val workPhotoUrl = dialogBinding.etMasterWorkPhoto.text.toString().trim()
        val workPhotoCaption = dialogBinding.etMasterWorkPhotoCaption.text.toString().trim()

        if (name.isBlank()) {
            Toast.makeText(this, "Enter your master name", Toast.LENGTH_SHORT).show()
            return null
        }
        if (specialization.isBlank()) {
            Toast.makeText(this, "Enter your specialization", Toast.LENGTH_SHORT).show()
            return null
        }
        if (city.isBlank()) {
            Toast.makeText(this, "Enter your city", Toast.LENGTH_SHORT).show()
            return null
        }

        val experienceYears = experienceText.toIntOrNull()
        if (experienceYears == null || experienceYears < 0) {
            Toast.makeText(this, "Enter a valid experience value", Toast.LENGTH_SHORT).show()
            return null
        }

        val workPhotoUrls = if (workPhotoUrl.isNotBlank()) listOf(workPhotoUrl) else emptyList()
        val base = session.getMasterDraft()
        return MasterProfileDraft(
            masterId = masterId,
            name = name,
            specialization = specialization,
            city = city,
            address = address,
            experienceYears = experienceYears,
            description = description,
            profilePhoto = profilePhoto,
            workPhotoUrls = workPhotoUrls,
            workPhotoUrl = workPhotoUrl,
            workPhotoCaption = workPhotoCaption,
            mondayHours = dialogBinding.etMondayHours.text.toString().trim(),
            tuesdayHours = dialogBinding.etTuesdayHours.text.toString().trim(),
            wednesdayHours = dialogBinding.etWednesdayHours.text.toString().trim(),
            thursdayHours = dialogBinding.etThursdayHours.text.toString().trim(),
            fridayHours = dialogBinding.etFridayHours.text.toString().trim(),
            saturdayHours = dialogBinding.etSaturdayHours.text.toString().trim(),
            sundayHours = dialogBinding.etSundayHours.text.toString().trim(),
            scheduleWeeks = base.scheduleWeeks,
            services = base.services,
        )
    }

    private fun MasterProfileDraft.toRequest(): MasterProfileRequest {
        return MasterProfileRequest(
            name = name,
            specialization = specialization,
            city = city,
            address = address,
            experienceYears = experienceYears,
            description = description,
            profilePhoto = profilePhoto,
            mondayHours = mondayHours,
            tuesdayHours = tuesdayHours,
            wednesdayHours = wednesdayHours,
            thursdayHours = thursdayHours,
            fridayHours = fridayHours,
            saturdayHours = saturdayHours,
            sundayHours = sundayHours,
            workPhotos = buildList {
                val urls = when {
                    workPhotoUrls.isNotEmpty() -> workPhotoUrls
                    workPhotoUrl.isNotBlank() -> listOf(workPhotoUrl)
                    else -> emptyList()
                }
                urls.forEachIndexed { index, url ->
                    add(
                        MasterWorkPhotoRequest(
                            photoUrl = url,
                            caption = if (index == 0) workPhotoCaption else "",
                        ),
                    )
                }
            },
            services = services.map { row ->
                MasterServiceRequest(
                    name = row.name,
                    price = row.price,
                    durationMinutes = row.durationMinutes,
                )
            },
        )
    }

    private fun MasterProfileResponse.toDraft(): MasterProfileDraft {
        val profile = profilePhoto.trim()
        val photos = workPhotos.orEmpty()
        val filteredUrls = photos.map { it.photoUrl }
            .filter { it.isNotBlank() && !it.trim().equals(profile, ignoreCase = true) }
        return MasterProfileDraft(
            masterId = id,
            name = name,
            specialization = specialization,
            city = city,
            address = address,
            experienceYears = experienceYears,
            description = description,
            profilePhoto = profilePhoto,
            workPhotoUrls = filteredUrls,
            workPhotoUrl = filteredUrls.firstOrNull() ?: "",
            workPhotoCaption = photos.firstOrNull()?.caption ?: "",
            mondayHours = mondayHours,
            tuesdayHours = tuesdayHours,
            wednesdayHours = wednesdayHours,
            thursdayHours = thursdayHours,
            fridayHours = fridayHours,
            saturdayHours = saturdayHours,
            sundayHours = sundayHours,
            scheduleWeeks = session.getMasterDraft().scheduleWeeks,
            services = services.orEmpty().map { s ->
                MasterServiceItem(
                    name = s.name,
                    price = kotlin.math.round(s.price).toInt().coerceAtLeast(0),
                    durationMinutes = s.durationMinutes,
                )
            },
        )
    }

    private fun splitName(fullName: String): Pair<String, String> {
        val parts = fullName.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val firstName = parts.firstOrNull().orEmpty()
        val lastName = parts.drop(1).joinToString(" ")
        return firstName to lastName
    }

    private fun isNotFoundError(error: Exception): Boolean {
        return error.message?.contains("HTTP 404") == true
    }
}
