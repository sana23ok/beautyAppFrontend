package com.example.beautyappfrontend.ui.screens

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import kotlinx.coroutines.async
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.data.repository.BookingRepository
import com.example.beautyappfrontend.data.repository.ChatRepository
import com.example.beautyappfrontend.data.repository.FavoriteMastersRepository
import com.example.beautyappfrontend.data.repository.MasterRepository
import com.example.beautyappfrontend.databinding.ActivityMasterDetailBinding
import com.example.beautyappfrontend.databinding.DialogBookingAppointmentBinding
import com.example.beautyappfrontend.domain.model.BookingRequest
import com.example.beautyappfrontend.domain.model.BookingResponse
import com.example.beautyappfrontend.domain.model.MasterProfileResponse
import com.example.beautyappfrontend.domain.model.MasterReviewItem
import com.example.beautyappfrontend.domain.model.MasterReviewsEnvelope
import com.example.beautyappfrontend.domain.model.MasterScheduleData
import com.example.beautyappfrontend.domain.model.MasterServiceResponse
import com.example.beautyappfrontend.utils.MasterProfileSchedule
import com.example.beautyappfrontend.utils.MasterScheduleUi
import com.example.beautyappfrontend.utils.SessionManager
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MasterDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMasterDetailBinding
    private lateinit var session: SessionManager
    private val repository = MasterRepository()
    private val bookingRepository = BookingRepository()
    private val chatRepository = ChatRepository()

    private var currentMaster: MasterProfileResponse? = null
    private var cachedScheduleWeeks: List<List<List<Int>>> = MasterScheduleData.empty()
    private var bookedScheduleWeeks: List<List<List<Int>>> = MasterScheduleData.empty()
    private var cachedBookings: List<BookingResponse> = emptyList()
    private var scheduleWeekOffset: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMasterDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        val masterId = intent.getIntExtra(EXTRA_MASTER_ID, -1)
        if (masterId <= 0) {
            Toast.makeText(this, R.string.master_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        fun goBack() {
            val up = NavUtils.getParentActivityIntent(this)
            if (up != null && navigateUpTo(up)) return
            finish()
        }
        binding.btnBack.setOnClickListener { goBack() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })

        binding.btnSchedulePrev.setOnClickListener {
            if (scheduleWeekOffset > 0) {
                scheduleWeekOffset--
                renderScheduleWeek()
            }
        }
        binding.btnScheduleNext.setOnClickListener {
            if (scheduleWeekOffset < MasterScheduleData.WEEK_COUNT - 1) {
                scheduleWeekOffset++
                renderScheduleWeek()
            }
        }

        binding.progress.visibility = View.VISIBLE
        binding.scrollContent.visibility = View.GONE
        loadMaster(masterId)
    }

    override fun onResume() {
        super.onResume()
        val m = currentMaster ?: return
        if (session.isLoggedIn()) {
            lifecycleScope.launch {
                FavoriteMastersRepository.sync()
                renderFavoriteIcon(FavoriteMastersRepository.isFavorite(m.id))
            }
        } else {
            FavoriteMastersRepository.clearCache()
            renderFavoriteIcon(false)
        }
    }

    private fun renderFavoriteIcon(isFav: Boolean) {
        binding.btnFavorite.setImageResource(
            if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline,
        )
        binding.btnFavorite.imageTintList = null
    }

    private fun loadMaster(masterId: Int) {
        lifecycleScope.launch {
            try {
                // Kick off the booked-slots request immediately in parallel with the
                // master profile — they hit different endpoints and both are needed
                // to render the screen, so running them concurrently shaves roughly
                // one network round-trip off the initial "loading" time.
                val bookingsDeferred = async {
                    runCatching {
                        val range = bookingRange()
                        bookingRepository.getMasterBookings(masterId, range.first, range.second)
                    }.getOrDefault(emptyList())
                }
                val reviewsDeferred = async {
                    runCatching {
                        repository.getMasterReviews(masterId, session.getToken())
                    }.getOrNull()
                }
                val master = repository.getMasterProfile(masterId)
                currentMaster = master
                if (session.isLoggedIn()) {
                    FavoriteMastersRepository.sync()
                } else {
                    FavoriteMastersRepository.clearCache()
                }
                bindMaster(master)
                // Reveal the content as soon as profile data is on screen, even
                // before the bookings overlay is ready — prevents the long blank
                // screen seen in logcat while two sequential GETs completed.
                showContent()
                applyBookedSlots(bookingsDeferred.await())
                reviewsDeferred.await()?.let { bindReviewsSection(it, masterId) }
                    ?: bindReviewsSectionFromProfile(master)
            } catch (e: Exception) {
                hideProgress()
                Toast.makeText(this@MasterDetailActivity, e.message ?: getString(R.string.load_failed), Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun showContent() {
        binding.progress.visibility = View.GONE
        binding.scrollContent.visibility = View.VISIBLE
    }

    private fun hideProgress() {
        binding.progress.visibility = View.GONE
    }

    private fun applyBookedSlots(bookings: List<BookingResponse>) {
        cachedBookings = bookings
        bookedScheduleWeeks = buildBookedWeeks(bookings)
        renderScheduleWeek()
    }

    private suspend fun refreshBookedSlots(masterId: Int) {
        val bookings = runCatching {
            val range = bookingRange()
            bookingRepository.getMasterBookings(masterId, range.first, range.second)
        }.getOrDefault(emptyList())
        applyBookedSlots(bookings)
    }

    private fun bindMaster(m: MasterProfileResponse) {
        bindMasterHeader(m)
        bindMasterPhoto(m)
        bindPriceSection(m)
        bindWorkPhotosSection(m)
        bindMessageButton(m)
        bindFavoriteButton(m)
        bindInitialSchedule(m)
    }

    private fun bindFavoriteButton(m: MasterProfileResponse) {
        renderFavoriteIcon(FavoriteMastersRepository.isFavorite(m.id))
        binding.btnFavorite.setOnClickListener {
            if (!session.isLoggedIn()) {
                Toast.makeText(this, "Please log in to save favorites", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                FavoriteMastersRepository.toggle(m.id)
                    .onSuccess { isFav -> renderFavoriteIcon(isFav) }
                    .onFailure { e ->
                        Toast.makeText(
                            this@MasterDetailActivity,
                            e.message ?: "Could not update favorites",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
            }
        }
    }

    private fun bindMasterHeader(m: MasterProfileResponse) {
        binding.tvName.text = m.name.ifBlank { "—" }
        binding.tvSpecialization.text = m.specialization.ifBlank { "—" }
        updateReviewSummary(m.reviewCount, m.reviewsAverage)
        val loc = listOf(m.city, m.address).filter { it.isNotBlank() }.joinToString(", ")
        binding.tvLocation.text = loc.ifBlank { "—" }
        binding.tvDescription.text = m.description.ifBlank { getString(R.string.no_description) }
        binding.tvExperience.text = getString(R.string.experience_years_format, m.experienceYears.coerceAtLeast(0))
    }

    private fun updateReviewSummary(count: Int, average: Double?) {
        if (count <= 0) {
            binding.tvRating.text = getString(R.string.master_no_reviews)
        } else {
            val avg = average ?: 0.0
            binding.tvRating.text = getString(R.string.master_reviews_summary, avg, count)
        }
    }

    private fun bindReviewsSectionFromProfile(m: MasterProfileResponse) {
        updateReviewSummary(m.reviewCount, m.reviewsAverage)
        binding.tvReviewsSummary.visibility = if (m.reviewCount <= 0) View.GONE else View.VISIBLE
        binding.tvReviewsSummary.text = getString(R.string.master_reviews_summary, m.reviewsAverage ?: 0.0, m.reviewCount)
        binding.layoutReviewsList.removeAllViews()
        binding.tvReviewsEmpty.visibility = View.GONE
        binding.cardWriteReview.visibility = View.GONE
        binding.tvReviewEligibility.visibility = View.GONE
    }

    private fun bindReviewsSection(envelope: MasterReviewsEnvelope, masterId: Int) {
        val m = currentMaster ?: return
        updateReviewSummary(envelope.count, envelope.average)
        binding.tvReviewsSummary.visibility = if (envelope.count <= 0) View.GONE else View.VISIBLE
        if (envelope.count > 0) {
            binding.tvReviewsSummary.text = getString(
                R.string.master_reviews_summary,
                envelope.average ?: 0.0,
                envelope.count,
            )
        }

        val own = isOwnMasterProfile(m)
        binding.cardWriteReview.visibility = View.GONE
        binding.tvReviewEligibility.visibility = View.GONE

        if (!own && (envelope.canReview || envelope.yourReview != null)) {
            binding.cardWriteReview.visibility = View.VISIBLE
            val prefill = envelope.yourReview
            binding.ratingBarReview.rating = (prefill?.rating ?: 5).toFloat().coerceIn(1f, 5f)
            binding.etReviewComment.setText(prefill?.comment.orEmpty())
            binding.btnSubmitReview.setOnClickListener { submitReview(masterId) }
        } else if (!own && session.getToken().isNullOrBlank()) {
            binding.tvReviewEligibility.visibility = View.VISIBLE
            binding.tvReviewEligibility.text = getString(R.string.review_eligibility_login)
        } else if (!own && !envelope.canReview && envelope.yourReview == null) {
            binding.tvReviewEligibility.visibility = View.VISIBLE
            binding.tvReviewEligibility.text = getString(R.string.review_eligibility_wait)
        }

        binding.layoutReviewsList.removeAllViews()
        binding.tvReviewsEmpty.visibility = View.GONE
        envelope.results.forEach { item ->
            binding.layoutReviewsList.addView(createReviewCard(item))
        }
    }

    private fun createReviewCard(item: MasterReviewItem): View {
        val card = layoutInflater.inflate(R.layout.item_master_review, binding.layoutReviewsList, false)
        val ivAvatar = card.findViewById<ImageView>(R.id.iv_review_avatar)
        val tvAuthor = card.findViewById<TextView>(R.id.tv_review_author)
        val tvDate = card.findViewById<TextView>(R.id.tv_review_date)
        val ratingRow = card.findViewById<android.widget.RatingBar>(R.id.rating_bar_review_row)
        val tvComment = card.findViewById<TextView>(R.id.tv_review_comment)
        val tvMore = card.findViewById<TextView>(R.id.tv_review_read_more)

        val name = item.authorName.ifBlank { "—" }
        tvAuthor.text = buildReviewAuthorLabel(name, item.isVerified)
        tvDate.text = formatReviewDate(item.createdAt)
        ratingRow.rating = item.rating.toFloat().coerceIn(0f, 5f)

        if (item.authorAvatar.isNotBlank()) {
            ivAvatar.load(item.authorAvatar) {
                crossfade(true)
                placeholder(R.drawable.ic_nav_profile)
                error(R.drawable.ic_nav_profile)
            }
        } else {
            ivAvatar.setImageResource(R.drawable.ic_nav_profile)
        }

        val comment = item.comment.trim()
        tvComment.text = comment.ifBlank { "—" }
        if (comment.length > 160) {
            tvComment.maxLines = 3
            tvComment.ellipsize = android.text.TextUtils.TruncateAt.END
            tvMore.visibility = View.VISIBLE
            var expanded = false
            tvMore.setOnClickListener {
                expanded = !expanded
                tvComment.maxLines = if (expanded) Int.MAX_VALUE else 3
                tvMore.text = getString(if (expanded) R.string.review_read_less else R.string.review_read_more)
            }
        } else {
            tvComment.maxLines = Int.MAX_VALUE
            tvMore.visibility = View.GONE
        }
        return card
    }

    private fun buildReviewAuthorLabel(name: String, verified: Boolean): CharSequence {
        val nameColor = ContextCompat.getColor(this, R.color.text_primary)
        if (!verified) {
            val s = SpannableString(name)
            s.setSpan(StyleSpan(Typeface.BOLD), 0, name.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(ForegroundColorSpan(nameColor), 0, name.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            return s
        }
        val dot = " · "
        val suffix = getString(R.string.review_verified)
        val full = name + dot + suffix
        val ss = SpannableString(full)
        val mutedGreen = ContextCompat.getColor(this, R.color.text_secondary)
        ss.setSpan(StyleSpan(Typeface.BOLD), 0, name.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        ss.setSpan(ForegroundColorSpan(nameColor), 0, name.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val fromDot = name.length
        ss.setSpan(ForegroundColorSpan(mutedGreen), fromDot, full.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        ss.setSpan(StyleSpan(Typeface.NORMAL), fromDot, full.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return ss
    }

    private fun formatReviewDate(iso: String): String {
        if (iso.isBlank()) return ""
        val outPattern = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault())
        return try {
            val odt = OffsetDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            odt.format(outPattern)
        } catch (_: Exception) {
            try {
                LocalDate.parse(iso.take(10), DateTimeFormatter.ISO_LOCAL_DATE).format(outPattern)
            } catch (_: Exception) {
                iso
            }
        }
    }

    private fun submitReview(masterId: Int) {
        val token = session.getToken()
        if (token.isNullOrBlank()) {
            Toast.makeText(this, R.string.review_eligibility_login, Toast.LENGTH_SHORT).show()
            return
        }
        val stars = binding.ratingBarReview.rating.roundToInt().coerceIn(1, 5)
        if (stars < 1) {
            Toast.makeText(this, R.string.review_select_stars, Toast.LENGTH_SHORT).show()
            return
        }
        val comment = binding.etReviewComment.text?.toString().orEmpty()
        binding.btnSubmitReview.isEnabled = false
        lifecycleScope.launch {
            try {
                val env = repository.postMasterReview(masterId, token, stars, comment)
                bindReviewsSection(env, masterId)
                Toast.makeText(this@MasterDetailActivity, R.string.review_posted, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MasterDetailActivity, e.message ?: getString(R.string.load_failed), Toast.LENGTH_LONG).show()
            } finally {
                binding.btnSubmitReview.isEnabled = true
            }
        }
    }

    private fun bindMasterPhoto(m: MasterProfileResponse) {
        if (m.profilePhoto.isNotBlank()) {
            binding.ivPhoto.load(m.profilePhoto) {
                crossfade(true)
                placeholder(R.drawable.ic_nav_profile)
                error(R.drawable.ic_nav_profile)
            }
        } else {
            binding.ivPhoto.setImageResource(R.drawable.ic_nav_profile)
        }
    }

    private fun bindInitialSchedule(m: MasterProfileResponse) {
        cachedScheduleWeeks = MasterProfileSchedule.buildScheduleWeeks(m)
        scheduleWeekOffset = 0
        renderScheduleWeek()
    }

    private fun bindWorkPhotosSection(m: MasterProfileResponse) {
        val photos = m.workPhotos.orEmpty().filter { it.photoUrl.isNotBlank() }
        val grid = binding.layoutWorkPhotosGrid
        grid.removeAllViews()

        if (photos.isEmpty()) {
            binding.tvWorkPhotosTitle.visibility = View.GONE
            grid.visibility = View.GONE
            return
        }

        binding.tvWorkPhotosTitle.visibility = View.VISIBLE
        grid.visibility = View.VISIBLE

        val urls = photos.map { it.photoUrl }
        val ids = photos.map { it.id ?: 0 }

        val overflow = photos.size > MAX_GRID_TILES
        // When there are more than 9 photos, slot 9 becomes a plain "See more" tile
        // that opens the fullscreen gallery at that 9th photo (index MAX_GRID_TILES - 1).
        val normalTileCount =
            if (overflow) MAX_GRID_TILES - 1 else photos.size.coerceAtMost(MAX_GRID_TILES)

        for (i in 0 until normalTileCount) {
            grid.addView(createPhotoTile(photos[i].photoUrl, i, urls, ids))
        }

        if (overflow) {
            grid.addView(
                createSeeMoreTile(
                    startIndex = MAX_GRID_TILES - 1,
                    urls = urls,
                    ids = ids,
                ),
            )
        }
    }

    private fun bindMessageButton(m: MasterProfileResponse) {
        // Hide the button on the user's own master profile – no self-messaging.
        val isOwn = isOwnMasterProfile(m)
        val hasParticipant = (m.userId ?: 0) > 0
        if (isOwn || !hasParticipant) {
            binding.btnMessage.visibility = View.GONE
            binding.btnMessage.setOnClickListener(null)
            return
        }
        binding.btnMessage.visibility = View.VISIBLE
        binding.btnMessage.setOnClickListener { startConversationWith(m) }
    }

    private fun startConversationWith(m: MasterProfileResponse) {
        val token = session.getToken()
        if (token.isNullOrBlank()) {
            Toast.makeText(this, R.string.master_detail_message_login_required, Toast.LENGTH_SHORT).show()
            return
        }
        val participantUserId = m.userId
        if (participantUserId == null || participantUserId <= 0) {
            Toast.makeText(this, R.string.master_detail_message_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnMessage.isEnabled = false
        Toast.makeText(this, R.string.master_detail_message_starting, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val conversation = chatRepository.startConversation(
                    token = token,
                    participantId = participantUserId,
                )
                val intent = Intent(this@MasterDetailActivity, ChatConversationActivity::class.java).apply {
                    putExtra(ChatConversationActivity.EXTRA_CONVERSATION_ID, conversation.id)
                    putExtra(
                        ChatConversationActivity.EXTRA_PARTICIPANT_NAME,
                        conversation.participant?.displayName ?: m.name,
                    )
                    putExtra(
                        ChatConversationActivity.EXTRA_PARTICIPANT_AVATAR,
                        conversation.participant?.avatar ?: m.profilePhoto,
                    )
                    putExtra(
                        ChatConversationActivity.EXTRA_IS_ONLINE,
                        conversation.participant?.isOnline ?: false,
                    )
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(
                    this@MasterDetailActivity,
                    e.message ?: getString(R.string.master_detail_message_failed),
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                binding.btnMessage.isEnabled = true
            }
        }
    }

    private fun photoTileLayoutParams(): GridLayout.LayoutParams {
        return GridLayout.LayoutParams().apply {
            width = 0
            height = 104.dp()
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
            setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp())
        }
    }

    private fun createPhotoTile(
        photoUrl: String,
        index: Int,
        urls: List<String>,
        ids: List<Int>,
    ): View {
        val frame = FrameLayout(this).apply {
            layoutParams = photoTileLayoutParams()
            background = ContextCompat.getDrawable(this@MasterDetailActivity, R.drawable.bg_photo_grid_cell)
            isClickable = true
            isFocusable = true
            foreground = ContextCompat.getDrawable(
                this@MasterDetailActivity,
                androidx.appcompat.R.drawable.abc_list_selector_holo_light,
            )
            setOnClickListener { openGallery(urls, ids, index) }
        }
        val image = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = getString(R.string.master_photo_slot_desc)
            load(photoUrl) { crossfade(true) }
        }
        frame.addView(image)
        return frame
    }

    /** Slot 9 when the master has more than 9 photos — a plain "See more" tile that
     *  opens the fullscreen gallery at the overflow position. No photo/blur here to
     *  keep the grid lightweight. */
    private fun createSeeMoreTile(
        startIndex: Int,
        urls: List<String>,
        ids: List<Int>,
    ): View {
        val frame = FrameLayout(this).apply {
            layoutParams = photoTileLayoutParams()
            background = ContextCompat.getDrawable(this@MasterDetailActivity, R.drawable.bg_photo_grid_cell)
            isClickable = true
            isFocusable = true
            foreground = ContextCompat.getDrawable(
                this@MasterDetailActivity,
                androidx.appcompat.R.drawable.abc_list_selector_holo_light,
            )
            contentDescription = getString(R.string.master_photos_see_more)
            setOnClickListener { openGallery(urls, ids, startIndex) }
        }

        val label = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            )
            text = getString(R.string.master_photos_see_more)
            setTextColor(ContextCompat.getColor(this@MasterDetailActivity, android.R.color.white))
            textSize = 13f
            background = ContextCompat.getDrawable(this@MasterDetailActivity, R.drawable.bg_see_more_pill)
            setPadding(14.dp(), 6.dp(), 14.dp(), 6.dp())
        }
        frame.addView(label)

        return frame
    }

    private fun openGallery(urls: List<String>, ids: List<Int>, startIndex: Int) {
        val intent = PhotoGalleryActivity.newIntent(
            context = this,
            urls = urls,
            ids = ids,
            startIndex = startIndex,
            canDelete = false,
        )
        startActivity(intent)
    }

    private fun renderScheduleWeek() {
        binding.tvScheduleWeekLabel.text = MasterScheduleUi.weekRangeLabel(scheduleWeekOffset)
        binding.btnSchedulePrev.isEnabled = scheduleWeekOffset > 0
        binding.btnSchedulePrev.alpha = if (scheduleWeekOffset > 0) 1f else 0.35f
        binding.btnScheduleNext.isEnabled = scheduleWeekOffset < MasterScheduleData.WEEK_COUNT - 1
        binding.btnScheduleNext.alpha = if (scheduleWeekOffset < MasterScheduleData.WEEK_COUNT - 1) 1f else 0.35f
        val overlays = MasterScheduleUi.buildOverlays(
            cachedBookings, scheduleWeekOffset, session.getUserId(),
        )
        MasterScheduleUi.populateGrid(
            binding.layoutScheduleGrid,
            scheduleWeekOffset,
            cachedScheduleWeeks,
            bookedScheduleWeeks,
            onDayClick = { dayIndex -> openBookingDialog(dayIndex) },
            overlays = overlays,
        )
    }

    private fun bindPriceSection(m: MasterProfileResponse) {
        val services = m.services.orEmpty().filter { it.name.isNotBlank() || it.price > 0.0 || it.durationMinutes > 0 }
        binding.layoutPriceRows.removeAllViews()
        if (services.isEmpty()) {
            binding.tvPriceTitle.visibility = View.GONE
            binding.cardPriceList.visibility = View.GONE
            return
        }
        binding.tvPriceTitle.visibility = View.VISIBLE
        binding.cardPriceList.visibility = View.VISIBLE
        binding.tvPriceEmpty.visibility = View.GONE
        services.forEachIndexed { index, svc ->
            binding.layoutPriceRows.addView(createPriceRow(svc))
            if (index < services.lastIndex) {
                binding.layoutPriceRows.addView(
                    View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1.dp(),
                        ).apply { topMargin = 6.dp() }
                        setBackgroundResource(R.color.divider_color)
                    },
                )
            }
        }
    }

    private fun openBookingDialog(dayIndex: Int) {
        val master = currentMaster ?: return
        val token = session.getToken()
        if (token.isNullOrBlank()) {
            Toast.makeText(this, R.string.booking_login_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (isOwnMasterProfile(master)) {
            Toast.makeText(this, "You cannot book your own master profile", Toast.LENGTH_SHORT).show()
            return
        }
        val bookingDate = dateKeyForDay(scheduleWeekOffset, dayIndex)
        if (isDateBeforeToday(bookingDate)) {
            Toast.makeText(this, R.string.booking_time_in_past, Toast.LENGTH_SHORT).show()
            return
        }
        val workingHours = cachedScheduleWeeks.getOrNull(scheduleWeekOffset)?.getOrNull(dayIndex).orEmpty()
        if (workingHours.isEmpty()) {
            Toast.makeText(this, R.string.booking_day_closed, Toast.LENGTH_SHORT).show()
            return
        }

        val services = master.services.orEmpty().filter { !it.name.isNullOrBlank() && it.durationMinutes > 0 && it.id != null }
        if (services.isEmpty()) {
            Toast.makeText(this, R.string.booking_services_missing, Toast.LENGTH_SHORT).show()
            return
        }

        val bindingDialog = DialogBookingAppointmentBinding.inflate(layoutInflater)
        bindingDialog.tvBookingDate.text = bookingDate.toDisplayDate()

        val serviceLabels = services.map { svc ->
            buildString {
                append(svc.name)
                append(" · ")
                append(svc.durationMinutes)
                append(" min · ")
                append(formatPrice(svc.price))
                if (svc.requiresPrepayment) {
                    append(" · ")
                    append(getString(R.string.master_detail_prepayment_badge))
                }
            }
        }
        bindingDialog.spinnerBookingService.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, serviceLabels)

        var selectedService: MasterServiceResponse? = null
        var selectedTime: String? = null

        fun renderTimes(service: MasterServiceResponse, slots: List<String>) {
            selectedTime = null
            bindingDialog.groupBookingTimes.removeAllViews()
            bindingDialog.tvBookingTimeHint.text =
                if (slots.isEmpty()) getString(R.string.booking_no_slots) else getString(R.string.booking_time_label)

            slots.forEach { slot ->
                val slotIsPast = isDateTimeBeforeNow(bookingDate, slot)
                bindingDialog.groupBookingTimes.addView(
                    RadioButton(this).apply {
                        text = getString(R.string.booking_time_option, slot, service.durationMinutes)
                        setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                        if (slotIsPast) alpha = 0.5f
                        setOnCheckedChangeListener { buttonView, isChecked ->
                            if (!isChecked) return@setOnCheckedChangeListener
                            if (slotIsPast) {
                                selectedTime = null
                                Toast.makeText(
                                    this@MasterDetailActivity,
                                    R.string.booking_time_in_past,
                                    Toast.LENGTH_SHORT,
                                ).show()
                                buttonView.post { bindingDialog.groupBookingTimes.clearCheck() }
                            } else {
                                selectedTime = slot
                            }
                        }
                    },
                )
            }
        }

        fun loadSlots(service: MasterServiceResponse) {
            bindingDialog.tvBookingTimeHint.text = getString(R.string.load_failed)
            bindingDialog.groupBookingTimes.removeAllViews()
            lifecycleScope.launch {
                try {
                    val slots = bookingRepository.getAvailableSlots(master.id, service.id ?: 0, bookingDate)
                    renderTimes(service, slots.slots)
                } catch (e: Exception) {
                    bindingDialog.tvBookingTimeHint.text = e.message ?: getString(R.string.load_failed)
                }
            }
        }

        bindingDialog.spinnerBookingService.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val svc = services[position]
                selectedService = svc
                bindingDialog.tvBookingPrepaymentNotice.visibility =
                    if (svc.requiresPrepayment) View.VISIBLE else View.GONE
                loadSlots(svc)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.booking_dialog_title)
            .setView(bindingDialog.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.booking_dialog_title, null)
            .create()

        dialog.setOnShowListener {
            selectedService = services.firstOrNull()
            bindingDialog.tvBookingPrepaymentNotice.visibility =
                if (services.firstOrNull()?.requiresPrepayment == true) View.VISIBLE else View.GONE
            selectedService?.let { loadSlots(it) }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = getString(R.string.booking_dialog_title)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val service = selectedService
                if (service?.id == null) {
                    Toast.makeText(this, R.string.booking_services_missing, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val time = selectedTime
                if (time.isNullOrBlank()) {
                    Toast.makeText(this, R.string.booking_select_time, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                lifecycleScope.launch {
                    try {
                        bookingRepository.createBooking(
                            token,
                            BookingRequest(
                                masterId = master.id,
                                serviceId = service.id,
                                appointmentDate = bookingDate,
                                startTime = time,
                                notes = bindingDialog.etBookingNotes.text.toString().trim(),
                            ),
                        )
                        refreshBookedSlots(master.id)
                        dialog.dismiss()
                        Toast.makeText(this@MasterDetailActivity, R.string.booking_success, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@MasterDetailActivity, e.message ?: getString(R.string.load_failed), Toast.LENGTH_LONG).show()
                    } finally {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                    }
                }
            }
        }
        dialog.show()
    }

    private fun buildBookedWeeks(bookings: List<BookingResponse>): List<List<List<Int>>> {
        val out = MutableList(MasterScheduleData.WEEK_COUNT) {
            MutableList(MasterScheduleData.DAY_COUNT) { mutableSetOf<Int>() }
        }
        bookings.forEach { booking ->
            val target = weekAndDayIndex(booking.appointmentDate) ?: return@forEach
            val day = parseDate(booking.appointmentDate) ?: return@forEach
            val start = parseTime(booking.startTime) ?: return@forEach
            val end = parseTime(booking.endTime) ?: return@forEach
            for (hour in MasterScheduleData.HOUR_START..MasterScheduleData.HOUR_END_INCLUSIVE) {
                val cellStart = Calendar.getInstance().apply {
                    time = day
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time
                val cellEnd = Calendar.getInstance().apply {
                    time = day
                    set(Calendar.HOUR_OF_DAY, hour + 1)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time
                if (start.before(cellEnd) && end.after(cellStart)) {
                    out[target.first][target.second].add(hour)
                }
            }
        }
        return out.map { week -> week.map { it.sorted() } }
    }

    private fun bookingRange(): Pair<String, String> {
        val monday = mondayCalendarForOffset(0)
        val end = mondayCalendarForOffset(MasterScheduleData.WEEK_COUNT - 1)
        end.add(Calendar.DAY_OF_MONTH, 6)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(monday.time) to fmt.format(end.time)
    }

    private fun dateKeyForDay(weekOffset: Int, dayIndex: Int): String {
        val cal = mondayCalendarForOffset(weekOffset)
        cal.add(Calendar.DAY_OF_MONTH, dayIndex)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    private fun weekAndDayIndex(dateKey: String): Pair<Int, Int>? {
        val date = parseDate(dateKey) ?: return null
        val monday0 = mondayCalendarForOffset(0).timeInMillis
        val diffDays = ((date.time - monday0) / (24L * 60 * 60 * 1000)).toInt()
        if (diffDays < 0) return null
        val week = diffDays / 7
        val day = diffDays % 7
        if (week !in 0 until MasterScheduleData.WEEK_COUNT || day !in 0 until MasterScheduleData.DAY_COUNT) return null
        return week to day
    }

    private fun mondayCalendarForOffset(weekOffset: Int): Calendar {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val daysFromMonday = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
        cal.add(Calendar.DAY_OF_MONTH, weekOffset * 7)
        return cal
    }

    private fun parseDate(value: String) = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value)
    }.getOrNull()

    private fun parseTime(value: String) = runCatching {
        val raw = value.trim()
        when {
            raw.length >= 8 -> SimpleDateFormat("HH:mm:ss", Locale.US).parse(raw.take(8))
            raw.length >= 5 -> SimpleDateFormat("HH:mm", Locale.US).parse(raw.take(5))
            else -> null
        }
    }.getOrNull()

    private fun todayMidnight(): Calendar = Calendar.getInstance(TimeZone.getDefault()).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun isDateBeforeToday(dateKey: String): Boolean {
        val date = parseDate(dateKey) ?: return false
        return date.before(todayMidnight().time)
    }

    private fun isDateTimeBeforeNow(dateKey: String, timeKey: String): Boolean {
        val date = parseDate(dateKey) ?: return false
        val time = parseTime(timeKey) ?: return false
        val timeCal = Calendar.getInstance(TimeZone.getDefault()).apply { this.time = time }
        val slotCal = Calendar.getInstance(TimeZone.getDefault()).apply {
            this.time = date
            set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return slotCal.time.before(Date())
    }

    private fun formatPrice(price: Double): String {
        return if (price % 1.0 == 0.0) "${price.toInt()} ₴" else String.format("%.2f ₴", price)
    }

    private fun String.toDisplayDate(): String {
        val date = parseDate(this) ?: return this
        return SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(date)
    }

    private fun createPriceRow(svc: MasterServiceResponse): View {
        val service = svc.name.trim().ifBlank { "—" }
        val minutes = if (svc.durationMinutes > 0) svc.durationMinutes.toString() else "—"
        val price = formatPrice(svc.price)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 8.dp() }
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                    addView(
                        TextView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            text = service
                            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                            textSize = 14f
                        },
                    )
                    addView(
                        TextView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(52.dp(), LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = 6.dp() }
                            text = minutes
                            textAlignment = View.TEXT_ALIGNMENT_CENTER
                            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                            textSize = 14f
                        },
                    )
                    addView(
                        TextView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(72.dp(), LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = 6.dp() }
                            text = price
                            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                            textSize = 14f
                        },
                    )
                },
            )
            if (svc.requiresPrepayment) {
                addView(
                    TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = 4.dp() }
                        text = getString(R.string.master_detail_prepayment_badge)
                        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                        textSize = 12f
                    },
                )
            }
        }
    }

    private fun isOwnMasterProfile(master: MasterProfileResponse): Boolean {
        if (!session.isMaster()) return false
        val ownId = session.getMasterDraft().masterId ?: return false
        return ownId == master.id
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_MASTER_ID = "master_id"

        /** Portfolio grid on the master detail page is capped at a 3×3 (9-tile) layout. */
        private const val MAX_GRID_TILES = 9
    }
}
