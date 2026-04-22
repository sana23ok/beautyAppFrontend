package com.example.beautyappfrontend.ui.screens

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.data.repository.AuthRepository
import com.example.beautyappfrontend.data.repository.BookingRepository
import com.example.beautyappfrontend.data.repository.ChatRepository
import com.example.beautyappfrontend.data.repository.MasterRepository
import com.example.beautyappfrontend.databinding.ActivityProfileBinding
import com.example.beautyappfrontend.databinding.DialogBookingCancelReasonBinding
import com.example.beautyappfrontend.databinding.DialogBookingInfoBinding
import com.example.beautyappfrontend.databinding.ItemAppointmentBinding
import com.example.beautyappfrontend.databinding.DialogMasterProfileEditBinding
import com.example.beautyappfrontend.databinding.DialogMasterScheduleEditBinding
import com.example.beautyappfrontend.databinding.DialogUserProfileEditBinding
import com.example.beautyappfrontend.domain.model.BookingResponse
import com.example.beautyappfrontend.domain.model.MasterProfileDraft
import com.example.beautyappfrontend.domain.model.MasterProfileRequest
import com.example.beautyappfrontend.domain.model.MasterProfileResponse
import com.example.beautyappfrontend.domain.model.MasterServiceItem
import com.example.beautyappfrontend.domain.model.MasterServiceRequest
import com.example.beautyappfrontend.domain.model.MasterScheduleData
import com.example.beautyappfrontend.domain.model.MasterWorkPhotoRequest
import com.example.beautyappfrontend.domain.model.MasterWorkPhotoResponse
import com.example.beautyappfrontend.domain.model.normalizeScheduleWeeks
import com.example.beautyappfrontend.domain.model.UserProfileUpdateRequest
import com.example.beautyappfrontend.utils.ChatBadgeHelper
import com.example.beautyappfrontend.utils.MasterProfileSchedule
import com.example.beautyappfrontend.utils.MasterScheduleFormat
import com.example.beautyappfrontend.utils.MasterScheduleUi
import com.example.beautyappfrontend.utils.SessionManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var session: SessionManager
    private val authRepository = AuthRepository()
    private val bookingRepository = BookingRepository()
    private val masterRepository = MasterRepository()
    private val chatRepository = ChatRepository()

    private var pendingAvatarEditText: EditText? = null

    /** 0 = current week … 3 = fourth week ahead (4 weeks total). */
    private var scheduleWeekOffset: Int = 0
    private var clientBookings: List<BookingResponse> = emptyList()
    private var masterBookings: List<BookingResponse> = emptyList()

    /** Latest portfolio photos (with backend ids) for the current master. */
    private var workPhotos: List<MasterWorkPhotoResponse> = emptyList()

    /** Refresh work photos when returning from the fullscreen gallery (a delete may have happened). */
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val changed = result.data?.getBooleanExtra(PhotoGalleryActivity.EXTRA_RESULT_CHANGED, false) == true
        if (result.resultCode == Activity.RESULT_OK || changed) {
            syncProfileFromBackend()
        }
    }

    /** Dedicated picker for portfolio (work) photos — separate from the avatar picker. */
    private val pickWorkPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        uploadWorkPhoto(uri)
    }

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
        private const val APPOINTMENTS_PER_MONTH_LIMIT = 20

        /** Must match backend `MAX_WORK_PHOTOS` in masters/views.py. */
        private const val MAX_WORK_PHOTOS = 50

        /** Portfolio grid always displays at most this many tiles (3×3). */
        private const val MAX_GRID_TILES = 9
    }

    private data class ServiceRowViews(
        val serviceId: Int?,
        val name: EditText,
        val minutes: EditText,
        val price: EditText,
    )

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
        binding.layoutAppointmentsSection.visibility = View.VISIBLE

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
                val user = fetchAndPersistUser(token)
                syncRoleSpecificData(token, user.isMaster == true)
                populateUserData()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync profile", e)
            }
        }
    }

    private suspend fun fetchAndPersistUser(
        token: String,
    ): com.example.beautyappfrontend.domain.model.AuthUserInfo {
        val user = authRepository.getCurrentUser(token)
        session.saveUserInfo(user)
        session.saveIsMaster(user.isMaster == true)
        return user
    }

    private suspend fun syncRoleSpecificData(token: String, isMaster: Boolean) {
        if (isMaster) {
            syncMasterData(token)
        } else {
            syncClientData(token)
        }
    }

    /** Client: client bookings are the only data to load. */
    private suspend fun syncClientData(token: String) = coroutineScope {
        val bookingsDeferred = async { runCatching { bookingRepository.getMyBookings(token) }.getOrDefault(emptyList()) }
        clientBookings = bookingsDeferred.await()
        masterBookings = emptyList()
        workPhotos = emptyList()
    }

    /**
     * Master: fan out all independent calls in parallel — client bookings, master
     * profile and work-photo list all hit unrelated endpoints, so running them
     * sequentially was the main cause of the multi-second profile load seen in
     * logcat. Master-bookings need the master id, so they kick off right after
     * the profile resolves (still in parallel with the work-photos list).
     */
    private suspend fun syncMasterData(token: String) = coroutineScope {
        val clientBookingsDeferred =
            async { runCatching { bookingRepository.getMyBookings(token) }.getOrDefault(emptyList()) }
        val masterProfileDeferred = async { runCatching { masterRepository.getMyMasterProfile(token) } }
        val workPhotosDeferred = async { runCatching { masterRepository.getMyWorkPhotos(token) } }

        clientBookings = clientBookingsDeferred.await()

        val masterResult = masterProfileDeferred.await()
        val masterError = masterResult.exceptionOrNull()
        if (masterError != null) {
            if (!isNotFoundError(masterError)) throw masterError
            masterBookings = emptyList()
            workPhotos = workPhotosDeferred.await().getOrDefault(emptyList())
            return@coroutineScope
        }

        val master = masterResult.getOrNull() ?: return@coroutineScope
        session.saveMasterProfile(master)
        session.saveMasterDraft(master.toDraft())

        val masterBookingsDeferred = async { loadMasterBookingsRange(master.id) }
        val photosResult = workPhotosDeferred.await()
        workPhotos = photosResult.getOrElse { master.workPhotos.orEmpty() }
        masterBookings = masterBookingsDeferred.await()
    }

    private fun isNotFoundError(error: Throwable): Boolean {
        return error.message?.contains("HTTP 404") == true
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
        binding.btnAddMasterService.setOnClickListener {
            addServiceRow()
        }
        binding.btnSaveMasterPriceList.setOnClickListener {
            savePriceList()
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
        renderClientAppointments(clientBookings)
    }

    private fun renderClientAppointments(bookings: List<BookingResponse>) {
        val container = binding.layoutAppointmentsList
        container.removeAllViews()

        val visible = filterVisibleAppointments(bookings)

        if (visible.isEmpty()) {
            container.visibility = View.GONE
            binding.tvAppointmentsEmpty.visibility = View.VISIBLE
            return
        }

        binding.tvAppointmentsEmpty.visibility = View.GONE
        container.visibility = View.VISIBLE

        visible.forEachIndexed { index, booking ->
            if (index > 0) {
                container.addView(
                    View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1,
                        )
                        setBackgroundResource(R.color.divider_color)
                    },
                )
            }

            val row = ItemAppointmentBinding.inflate(layoutInflater, container, false)
            val location = listOf(booking.masterCity, booking.masterAddress)
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { "—" }
            row.tvAppointmentDate.text =
                "${booking.appointmentDate.toDisplayDate()} · ${booking.startTime.shortTime()}-${booking.endTime.shortTime()}"
            row.tvAppointmentSubtitle.text =
                "${booking.serviceName} · ${booking.masterName} · $location"
            row.root.alpha = 1f
            row.root.isClickable = true
            row.root.setOnClickListener { openMasterDetail(booking.master) }
            container.addView(row.root)
        }
    }

    /**
     * Returns upcoming appointments sorted ascending, capped at 20 per calendar month
     * (grouped by YYYY-MM). Past appointments are filtered out.
     */
    private fun filterVisibleAppointments(bookings: List<BookingResponse>): List<BookingResponse> {
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
        val sorted = bookings
            .asSequence()
            .filter { it.appointmentDate.isNotBlank() }
            .filter { it.appointmentDate >= todayKey }
            .sortedWith(
                compareBy<BookingResponse> { it.appointmentDate }.thenBy { it.startTime }
            )
            .toList()

        val perMonth = mutableMapOf<String, Int>()
        val result = mutableListOf<BookingResponse>()
        for (booking in sorted) {
            val monthKey = booking.appointmentDate.take(7) // YYYY-MM
            val count = perMonth.getOrDefault(monthKey, 0)
            if (count >= APPOINTMENTS_PER_MONTH_LIMIT) continue
            perMonth[monthKey] = count + 1
            result.add(booking)
        }
        return result
    }

    private fun openMasterDetail(masterId: Int) {
        if (masterId <= 0) {
            Toast.makeText(this, "Master profile is not available", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, MasterDetailActivity::class.java)
            .putExtra(MasterDetailActivity.EXTRA_MASTER_ID, masterId)
        startActivity(intent)
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
        renderPriceListEditor(draft)
        renderSchedule(draft)
        renderClientAppointments(clientBookings)
    }

    private fun renderPriceListEditor(draft: MasterProfileDraft) {
        binding.layoutMasterPriceBlock.visibility = if (draft.masterId != null) View.VISIBLE else View.GONE
        binding.layoutMasterPriceRows.removeAllViews()

        val rows = draft.services
            .map { it.copy(name = it.name.trim()) }
            .filter { it.name.isNotBlank() || it.durationMinutes > 0 || it.price > 0 }

        if (rows.isEmpty()) {
            addServiceRow()
            return
        }
        rows.forEach { addServiceRow(it) }
    }

    private fun addServiceRow(initial: MasterServiceItem = MasterServiceItem()) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            setPadding(0, 8.dp(), 0, 4.dp())
        }

        val etName = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 44.dp(), 1f)
            background = ContextCompat.getDrawable(this@ProfileActivity, R.drawable.bg_input_field)
            hint = getString(R.string.master_price_col_service)
            setText(initial.name)
            setPadding(12.dp(), 0, 12.dp(), 0)
            setSingleLine(true)
        }
        val etMinutes = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(54.dp(), 44.dp()).apply { marginStart = 6.dp() }
            background = ContextCompat.getDrawable(this@ProfileActivity, R.drawable.bg_input_field)
            hint = getString(R.string.master_price_hint_minutes)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            if (initial.durationMinutes > 0) setText(initial.durationMinutes.toString())
            gravity = android.view.Gravity.CENTER
        }
        val etPrice = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(78.dp(), 44.dp()).apply { marginStart = 6.dp() }
            background = ContextCompat.getDrawable(this@ProfileActivity, R.drawable.bg_input_field)
            hint = getString(R.string.master_price_hint_uah)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            if (initial.price > 0) setText(initial.price.toString())
            gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            setPadding(10.dp(), 0, 10.dp(), 0)
        }
        val btnRemove = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(40.dp(), 40.dp()).apply { marginStart = 6.dp() }
            background = ContextCompat.getDrawable(this@ProfileActivity, R.drawable.bg_outlined_peach)
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this@ProfileActivity, R.color.peach_dark))
            contentDescription = getString(R.string.master_price_remove_row)
            setOnClickListener {
                binding.layoutMasterPriceRows.removeView(row)
                if (binding.layoutMasterPriceRows.childCount == 0) addServiceRow()
            }
        }

        row.addView(etName)
        row.addView(etMinutes)
        row.addView(etPrice)
        row.addView(btnRemove)
        row.tag = ServiceRowViews(initial.id, etName, etMinutes, etPrice)
        binding.layoutMasterPriceRows.addView(row)
    }

    private fun collectServicesFromRows(): List<MasterServiceItem>? {
        val out = mutableListOf<MasterServiceItem>()
        for (i in 0 until binding.layoutMasterPriceRows.childCount) {
            val row = binding.layoutMasterPriceRows.getChildAt(i)
            val holder = row.tag as? ServiceRowViews ?: continue
            val name = holder.name.text.toString().trim()
            val minutesRaw = holder.minutes.text.toString().trim()
            val priceRaw = holder.price.text.toString().trim()
            if (name.isBlank() && minutesRaw.isBlank() && priceRaw.isBlank()) continue

            if (name.isBlank()) {
                Toast.makeText(this, "Enter service name in row ${i + 1}", Toast.LENGTH_SHORT).show()
                return null
            }
            val minutes = minutesRaw.toIntOrNull()
            if (minutes == null || minutes < 0) {
                Toast.makeText(this, "Invalid duration in row ${i + 1}", Toast.LENGTH_SHORT).show()
                return null
            }
            val price = priceRaw.toIntOrNull()
            if (price == null || price < 0) {
                Toast.makeText(this, "Invalid price in row ${i + 1}", Toast.LENGTH_SHORT).show()
                return null
            }
            out.add(
                MasterServiceItem(
                    id = holder.serviceId,
                    name = name,
                    durationMinutes = minutes,
                    price = price,
                ),
            )
        }
        return out
    }

    private fun savePriceList() {
        val token = session.getToken()
        val draft = session.getMasterDraft()
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Please sign in again", Toast.LENGTH_SHORT).show()
            return
        }
        if (draft.masterId == null) {
            Toast.makeText(this, "Create your master profile first", Toast.LENGTH_SHORT).show()
            return
        }
        val currentRows = collectServicesFromRows() ?: return

        // Diff: figure out which services to create, update, or delete.
        val originalIds = draft.services.mapNotNull { it.id }.toSet()
        val currentIds  = currentRows.mapNotNull { it.id }.toSet()
        val toDeleteIds = originalIds - currentIds
        val toUpdate    = currentRows.filter { it.id != null }
        val toCreate    = currentRows.filter { it.id == null }

        binding.btnSaveMasterPriceList.isEnabled = false
        lifecycleScope.launch {
            try {
                // 1. Delete removed rows (safe: backend keeps row if bookings exist)
                for (id in toDeleteIds) {
                    masterRepository.deleteService(token, id)
                }
                // 2. Update changed existing rows
                for (svc in toUpdate) {
                    masterRepository.updateService(
                        token, svc.id!!,
                        MasterServiceRequest(
                            name = svc.name,
                            price = svc.price,
                            durationMinutes = svc.durationMinutes,
                            requiresPrepayment = svc.requiresPrepayment,
                        ),
                    )
                }
                // 3. Create brand-new rows
                for (svc in toCreate) {
                    masterRepository.createService(
                        token,
                        MasterServiceRequest(
                            name = svc.name,
                            price = svc.price,
                            durationMinutes = svc.durationMinutes,
                            requiresPrepayment = svc.requiresPrepayment,
                        ),
                    )
                }
                // 4. Re-fetch so UI shows fresh IDs for newly created rows
                val refreshed = masterRepository.getMyMasterProfile(token)
                session.saveMasterProfile(refreshed)
                session.saveMasterDraft(refreshed.toDraft())
                populateUserData()
                Toast.makeText(
                    this@ProfileActivity,
                    getString(R.string.master_price_saved),
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ProfileActivity,
                    e.message ?: "Failed to save price list",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                binding.btnSaveMasterPriceList.isEnabled = true
            }
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

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
        val container = binding.layoutWorkGallery
        container.removeAllViews()

        // Photos we have ids for (from /me/work_photos/); these support deletion.
        val photos = workPhotos.filter { it.photoUrl.isNotBlank() }

        // Fallback: if the dedicated endpoint hasn't been reached yet, show the URLs
        // from the master draft so something is visible immediately on first load.
        val fallback = if (photos.isEmpty()) {
            draft.workPhotoUrls
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .map { MasterWorkPhotoResponse(id = null, photoUrl = it, caption = "") }
        } else {
            emptyList()
        }

        val combined = photos + fallback
        val canUploadMore = photos.size < MAX_WORK_PHOTOS
        val canDelete = photos.any { (it.id ?: 0) > 0 }

        binding.tvWorkGalleryEmpty.visibility =
            if (combined.isEmpty()) View.VISIBLE else View.GONE

        // Grid is capped at MAX_GRID_TILES (9). Slot 9 is reserved for the "+" tile
        // so the master can always add more photos regardless of portfolio size.
        val overflow = combined.size >= MAX_GRID_TILES
        val normalTileCount = if (overflow) MAX_GRID_TILES - 1 else combined.size

        for (i in 0 until normalTileCount) {
            container.addView(createWorkPhotoTile(combined[i], i, combined, canDelete))
        }

        if (overflow) {
            if (canUploadMore) {
                container.addView(createAddPhotoTile())
            } else {
                container.addView(
                    createWorkPhotoTile(combined[MAX_GRID_TILES - 1], MAX_GRID_TILES - 1, combined, canDelete),
                )
            }
        } else if (canUploadMore) {
            container.addView(createAddPhotoTile())
        }
    }

    private fun workTileLayoutParams(): GridLayout.LayoutParams {
        return GridLayout.LayoutParams().apply {
            width = 0
            height = 104.dp()
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
            setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp())
        }
    }

    private fun createWorkPhotoTile(
        photo: MasterWorkPhotoResponse,
        index: Int,
        allPhotos: List<MasterWorkPhotoResponse>,
        canDelete: Boolean,
    ): View {
        val frame = FrameLayout(this).apply {
            layoutParams = workTileLayoutParams()
            background = ContextCompat.getDrawable(this@ProfileActivity, R.drawable.bg_photo_grid_cell)
            isClickable = true
            isFocusable = true
            foreground = ContextCompat.getDrawable(
                this@ProfileActivity,
                androidx.appcompat.R.drawable.abc_list_selector_holo_light,
            )
        }

        val image = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = getString(R.string.master_photo_slot_desc)
            load(photo.photoUrl) { crossfade(true) }
        }
        frame.addView(image)

        frame.setOnClickListener { openGalleryAt(allPhotos, index) }
        frame.setOnLongClickListener {
            val id = photo.id ?: 0
            if (canDelete && id > 0) {
                confirmDeleteWorkPhoto(id)
            } else {
                openGalleryAt(allPhotos, index)
            }
            true
        }
        return frame
    }

    private fun createAddPhotoTile(): View {
        val frame = FrameLayout(this).apply {
            layoutParams = workTileLayoutParams()
            background = ContextCompat.getDrawable(this@ProfileActivity, R.drawable.bg_photo_add_empty)
            isClickable = true
            isFocusable = true
            foreground = ContextCompat.getDrawable(
                this@ProfileActivity,
                androidx.appcompat.R.drawable.abc_list_selector_holo_light,
            )
            contentDescription = getString(R.string.master_add_photo_desc)
            setOnClickListener { launchWorkPhotoPicker() }
        }
        val plus = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                44.dp(),
                44.dp(),
                Gravity.CENTER,
            )
            background = ContextCompat.getDrawable(this@ProfileActivity, R.drawable.bg_peach_circle)
            setPadding(10.dp())
            setImageResource(R.drawable.ic_plus_white)
            contentDescription = getString(R.string.master_add_photo_desc)
        }
        frame.addView(plus)
        return frame
    }

    private fun openGalleryAt(photos: List<MasterWorkPhotoResponse>, startIndex: Int) {
        if (photos.isEmpty()) return
        val urls = photos.map { it.photoUrl }
        val ids = photos.map { it.id ?: 0 }
        val isOwner = session.isMaster() && session.getMasterDraft().masterId != null
        val intent = PhotoGalleryActivity.newIntent(
            context = this,
            urls = urls,
            ids = ids,
            startIndex = startIndex,
            canDelete = isOwner,
        )
        galleryLauncher.launch(intent)
    }

    private fun launchWorkPhotoPicker() {
        val token = session.getToken()
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Please sign in again", Toast.LENGTH_SHORT).show()
            return
        }
        if (!session.isMaster() || session.getMasterDraft().masterId == null) {
            Toast.makeText(this, "Create your master profile first", Toast.LENGTH_SHORT).show()
            return
        }
        if (workPhotos.size >= MAX_WORK_PHOTOS) {
            Toast.makeText(this, R.string.master_photos_limit_reached, Toast.LENGTH_LONG).show()
            return
        }
        pickWorkPhotoLauncher.launch("image/*")
    }

    private fun uploadWorkPhoto(uri: Uri) {
        val token = session.getToken() ?: return
        lifecycleScope.launch {
            try {
                val mimeType = resolveImageMimeType(uri)
                val file = copyUriToCacheFile(uri, mimeType)
                val part = MultipartBody.Part.createFormData(
                    "photo",
                    file.name,
                    file.asRequestBody(mimeType.toMediaTypeOrNull()),
                )
                val uploaded = masterRepository.uploadMyWorkPhoto(token, part)
                workPhotos = listOf(uploaded) + workPhotos
                populateUserData()
                Toast.makeText(
                    this@ProfileActivity,
                    R.string.master_photo_uploaded,
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ProfileActivity,
                    e.message ?: getString(R.string.master_photo_upload_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun confirmDeleteWorkPhoto(photoId: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.gallery_delete_title)
            .setMessage(R.string.gallery_delete_message)
            .setPositiveButton(R.string.gallery_delete_confirm) { _, _ -> deleteWorkPhoto(photoId) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteWorkPhoto(photoId: Int) {
        val token = session.getToken() ?: return
        lifecycleScope.launch {
            try {
                masterRepository.deleteMyWorkPhoto(token, photoId)
                workPhotos = workPhotos.filter { it.id != photoId }
                populateUserData()
                Toast.makeText(
                    this@ProfileActivity,
                    R.string.master_photo_deleted,
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ProfileActivity,
                    e.message ?: getString(R.string.load_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun renderSchedule(draft: MasterProfileDraft) {
        binding.tvScheduleWeekLabel.text = MasterScheduleUi.weekRangeLabel(scheduleWeekOffset)
        binding.btnSchedulePrev.isEnabled = scheduleWeekOffset > 0
        binding.btnSchedulePrev.alpha = if (scheduleWeekOffset > 0) 1f else 0.35f
        binding.btnScheduleNext.isEnabled = scheduleWeekOffset < 3
        binding.btnScheduleNext.alpha = if (scheduleWeekOffset < 3) 1f else 0.35f
        val overlays = MasterScheduleUi.buildOverlays(
            masterBookings, scheduleWeekOffset, session.getUserId(),
        )
        MasterScheduleUi.populateGrid(
            binding.layoutScheduleGrid,
            scheduleWeekOffset,
            normalizeScheduleWeeks(draft.scheduleWeeks),
            overlays = overlays,
            onBookingClick = { bookingId -> openBookingInfoDialog(bookingId) },
        )
    }

    private fun openBookingInfoDialog(bookingId: Int) {
        val booking = masterBookings.firstOrNull { it.id == bookingId } ?: return
        val bindingDialog = DialogBookingInfoBinding.inflate(layoutInflater)

        val clientName = booking.clientName.ifBlank { "Client" }
        bindingDialog.tvBookingClientName.text = clientName
        if (booking.clientPhone.isNotBlank()) {
            bindingDialog.tvBookingClientPhone.visibility = View.VISIBLE
            bindingDialog.tvBookingClientPhone.text = booking.clientPhone
        } else {
            bindingDialog.tvBookingClientPhone.visibility = View.GONE
        }
        if (booking.clientAvatar.isNotBlank()) {
            bindingDialog.ivBookingClientAvatar.imageTintList = null
            bindingDialog.ivBookingClientAvatar.load(booking.clientAvatar) {
                crossfade(true)
                placeholder(R.drawable.ic_nav_profile)
                error(R.drawable.ic_nav_profile)
            }
        } else {
            bindingDialog.ivBookingClientAvatar.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.text_hint),
            )
            bindingDialog.ivBookingClientAvatar.setImageResource(R.drawable.ic_nav_profile)
        }

        val duration = booking.serviceDurationMinutes.coerceAtLeast(0)
        bindingDialog.tvBookingInfoService.text = getString(
            R.string.booking_info_service_format,
            booking.serviceName.ifBlank { "—" },
            duration,
        )
        bindingDialog.tvBookingInfoWhen.text = getString(
            R.string.booking_info_when_format,
            booking.appointmentDate.toDisplayDate(),
            booking.startTime.shortTime(),
            booking.endTime.shortTime(),
        )
        bindingDialog.tvBookingInfoStatus.text = getString(
            R.string.booking_info_status_format,
            booking.status.ifBlank { "—" },
        )
        if (booking.notes.isNotBlank()) {
            bindingDialog.tvBookingInfoNotesLabel.visibility = View.VISIBLE
            bindingDialog.tvBookingInfoNotes.visibility = View.VISIBLE
            bindingDialog.tvBookingInfoNotes.text = booking.notes
        } else {
            bindingDialog.tvBookingInfoNotesLabel.visibility = View.GONE
            bindingDialog.tvBookingInfoNotes.visibility = View.GONE
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.booking_info_title)
            .setView(bindingDialog.root)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        bindingDialog.btnBookingInfoMessage.setOnClickListener {
            dialog.dismiss()
            openChatWithClient(booking)
        }
        bindingDialog.btnBookingInfoCancel.setOnClickListener {
            dialog.dismiss()
            openCancelReasonDialog(booking)
        }
        dialog.show()
    }

    private fun openChatWithClient(booking: BookingResponse) {
        val token = session.getToken()
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Please sign in again", Toast.LENGTH_SHORT).show()
            return
        }
        val clientUserId = booking.client
        if (clientUserId <= 0) {
            Toast.makeText(this, R.string.master_detail_message_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                val conversation = chatRepository.startConversation(token, clientUserId)
                val intent = Intent(this@ProfileActivity, ChatConversationActivity::class.java).apply {
                    putExtra(ChatConversationActivity.EXTRA_CONVERSATION_ID, conversation.id)
                    putExtra(
                        ChatConversationActivity.EXTRA_PARTICIPANT_NAME,
                        conversation.participant?.displayName ?: booking.clientName,
                    )
                    putExtra(
                        ChatConversationActivity.EXTRA_PARTICIPANT_AVATAR,
                        conversation.participant?.avatar ?: booking.clientAvatar,
                    )
                    putExtra(
                        ChatConversationActivity.EXTRA_IS_ONLINE,
                        conversation.participant?.isOnline ?: false,
                    )
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(
                    this@ProfileActivity,
                    e.message ?: getString(R.string.master_detail_message_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun openCancelReasonDialog(booking: BookingResponse) {
        val token = session.getToken()
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Please sign in again", Toast.LENGTH_SHORT).show()
            return
        }
        val bind = DialogBookingCancelReasonBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.booking_cancel_title)
            .setView(bind.root)
            .setNegativeButton(R.string.booking_cancel_keep, null)
            .setPositiveButton(R.string.booking_cancel_confirm, null)
            .create()

        dialog.setOnShowListener {
            val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                val reason = bind.etBookingCancelReason.text.toString().trim()
                btn.isEnabled = false
                lifecycleScope.launch {
                    try {
                        bookingRepository.cancelBooking(token, booking.id, reason)
                        val draft = session.getMasterDraft()
                        draft.masterId?.let { masterBookings = loadMasterBookingsRange(it) }
                        populateUserData()
                        dialog.dismiss()
                        Toast.makeText(
                            this@ProfileActivity,
                            R.string.booking_cancel_success,
                            Toast.LENGTH_LONG,
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@ProfileActivity,
                            e.message ?: getString(R.string.load_failed),
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

    private suspend fun loadMasterBookingsRange(masterId: Int): List<BookingResponse> {
        return runCatching {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val dow = when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
                java.util.Calendar.MONDAY -> 0; java.util.Calendar.TUESDAY -> 1
                java.util.Calendar.WEDNESDAY -> 2; java.util.Calendar.THURSDAY -> 3
                java.util.Calendar.FRIDAY -> 4; java.util.Calendar.SATURDAY -> 5
                java.util.Calendar.SUNDAY -> 6; else -> 0
            }
            cal.add(java.util.Calendar.DAY_OF_MONTH, -dow)
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val from = fmt.format(cal.time)
            cal.add(java.util.Calendar.DAY_OF_MONTH, MasterScheduleData.WEEK_COUNT * 7 - 1)
            val to = fmt.format(cal.time)
            bookingRepository.getMasterBookings(masterId, from, to)
        }.getOrDefault(emptyList())
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
                val updatedDraft = draft.copy(scheduleWeeks = normalized)
                val token = session.getToken()
                if (token.isNullOrBlank()) {
                    Toast.makeText(this, "Please sign in again", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                lifecycleScope.launch {
                    try {
                        masterRepository.upsertWeekTimetablesFromGrid(token, normalized)
                        masterRepository.updateMyMasterProfile(token, updatedDraft.toRequestWithScheduleFromGrid())
                        val master = masterRepository.getMyMasterProfile(token)
                        session.saveMasterProfile(master)
                        session.saveMasterDraft(master.toDraft())
                        populateUserData()
                        dialog.dismiss()
                        Toast.makeText(this@ProfileActivity, R.string.schedule_saved_remote, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@ProfileActivity,
                            e.message ?: getString(R.string.load_failed),
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

    private fun openUserEditDialog() {
        val token = session.getToken()
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Please sign in again", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogUserProfileEditBinding.inflate(layoutInflater)
        dialogBinding.etFullName.setText(session.getDisplayName())
        dialogBinding.etPhone.setText(session.getPhoneNumber())

        dialogBinding.btnUploadAvatar.setOnClickListener {
            pendingAvatarEditText = null
            pickImageLauncher.launch("image/*")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit profile")
            .setView(dialogBinding.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialogBinding.btnSwitchToProfessional.setOnClickListener {
            confirmSwitchToProfessional(token) { dialog.dismiss() }
        }
        dialogBinding.btnDeleteProfile.setOnClickListener {
            confirmDeleteAccount(token) { dialog.dismiss() }
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val fullName = dialogBinding.etFullName.text.toString().trim()
                val phone = dialogBinding.etPhone.text.toString().trim()
                val avatar = session.getAvatarUrl().orEmpty()

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
            pendingAvatarEditText = null
            pickImageLauncher.launch("image/*")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existingDraft.masterId == null) "Create master profile" else "Edit master profile")
            .setView(dialogBinding.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialogBinding.btnDeleteMasterProfile.setOnClickListener {
            confirmDeleteAccount(token) { dialog.dismiss() }
        }

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
        dialogBinding.etMasterWorkPhotoCaption.setText(draft.workPhotoCaption)
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
        val base = session.getMasterDraft()
        val profilePhoto = session.getAvatarUrl().orEmpty().ifBlank { base.profilePhoto }
        val workPhotoUrl = base.workPhotoUrl
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

        val workPhotoUrls = if (workPhotoUrl.isNotBlank()) listOf(workPhotoUrl) else base.workPhotoUrls
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
            mondayHours = base.mondayHours,
            tuesdayHours = base.tuesdayHours,
            wednesdayHours = base.wednesdayHours,
            thursdayHours = base.thursdayHours,
            fridayHours = base.fridayHours,
            saturdayHours = base.saturdayHours,
            sundayHours = base.sundayHours,
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

    /** Updates [MasterProfileRequest] day-hour strings from week 0 of [scheduleWeeks] (for `/api/masters/me/`). */
    private fun MasterProfileDraft.toRequestWithScheduleFromGrid(): MasterProfileRequest {
        val base = toRequest()
        val w0 = normalizeScheduleWeeks(scheduleWeeks).getOrNull(0) ?: return base
        return base.copy(
            mondayHours = MasterScheduleFormat.daySlotsToString(w0.getOrNull(0).orEmpty()),
            tuesdayHours = MasterScheduleFormat.daySlotsToString(w0.getOrNull(1).orEmpty()),
            wednesdayHours = MasterScheduleFormat.daySlotsToString(w0.getOrNull(2).orEmpty()),
            thursdayHours = MasterScheduleFormat.daySlotsToString(w0.getOrNull(3).orEmpty()),
            fridayHours = MasterScheduleFormat.daySlotsToString(w0.getOrNull(4).orEmpty()),
            saturdayHours = MasterScheduleFormat.daySlotsToString(w0.getOrNull(5).orEmpty()),
            sundayHours = MasterScheduleFormat.daySlotsToString(w0.getOrNull(6).orEmpty()),
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
            scheduleWeeks = normalizeScheduleWeeks(MasterProfileSchedule.buildScheduleWeeks(this)),
            services = services.orEmpty().map { s ->
                MasterServiceItem(
                    id = s.id,
                    name = s.name,
                    price = kotlin.math.round(s.price).toInt().coerceAtLeast(0),
                    durationMinutes = s.durationMinutes,
                    requiresPrepayment = s.requiresPrepayment,
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

    private fun String.toDisplayDate(): String {
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(this)
            SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(parsed!!)
        } catch (_: Exception) {
            this
        }
    }

    private fun String.shortTime(): String = take(5)

    private fun confirmSwitchToProfessional(token: String, onDone: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Switch to professional account")
            .setMessage(
                "Your account will be converted to a master (professional) account. " +
                    "You will be able to create your master profile, set a schedule, and receive bookings. " +
                    "Continue?"
            )
            .setPositiveButton("Switch") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val user = authRepository.becomeMaster(token)
                        session.saveUserInfo(user)
                        session.saveIsMaster(true)
                        onDone()
                        Toast.makeText(
                            this@ProfileActivity,
                            "You are now a professional. Complete your master profile.",
                            Toast.LENGTH_LONG,
                        ).show()
                        populateUserData()
                        syncProfileFromBackend()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@ProfileActivity,
                            e.message ?: "Failed to switch account",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteAccount(token: String, onDone: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Delete profile")
            .setMessage(
                "Your account and all related data will be permanently deleted. " +
                    "This action cannot be undone. Continue?"
            )
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        authRepository.deleteCurrentUser(token)
                        session.clearSession()
                        onDone()
                        val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@ProfileActivity,
                            e.message ?: "Failed to delete profile",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
