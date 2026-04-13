package com.example.beautyappfrontend.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.domain.model.BookingResponse
import com.example.beautyappfrontend.domain.model.MasterScheduleData
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object MasterScheduleUi {

    private const val HOUR_START = 8
    private const val HOUR_END_INCLUSIVE = 19

    enum class Slot { FREE, BOOKED, CLOSED }

    data class BookingOverlay(
        val dayIndex: Int,
        val startHour: Int,
        val startMinute: Int,
        val endHour: Int,
        val endMinute: Int,
        val serviceName: String,
        val isOwnBooking: Boolean,
    )

    private val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val weekRangeFormat = SimpleDateFormat("d MMM", Locale.getDefault())
    private val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())

    fun weekRangeLabel(weekOffset: Int): String {
        val tz = TimeZone.getDefault()
        val cal = Calendar.getInstance(tz)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val daysFromMonday = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0; Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2; Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4; Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6; else -> 0
        }
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
        cal.add(Calendar.DAY_OF_MONTH, 7 * weekOffset)
        val start = cal.time
        cal.add(Calendar.DAY_OF_MONTH, 6)
        val end = cal.time
        return "${weekRangeFormat.format(start)} – ${weekRangeFormat.format(end)} ${yearFormat.format(end)}"
    }

    fun slotState(
        dayIndex: Int,
        hour: Int,
        weekOffset: Int,
        scheduleWeeks: List<List<List<Int>>> = MasterScheduleData.empty(),
        bookedWeeks: List<List<List<Int>>> = MasterScheduleData.empty(),
    ): Slot {
        val hours = scheduleWeeks.getOrNull(weekOffset)?.getOrNull(dayIndex).orEmpty()
        val booked = bookedWeeks.getOrNull(weekOffset)?.getOrNull(dayIndex).orEmpty()
        return when {
            hour in booked -> Slot.BOOKED
            hour in hours -> Slot.FREE
            else -> Slot.CLOSED
        }
    }

    /**
     * Build the booking overlays for the given week from raw [BookingResponse] list.
     * [currentUserId] is used to distinguish own bookings (light green) from others (bright pink).
     */
    fun buildOverlays(
        bookings: List<BookingResponse>,
        weekOffset: Int,
        currentUserId: Int,
    ): List<BookingOverlay> {
        val overlays = mutableListOf<BookingOverlay>()
        val mondayCal = mondayCalendar(weekOffset)
        val mondayMs = mondayCal.timeInMillis

        for (booking in bookings) {
            if (booking.status.equals("cancelled", ignoreCase = true)) continue
            val date = parseDate(booking.appointmentDate) ?: continue
            val diffDays = ((date.time - mondayMs) / (24L * 60 * 60 * 1000)).toInt()
            if (diffDays !in 0..6) continue

            val start = parseTimeHM(booking.startTime) ?: continue
            val end = parseTimeHM(booking.endTime) ?: continue

            overlays += BookingOverlay(
                dayIndex = diffDays,
                startHour = start.first,
                startMinute = start.second,
                endHour = end.first,
                endMinute = end.second,
                serviceName = booking.serviceName.ifBlank { "Booked" },
                isOwnBooking = booking.client == currentUserId,
            )
        }
        return overlays
    }

    fun populateGrid(
        container: FrameLayout,
        weekOffset: Int,
        scheduleWeeks: List<List<List<Int>>> = MasterScheduleData.empty(),
        bookedWeeks: List<List<List<Int>>> = MasterScheduleData.empty(),
        onDayClick: ((dayIndex: Int) -> Unit)? = null,
        overlays: List<BookingOverlay> = emptyList(),
    ) {
        val context = container.context
        container.removeAllViews()

        val gridLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        container.addView(gridLayout)

        fun buildAtWidth(widthPx: Int) {
            if (widthPx <= 0) return
            gridLayout.removeAllViews()
            val dm = context.resources.displayMetrics.density
            fun toPx(v: Float): Int = (v * dm).toInt()

            val padS = toPx(4f)
            val padM = toPx(6f)
            val rowMinH = toPx(44f)

            val labelColW = (widthPx * 0.15f).toInt().coerceIn(toPx(44f), toPx(80f))
            val remaining = (widthPx - labelColW).coerceAtLeast(0)
            val cellBase = remaining / 7
            val extra = remaining - cellBase * 7
            fun dayCellWidth(dayIndex: Int): Int = cellBase + if (dayIndex < extra) 1 else 0

            val rowLayoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )

            // Header row
            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = rowLayoutParams
                gravity = Gravity.CENTER_VERTICAL
            }
            headerRow.addView(
                headerCell(context, context.getString(R.string.schedule_column_hours), labelColW, rowMinH, padM),
            )
            val dayHeader = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(remaining, rowMinH)
                setBackgroundResource(R.drawable.bg_schedule_timetable_header)
            }
            for (dayIndex in 0..6) {
                val cw = dayCellWidth(dayIndex)
                val dayHasWorkingHours = scheduleWeeks.getOrNull(weekOffset)?.getOrNull(dayIndex).orEmpty().isNotEmpty()
                dayHeader.addView(
                    TextView(context).apply {
                        text = dayLabels[dayIndex]
                        gravity = Gravity.CENTER
                        textSize = 9f
                        maxLines = 1
                        setTextColor(ContextCompat.getColor(context, R.color.white))
                        setPadding(padS, padS, padS, padS)
                        layoutParams = LinearLayout.LayoutParams(cw, rowMinH)
                        if (dayHasWorkingHours && onDayClick != null) {
                            isClickable = true
                            isFocusable = true
                            setOnClickListener { onDayClick(dayIndex) }
                        }
                    },
                )
            }
            headerRow.addView(dayHeader)
            gridLayout.addView(headerRow)

            // Hour rows
            for (hour in HOUR_START..HOUR_END_INCLUSIVE) {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = rowLayoutParams
                }
                row.addView(
                    TextView(context).apply {
                        text = context.getString(R.string.schedule_hour_label, hour)
                        setPadding(padS, padM, padS, padM)
                        gravity = Gravity.CENTER
                        setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                        textSize = 11f
                        setBackgroundResource(R.drawable.bg_schedule_day_label)
                        layoutParams = LinearLayout.LayoutParams(labelColW, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            minimumHeight = rowMinH
                        }
                    },
                )
                for (dayIndex in 0..6) {
                    val slot = slotState(dayIndex, hour, weekOffset, scheduleWeeks, bookedWeeks)
                    val freeBg = if (dayIndex % 2 == 0) {
                        R.drawable.bg_schedule_slot_free
                    } else {
                        R.drawable.bg_schedule_slot_free_alt
                    }
                    val cw = dayCellWidth(dayIndex)
                    row.addView(
                        TextView(context).apply {
                            gravity = Gravity.CENTER
                            maxLines = 3
                            textSize = 7.5f
                            setPadding(padS, padS, padS, padS)
                            layoutParams = LinearLayout.LayoutParams(cw, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                minimumHeight = rowMinH
                            }
                            when (slot) {
                                Slot.FREE -> {
                                    text = ""
                                    setBackgroundResource(freeBg)
                                    if (onDayClick != null) {
                                        isClickable = true
                                        isFocusable = true
                                        setOnClickListener { onDayClick(dayIndex) }
                                    }
                                }
                                Slot.BOOKED -> {
                                    text = ""
                                    setBackgroundResource(freeBg)
                                    if (onDayClick != null) {
                                        isClickable = true
                                        isFocusable = true
                                        setOnClickListener { onDayClick(dayIndex) }
                                    }
                                }
                                Slot.CLOSED -> {
                                    text = ""
                                    background = hatchedBackground(context, cw, rowMinH)
                                }
                            }
                        },
                    )
                }
                gridLayout.addView(row)
            }

            // Add overlay rectangles after cells are measured
            if (overlays.isNotEmpty()) {
                gridLayout.post {
                    addBookingOverlays(
                        container, gridLayout, overlays,
                        labelColW, ::dayCellWidth, rowMinH,
                    )
                }
            }
        }

        fun scheduleBuild() {
            val w = container.width
            if (w > 0) {
                buildAtWidth(w)
                return
            }
            val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    val width = container.width
                    if (width > 0) {
                        container.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        buildAtWidth(width)
                    }
                }
            }
            container.viewTreeObserver.addOnGlobalLayoutListener(listener)
        }

        container.post { scheduleBuild() }
    }

    private fun addBookingOverlays(
        container: FrameLayout,
        gridLayout: LinearLayout,
        overlays: List<BookingOverlay>,
        labelColW: Int,
        dayCellWidth: (Int) -> Int,
        rowMinH: Int,
    ) {
        val context = container.context
        val dm = context.resources.displayMetrics.density
        val cornerRadius = 6f * dm

        val headerRow = gridLayout.getChildAt(0) ?: return
        val headerHeight = headerRow.height
        if (headerHeight <= 0) return

        val totalHours = HOUR_END_INCLUSIVE - HOUR_START + 1
        val rowHeights = IntArray(totalHours)
        for (i in 0 until totalHours) {
            val row = gridLayout.getChildAt(i + 1) ?: continue
            rowHeights[i] = row.height.coerceAtLeast(rowMinH)
        }

        for (overlay in overlays) {
            if (overlay.startHour < HOUR_START || overlay.startHour > HOUR_END_INCLUSIVE) continue
            if (overlay.endHour < HOUR_START) continue

            val isOwn = overlay.isOwnBooking
            val bgColor = ContextCompat.getColor(
                context,
                if (isOwn) R.color.booking_overlay_own else R.color.booking_overlay_others,
            )
            val textColor = ContextCompat.getColor(
                context,
                if (isOwn) R.color.booking_overlay_own_text else R.color.booking_overlay_others_text,
            )

            var dayX = labelColW
            for (d in 0 until overlay.dayIndex) {
                dayX += dayCellWidth(d)
            }
            val cellWidth = dayCellWidth(overlay.dayIndex)

            val startRowIndex = overlay.startHour - HOUR_START
            var topY = headerHeight
            for (r in 0 until startRowIndex) {
                topY += rowHeights[r]
            }
            val startRowH = rowHeights.getOrElse(startRowIndex) { rowMinH }
            val minuteFractionStart = overlay.startMinute / 60f
            topY += (startRowH * minuteFractionStart).toInt()

            val endRowIndex = (overlay.endHour - HOUR_START).coerceAtMost(totalHours)
            var bottomY = headerHeight
            for (r in 0 until endRowIndex) {
                bottomY += rowHeights[r]
            }
            if (overlay.endHour <= HOUR_END_INCLUSIVE) {
                val endRowH = rowHeights.getOrElse(endRowIndex) { rowMinH }
                val minuteFractionEnd = overlay.endMinute / 60f
                bottomY += (endRowH * minuteFractionEnd).toInt()
            } else {
                bottomY += rowHeights.getOrElse(totalHours - 1) { rowMinH }
            }

            val overlayHeight = (bottomY - topY).coerceAtLeast((24 * dm).toInt())

            val shape = GradientDrawable().apply {
                setColor(bgColor)
                this.cornerRadius = cornerRadius
            }

            val inset = (2 * dm).toInt()
            val tv = TextView(context).apply {
                text = overlay.serviceName
                setTextColor(textColor)
                textSize = 7f
                gravity = Gravity.CENTER
                maxLines = 3
                setPadding(inset, inset, inset, inset)
                background = shape
                layoutParams = FrameLayout.LayoutParams(
                    cellWidth - inset * 2,
                    overlayHeight,
                ).apply {
                    leftMargin = dayX + inset
                    topMargin = topY
                }
                isClickable = false
                isFocusable = false
            }
            container.addView(tv)
        }
    }

    private fun headerCell(
        context: Context,
        text: String,
        widthPx: Int,
        heightPx: Int,
        pad: Int,
    ): TextView = TextView(context).apply {
        this.text = text
        setPadding(pad, pad, pad, pad)
        gravity = Gravity.CENTER
        setTextColor(ContextCompat.getColor(context, R.color.white))
        textSize = 11f
        setBackgroundResource(R.drawable.bg_schedule_timetable_header)
        layoutParams = LinearLayout.LayoutParams(widthPx, heightPx)
    }

    private fun hatchedBackground(context: Context, widthPx: Int, heightPx: Int): Drawable {
        val d = context.resources.displayMetrics.density
        val w = widthPx.coerceAtLeast((40 * d).toInt().coerceAtLeast(32))
        val h = heightPx.coerceAtLeast((44 * d).toInt().coerceAtLeast(32))
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val base = ContextCompat.getColor(context, R.color.schedule_hatch_base)
        val line = ContextCompat.getColor(context, R.color.schedule_hatch_line)
        canvas.drawColor(base)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = line
            strokeWidth = dp(1f, d)
            style = Paint.Style.STROKE
        }
        var x = -h.toFloat()
        while (x < w + h) {
            canvas.drawLine(x, 0f, x + h, h.toFloat(), paint)
            x += dp(5f, d)
        }
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun dp(v: Float, density: Float): Float = v * density

    private fun mondayCalendar(weekOffset: Int): Calendar {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val daysFromMonday = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0; Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2; Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4; Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6; else -> 0
        }
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday + weekOffset * 7)
        return cal
    }

    private fun parseDate(value: String) = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value)
    }.getOrNull()

    /** Returns (hour, minute) from "HH:mm" or "HH:mm:ss". */
    private fun parseTimeHM(value: String): Pair<Int, Int>? {
        val raw = value.trim()
        if (raw.length < 5) return null
        val h = raw.substring(0, 2).toIntOrNull() ?: return null
        val m = raw.substring(3, 5).toIntOrNull() ?: return null
        return h to m
    }
}
