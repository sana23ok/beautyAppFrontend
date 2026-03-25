package com.example.beautyappfrontend.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
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
 * Weekly timetable mock: Mon–Sun 08:00–20:00 (hour slots 8–19).
 * [CLOSED] = grey hatched (not working). [BOOKED] = darker guava + "already booked".
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

    /** Mock: varies by week + day + hour. */
    fun mockSlot(dayIndex: Int, hour: Int, weekOffset: Int): Slot {
        // Not working (hatched): weekend mornings + some blocks
        if (dayIndex >= 5 && hour < 11) return Slot.CLOSED
        if (dayIndex == 0 && hour == 8) return Slot.CLOSED
        if (dayIndex == 1 && hour >= 17) return Slot.CLOSED
        if (dayIndex == 0 && hour == 12) return Slot.CLOSED

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

    fun populateGrid(container: LinearLayout, weekOffset: Int) {
        val context = container.context
        container.removeAllViews()
        val dp = context.resources.displayMetrics.density
        fun dp(v: Float): Int = (v * dp).toInt()

        val padS = dp(4f)
        val padM = dp(6f)
        val dayColW = dp(52f)
        val cellW = dp(34f)
        val rowMinH = dp(44f)
        val hourColumnCount = HOUR_END_INCLUSIVE - HOUR_START + 1
        val hoursTotalWidth = cellW * hourColumnCount

        // Header: one bar — "Days" + empty coral strip (no hour numbers; matches grid width below).
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                rowMinH,
            )
            gravity = Gravity.CENTER_VERTICAL
        }
        headerRow.addView(
            headerCell(context, "Days", dayColW, rowMinH, padM),
        )
        headerRow.addView(
            View(context).apply {
                setBackgroundResource(R.drawable.bg_schedule_timetable_header)
                layoutParams = LinearLayout.LayoutParams(hoursTotalWidth, rowMinH)
            },
        )
        container.addView(headerRow)

        for (dayIndex in 0..6) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            val dayBg = if (dayIndex % 2 == 0) R.drawable.bg_schedule_day_label else R.drawable.bg_schedule_day_label
            row.addView(
                TextView(context).apply {
                    text = dayLabels[dayIndex]
                    setPadding(padS, padM, padS, padM)
                    gravity = Gravity.CENTER
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    textSize = 11f
                    setBackgroundResource(dayBg)
                    layoutParams = LinearLayout.LayoutParams(dayColW, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        minimumHeight = rowMinH
                    }
                },
            )
            for (hour in HOUR_START..HOUR_END_INCLUSIVE) {
                val slot = mockSlot(dayIndex, hour, weekOffset)
                val freeBg = if (dayIndex % 2 == 0) {
                    R.drawable.bg_schedule_slot_free
                } else {
                    R.drawable.bg_schedule_slot_free_alt
                }
                row.addView(
                    TextView(context).apply {
                        gravity = Gravity.CENTER
                        maxLines = 3
                        textSize = 7.5f
                        setPadding(padS, padS, padS, padS)
                        layoutParams = LinearLayout.LayoutParams(cellW, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
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
                                background = hatchedBackground(context)
                            }
                        }
                    },
                )
            }
            container.addView(row)
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

    private fun hatchedBackground(context: Context): Drawable {
        val d = context.resources.displayMetrics.density
        val w = (40 * d).toInt().coerceAtLeast(32)
        val h = (44 * d).toInt().coerceAtLeast(32)
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
