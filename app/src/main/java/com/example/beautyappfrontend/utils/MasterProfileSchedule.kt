package com.example.beautyappfrontend.utils

import com.example.beautyappfrontend.domain.model.MasterProfileResponse
import com.example.beautyappfrontend.domain.model.MasterScheduleData
import com.example.beautyappfrontend.domain.model.MasterWeekTimetableResponse
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Builds [MasterScheduleData.WEEK_COUNT] weeks of hour slots (8–19) for read-only grids.
 * Empty / unparseable day strings → no free slots (all “not working”).
 * Rows from [MasterProfileResponse.weekTimetables] override when [weekStart] matches that week’s Monday (yyyy-MM-dd).
 */
object MasterProfileSchedule {

    private val weekKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    fun buildScheduleWeeks(master: MasterProfileResponse): List<List<List<Int>>> {
        val defaultWeek = weekFromDayStrings(
            listOf(
                master.mondayHours,
                master.tuesdayHours,
                master.wednesdayHours,
                master.thursdayHours,
                master.fridayHours,
                master.saturdayHours,
                master.sundayHours,
            ),
        )

        val byMonday: Map<String, List<List<Int>>> = master.weekTimetables.orEmpty()
            .mapNotNull { row ->
                val key = row.weekStart.trim().take(10)
                if (key.isBlank()) return@mapNotNull null
                key to weekFromTimetableRow(row)
            }
            .toMap()

        return List(MasterScheduleData.WEEK_COUNT) { weekOffset ->
            val monday = mondayCalendarForOffset(weekOffset)
            val key = weekKeyFormat.format(monday.time)
            byMonday[key] ?: defaultWeek
        }
    }

    private fun weekFromTimetableRow(row: MasterWeekTimetableResponse): List<List<Int>> {
        return weekFromDayStrings(
            listOf(
                row.mondayHours,
                row.tuesdayHours,
                row.wednesdayHours,
                row.thursdayHours,
                row.fridayHours,
                row.saturdayHours,
                row.sundayHours,
            ),
        )
    }

    private fun weekFromDayStrings(days: List<String>): List<List<Int>> {
        return days.map { parseDayHoursToFreeSlots(it) }
    }

    /**
     * Parses strings like `9:00–18:00`, `9-18`, `10:00 - 19:00` into hour indices 8–19 (grid rows).
     */
    fun parseDayHoursToFreeSlots(hours: String): List<Int> {
        val s = hours.trim()
        if (s.isBlank()) return emptyList()
        val nums = Regex("""\d{1,2}""").findAll(s).mapNotNull { it.value.toIntOrNull() }.toList()
        if (nums.size >= 2) {
            val start = nums[0].coerceIn(MasterScheduleData.HOUR_START, MasterScheduleData.HOUR_END_INCLUSIVE)
            val endMark = nums[1].coerceIn(MasterScheduleData.HOUR_START, 23)
            val endExclusive = endMark.coerceAtMost(MasterScheduleData.HOUR_END_INCLUSIVE + 1)
            if (start < endExclusive) {
                return (start until endExclusive)
                    .filter { it in MasterScheduleData.HOUR_START..MasterScheduleData.HOUR_END_INCLUSIVE }
            }
        }
        if (nums.size == 1) {
            val h = nums[0].coerceIn(MasterScheduleData.HOUR_START, MasterScheduleData.HOUR_END_INCLUSIVE)
            return listOf(h)
        }
        return emptyList()
    }

    private fun mondayCalendarForOffset(weekOffset: Int): Calendar {
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
        return cal
    }
}
