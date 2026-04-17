package com.example.beautyappfrontend.ui.screens

import android.os.Bundle
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
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.data.repository.BookingRepository
import com.example.beautyappfrontend.data.repository.MasterRepository
import com.example.beautyappfrontend.databinding.ActivityMasterDetailBinding
import com.example.beautyappfrontend.databinding.DialogBookingAppointmentBinding
import com.example.beautyappfrontend.domain.model.BookingRequest
import com.example.beautyappfrontend.domain.model.BookingResponse
import com.example.beautyappfrontend.domain.model.MasterProfileResponse
import com.example.beautyappfrontend.domain.model.MasterScheduleData
import com.example.beautyappfrontend.domain.model.MasterServiceResponse
import com.example.beautyappfrontend.utils.MasterProfileSchedule
import com.example.beautyappfrontend.utils.MasterScheduleUi
import com.example.beautyappfrontend.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class MasterDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMasterDetailBinding
    private lateinit var session: SessionManager
    private val repository = MasterRepository()
    private val bookingRepository = BookingRepository()

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

    private fun loadMaster(masterId: Int) {
        lifecycleScope.launch {
            try {
                val master = repository.getMasterProfile(masterId)
                currentMaster = master
                bindMaster(master)
                loadBookedSlots(master.id)
                binding.progress.visibility = View.GONE
                binding.scrollContent.visibility = View.VISIBLE
            } catch (e: Exception) {
                binding.progress.visibility = View.GONE
                Toast.makeText(this@MasterDetailActivity, e.message ?: getString(R.string.load_failed), Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private suspend fun loadBookedSlots(masterId: Int) {
        try {
            val range = bookingRange()
            val bookings = bookingRepository.getMasterBookings(masterId, range.first, range.second)
            cachedBookings = bookings
            bookedScheduleWeeks = buildBookedWeeks(bookings)
            renderScheduleWeek()
        } catch (_: Exception) {
            cachedBookings = emptyList()
            bookedScheduleWeeks = MasterScheduleData.empty()
            renderScheduleWeek()
        }
    }

    private fun bindMaster(m: MasterProfileResponse) {
        binding.tvName.text = m.name.ifBlank { "—" }
        binding.tvSpecialization.text = m.specialization.ifBlank { "—" }
        binding.tvRating.text = getString(R.string.master_rating_format, m.rating.toDouble())
        val loc = listOf(m.city, m.address).filter { it.isNotBlank() }.joinToString(", ")
        binding.tvLocation.text = loc.ifBlank { "—" }
        binding.tvDescription.text = m.description.ifBlank { getString(R.string.no_description) }
        binding.tvExperience.text = getString(R.string.experience_years_format, m.experienceYears.coerceAtLeast(0))

        if (m.profilePhoto.isNotBlank()) {
            binding.ivPhoto.load(m.profilePhoto) {
                crossfade(true)
                placeholder(R.drawable.ic_nav_profile)
                error(R.drawable.ic_nav_profile)
            }
        } else {
            binding.ivPhoto.setImageResource(R.drawable.ic_nav_profile)
        }

        bindPriceSection(m)
        bindWorkPhotosSection(m)
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

        photos.forEachIndexed { index, photo ->
            val frame = FrameLayout(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = 104.dp()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                    setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp())
                }
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
                load(photo.photoUrl) { crossfade(true) }
            }
            frame.addView(image)
            grid.addView(frame)
        }
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
            binding.layoutPriceRows.addView(createPriceRow(
                service = svc.name.trim().ifBlank { "—" },
                minutes = if (svc.durationMinutes > 0) svc.durationMinutes.toString() else "—",
                price = formatPrice(svc.price),
            ))
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
        val bookingDate = dateKeyForDay(scheduleWeekOffset, dayIndex)
        bindingDialog.tvBookingDate.text = bookingDate.toDisplayDate()

        val serviceLabels = services.map { "${it.name} · ${it.durationMinutes} min · ${formatPrice(it.price)}" }
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
                bindingDialog.groupBookingTimes.addView(
                    RadioButton(this).apply {
                        text = getString(R.string.booking_time_option, slot, service.durationMinutes)
                        setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                        setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) selectedTime = slot
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
                selectedService = services[position]
                loadSlots(services[position])
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
                        loadBookedSlots(master.id)
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

    private fun formatPrice(price: Double): String {
        return if (price % 1.0 == 0.0) "${price.toInt()} ₴" else String.format("%.2f ₴", price)
    }

    private fun String.toDisplayDate(): String {
        val date = parseDate(this) ?: return this
        return SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(date)
    }

    private fun createPriceRow(service: String, minutes: String, price: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 8.dp() }
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
    }
}
