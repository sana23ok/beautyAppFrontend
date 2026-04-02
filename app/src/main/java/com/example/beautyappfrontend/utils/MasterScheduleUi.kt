package com.example.beautyappfrontend.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewTreeObserver
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.beautyappfrontend.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Weekly timetable (transposed): each row is an hour [HOUR_START..HOUR_END], columns are Mon–Sun.
 * Each cell = that hour on that day. Optional per-day strings (e.g. "9:00 – 18:00") mark slots
 * outside the range as CLOSED. Otherwise demo rules apply for closed/booked slots.
 */
object MasterScheduleUi {

    private const val HOUR_START = 8
    private const val HOUR_END_INCLUSIVE = 19 // slot 19 = 19:00–20:00

    enum class Slot {
        FREE,
        BOOKED,
        CLOSED,
    }

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
        cal.add(Calendar.DAY_OF_MONTH, 7 * weekOffset)
        val start = cal.time
        cal.add(Calendar.DAY_OF_MONTH, 6)
        val end = cal.time
        return "${weekRangeFormat.format(start)} – ${weekRangeFormat.format(end)} ${yearFormat.format(end)}"
    }

    /**
     * Parses strings like "9:00 – 18:00", "8:00-20:00", "8 — 20".
     * Returns half-open range of slot indices [start, end) intersected with the grid (each slot = hour h).
     */
    private fun parseWorkingSlotRange(dayHours: String): IntRange? {
        if (dayHours.isBlank()) return null
        val m = Regex("""(\d{1,2})\s*[:-–—]\s*(\d{1,2})""").find(dayHours) ?: return null
        val start = m.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: return null
        val end = m.groupValues[2].toIntOrNull()?.coerceIn(0, 24) ?: return null
        if (start >= end) return null
        val slotStart = start.coerceAtLeast(HOUR_START)
        val slotEndExclusive = end.coerceAtMost(HOUR_END_INCLUSIVE + 1).coerceAtLeast(slotStart + 1)
        if (slotStart >= slotEndExclusive) return null
        return slotStart until slotEndExclusive
    }

    /** Mock: varies by week + day + hour; [dayHours] can force CLOSED outside working range. */
    fun mockSlot(
        dayIndex: Int,
        hour: Int,
        weekOffset: Int,
        dayHoursList: List<String> = List(7) { "" },
    ): Slot {
        val range = dayHoursList.getOrNull(dayIndex)?.let { parseWorkingSlotRange(it) }
        if (range != null && hour !in range) return Slot.CLOSED

        // Not working (hatched): weekend mornings + some blocks (only when no custom hours)
        if (range == null && dayIndex >= 5 && hour < 11) return Slot.CLOSED
        if (range == null && dayIndex == 0 && hour == 8) return Slot.CLOSED
        if (range == null && dayIndex == 1 && hour >= 17) return Slot.CLOSED
        if (range == null && dayIndex == 0 && hour == 12) return Slot.CLOSED

        // Booked (dark + label)
        return when (weekOffset) {
            0 -> when {
                dayIndex == 0 && hour == 10 -> Slot.BOOKED
                dayIndex == 2 && hour == 14 -> Slot.BOOKED
                dayIndex == 4 && hour == 16 -> Slot.BOOKED
                else -> Slot.FREE
            }
            1 -> when {
                dayIndex == 1 && hour == 11 -> Slot.BOOKED
                dayIndex == 3 && hour == 9 -> Slot.BOOKED
                dayIndex == 2 && hour == 14 -> Slot.BOOKED
                else -> Slot.FREE
            }
            2 -> when {
                dayIndex == 0 && hour == 15 -> Slot.BOOKED
                dayIndex == 4 && hour == 11 -> Slot.BOOKED
                else -> Slot.FREE
            }
            3 -> when {
                dayIndex == 0 && hour == 9 -> Slot.BOOKED
                dayIndex == 5 && hour == 14 -> Slot.BOOKED
                dayIndex == 4 && hour == 16 -> Slot.BOOKED
                else -> Slot.FREE
            }
            else -> Slot.FREE
        }
    }

    fun populateGrid(
        container: LinearLayout,
        weekOffset: Int,
        dayHoursList: List<String> = List(7) { "" },
    ) {
        val context = container.context
        container.removeAllViews()

        fun buildAtWidth(widthPx: Int) {
            if (widthPx <= 0) return
            container.removeAllViews()
            val dm = context.resources.displayMetrics.density
            fun toPx(v: Float): Int = (v * dm).toInt()

            val padS = toPx(4f)
            val padM = toPx(6f)
            val rowMinH = toPx(44f)

            // Left column ~15% of width (hour labels), rest split across Mon–Sun.
            val labelColW = (widthPx * 0.15f).toInt().coerceIn(toPx(44f), toPx(80f))
            val remaining = (widthPx - labelColW).coerceAtLeast(0)
            val cellBase = remaining / 7
            val extra = remaining - cellBase * 7
            fun dayCellWidth(dayIndex: Int): Int = cellBase + if (dayIndex < extra) 1 else 0
            val daysTotalWidth = remaining

            val rowLayoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )

            // Header: corner "Hours" + Mon..Sun (aligns with grid below).
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
                layoutParams = LinearLayout.LayoutParams(daysTotalWidth, rowMinH)
                setBackgroundResource(R.drawable.bg_schedule_timetable_header)
            }
            for (dayIndex in 0..6) {
                val cw = dayCellWidth(dayIndex)
                dayHeader.addView(
                    TextView(context).apply {
                        text = dayLabels[dayIndex]
                        gravity = Gravity.CENTER
                        textSize = 9f
                        maxLines = 1
                        setTextColor(ContextCompat.getColor(context, R.color.white))
                        setPadding(padS, padS, padS, padS)
                        layoutParams = LinearLayout.LayoutParams(cw, rowMinH)
                    },
                )
            }
            headerRow.addView(dayHeader)
            container.addView(headerRow)

            for (hour in HOUR_START..HOUR_END_INCLUSIVE) {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = rowLayoutParams
                }
                val hourLabelBg = R.drawable.bg_schedule_day_label
                row.addView(
                    TextView(context).apply {
                        text = context.getString(R.string.schedule_hour_label, hour)
                        setPadding(padS, padM, padS, padM)
                        gravity = Gravity.CENTER
                        setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                        textSize = 11f
                        setBackgroundResource(hourLabelBg)
                        layoutParams = LinearLayout.LayoutParams(labelColW, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            minimumHeight = rowMinH
                        }
                    },
                )
                for (dayIndex in 0..6) {
                    val slot = mockSlot(dayIndex, hour, weekOffset, dayHoursList)
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
                                }
                                Slot.BOOKED -> {
                                    text = context.getString(R.string.schedule_already_booked)
                                    setTextColor(ContextCompat.getColor(context, R.color.schedule_slot_booked_text))
                                    setBackgroundResource(R.drawable.bg_schedule_slot_booked)
                                }
                                Slot.CLOSED -> {
                                    text = ""
                                    background = hatchedBackground(context, cw, rowMinH)
                                }
                            }
                        },
                    )
                }
                container.addView(row)
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
}
