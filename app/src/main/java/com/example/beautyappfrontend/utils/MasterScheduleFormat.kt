package com.example.beautyappfrontend.utils

import com.example.beautyappfrontend.domain.model.MasterScheduleData

/**
 * Converts grid hour slots (8–19) to API day strings (e.g. "9-18", "11", "9-12, 14-16").
 * Inverse of [MasterProfileSchedule.parseDayHoursToFreeSlots].
 */
object MasterScheduleFormat {

    fun weekGridToDayStrings(week: List<List<Int>>): List<String> {
        return (0 until MasterScheduleData.DAY_COUNT).map { dayIndex ->
            daySlotsToString(week.getOrNull(dayIndex).orEmpty())
        }
    }

    fun daySlotsToString(slots: List<Int>): String {
        val sorted = slots.distinct().sorted()
            .filter { it in MasterScheduleData.HOUR_START..MasterScheduleData.HOUR_END_INCLUSIVE }
        if (sorted.isEmpty()) return ""

        val parts = mutableListOf<String>()
        var i = 0
        while (i < sorted.size) {
            val runStart = sorted[i]
            var runEnd = sorted[i]
            var j = i + 1
            while (j < sorted.size && sorted[j] == runEnd + 1) {
                runEnd = sorted[j]
                j++
            }
            if (runStart == runEnd) {
                parts.add("$runStart")
            } else {
                parts.add("$runStart-${runEnd + 1}")
            }
            i = j
        }
        return parts.joinToString(", ")
    }
}
