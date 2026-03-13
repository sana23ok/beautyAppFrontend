package com.example.beautyappfrontend.ui.screens

import android.app.ActivityOptions
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
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
import com.example.beautyappfrontend.databinding.DialogUserProfileEditBinding
import com.example.beautyappfrontend.domain.model.MasterProfileDraft
import com.example.beautyappfrontend.domain.model.MasterProfileRequest
import com.example.beautyappfrontend.domain.model.MasterProfileResponse
import com.example.beautyappfrontend.domain.model.MasterWorkPhotoRequest
import com.example.beautyappfrontend.domain.model.UserProfileUpdateRequest
import com.example.beautyappfrontend.utils.SessionManager
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var session: SessionManager
    private val authRepository = AuthRepository()
    private val masterRepository = MasterRepository()

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
        val urls = mutableListOf<String?>()
        if (draft.workPhotoUrl.isNotBlank()) {
            urls.add(draft.workPhotoUrl)
        }
        if (draft.profilePhoto.isNotBlank()) {
            urls.add(draft.profilePhoto)
        }
        while (urls.size < 4) {
            urls.add(null)
        }

        val views = listOf(binding.ivWork1, binding.ivWork2, binding.ivWork3, binding.ivWork4)
        views.zip(urls).forEach { (imageView, url) ->
            bindGalleryImage(imageView, url)
        }
    }

    private fun bindGalleryImage(imageView: ImageView, url: String?) {
        if (url.isNullOrBlank()) {
            imageView.setImageResource(R.drawable.ic_nav_profile)
            imageView.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.text_hint)
            )
            return
        }

        imageView.imageTintList = null
        imageView.load(url) {
            crossfade(true)
        }
    }

    private fun renderSchedule(draft: MasterProfileDraft) {
        binding.tvScheduleMon.text = formatHours(draft.mondayHours)
        binding.tvScheduleTue.text = formatHours(draft.tuesdayHours)
        binding.tvScheduleWed.text = formatHours(draft.wednesdayHours)
        binding.tvScheduleThu.text = formatHours(draft.thursdayHours)
        binding.tvScheduleFri.text = formatHours(draft.fridayHours)
        binding.tvScheduleSat.text = formatHours(draft.saturdayHours)
        binding.tvScheduleSun.text = formatHours(draft.sundayHours)
    }

    private fun formatHours(hours: String): String = hours.ifBlank { "Closed" }

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

        return MasterProfileDraft(
            masterId = masterId,
            name = name,
            specialization = specialization,
            city = city,
            address = address,
            experienceYears = experienceYears,
            description = description,
            profilePhoto = profilePhoto,
            workPhotoUrl = workPhotoUrl,
            workPhotoCaption = workPhotoCaption,
            mondayHours = dialogBinding.etMondayHours.text.toString().trim(),
            tuesdayHours = dialogBinding.etTuesdayHours.text.toString().trim(),
            wednesdayHours = dialogBinding.etWednesdayHours.text.toString().trim(),
            thursdayHours = dialogBinding.etThursdayHours.text.toString().trim(),
            fridayHours = dialogBinding.etFridayHours.text.toString().trim(),
            saturdayHours = dialogBinding.etSaturdayHours.text.toString().trim(),
            sundayHours = dialogBinding.etSundayHours.text.toString().trim(),
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
                if (workPhotoUrl.isNotBlank()) {
                    add(
                        MasterWorkPhotoRequest(
                            photoUrl = workPhotoUrl,
                            caption = workPhotoCaption,
                        )
                    )
                }
            },
        )
    }

    private fun MasterProfileResponse.toDraft(): MasterProfileDraft {
        return MasterProfileDraft(
            masterId = id,
            name = name,
            specialization = specialization,
            city = city,
            address = address,
            experienceYears = experienceYears,
            description = description,
            profilePhoto = profilePhoto,
            workPhotoUrl = workPhotos.firstOrNull()?.photoUrl ?: "",
            workPhotoCaption = workPhotos.firstOrNull()?.caption ?: "",
            mondayHours = mondayHours,
            tuesdayHours = tuesdayHours,
            wednesdayHours = wednesdayHours,
            thursdayHours = thursdayHours,
            fridayHours = fridayHours,
            saturdayHours = saturdayHours,
            sundayHours = sundayHours,
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
