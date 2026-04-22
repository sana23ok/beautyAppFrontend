package com.example.beautyappfrontend.domain.model

import com.google.gson.annotations.SerializedName

/** Local-only grid: 4 weeks × Mon–Sun; hour ints **8–19**. Empty = not working. */
object MasterScheduleData {
    const val WEEK_COUNT = 4
    const val DAY_COUNT = 7
    const val HOUR_START = 8
    const val HOUR_END_INCLUSIVE = 19

    fun empty(): List<List<List<Int>>> =
        List(WEEK_COUNT) { List(DAY_COUNT) { emptyList() } }
}

fun normalizeScheduleWeeks(raw: List<List<List<Int>>>?): List<List<List<Int>>> {
    val out = mutableListOf<MutableList<List<Int>>>()
    for (wi in 0 until MasterScheduleData.WEEK_COUNT) {
        val weekIn = raw?.getOrNull(wi)
        val days = mutableListOf<List<Int>>()
        for (di in 0 until MasterScheduleData.DAY_COUNT) {
            val dayIn = weekIn?.getOrNull(di)
            val hours = dayIn
                ?.filter { it in MasterScheduleData.HOUR_START..MasterScheduleData.HOUR_END_INCLUSIVE }
                ?.distinct()
                ?.sorted()
                ?: emptyList()
            days.add(hours)
        }
        out.add(days.toMutableList())
    }
    return out
}

data class MasterWorkPhotoRequest(
    @SerializedName("photo_url") val photoUrl: String,
    val caption: String = ""
)

data class MasterWorkPhotoResponse(
    val id: Int? = null,
    @SerializedName("photo_url") val photoUrl: String = "",
    val caption: String = "",
)

data class MasterServiceRequest(
    val name: String,
    val price: Int = 0,
    @SerializedName("duration_minutes") val durationMinutes: Int = 0,
    @SerializedName("requires_prepayment") val requiresPrepayment: Boolean = false,
)

data class MasterServiceResponse(
    val id: Int? = null,
    val name: String = "",
    /** API may send integer or decimal; Gson is more reliable with Double. */
    val price: Double = 0.0,
    @SerializedName("duration_minutes") val durationMinutes: Int = 0,
    @SerializedName("requires_prepayment") val requiresPrepayment: Boolean = false,
)

data class MasterWeekTimetableResponse(
    val id: Int? = null,
    @SerializedName("week_start") val weekStart: String = "",
    @SerializedName("monday_hours") val mondayHours: String = "",
    @SerializedName("tuesday_hours") val tuesdayHours: String = "",
    @SerializedName("wednesday_hours") val wednesdayHours: String = "",
    @SerializedName("thursday_hours") val thursdayHours: String = "",
    @SerializedName("friday_hours") val fridayHours: String = "",
    @SerializedName("saturday_hours") val saturdayHours: String = "",
    @SerializedName("sunday_hours") val sundayHours: String = "",
)

/** POST/PATCH body for `/api/masters/me/week-schedules/`. */
data class MasterWeekTimetableWriteRequest(
    @SerializedName("week_start") val weekStart: String,
    @SerializedName("monday_hours") val mondayHours: String = "",
    @SerializedName("tuesday_hours") val tuesdayHours: String = "",
    @SerializedName("wednesday_hours") val wednesdayHours: String = "",
    @SerializedName("thursday_hours") val thursdayHours: String = "",
    @SerializedName("friday_hours") val fridayHours: String = "",
    @SerializedName("saturday_hours") val saturdayHours: String = "",
    @SerializedName("sunday_hours") val sundayHours: String = "",
)

data class MasterProfileRequest(
    val name: String,
    val specialization: String,
    val city: String,
    val address: String = "",
    @SerializedName("experience_years") val experienceYears: Int,
    val description: String = "",
    @SerializedName("profile_photo") val profilePhoto: String = "",
    val iban: String = "",
    @SerializedName("payment_purpose") val paymentPurpose: String = "",
    @SerializedName("monday_hours") val mondayHours: String = "",
    @SerializedName("tuesday_hours") val tuesdayHours: String = "",
    @SerializedName("wednesday_hours") val wednesdayHours: String = "",
    @SerializedName("thursday_hours") val thursdayHours: String = "",
    @SerializedName("friday_hours") val fridayHours: String = "",
    @SerializedName("saturday_hours") val saturdayHours: String = "",
    @SerializedName("sunday_hours") val sundayHours: String = "",
    @SerializedName("work_photos") val workPhotos: List<MasterWorkPhotoRequest> = emptyList(),
    val services: List<MasterServiceRequest> = emptyList(),
)

data class MasterProfileResponse(
    val id: Int,
    @SerializedName("user_id") val userId: Int? = null,
    val name: String,
    val specialization: String,
    val city: String,
    val address: String = "",
    @SerializedName("experience_years") val experienceYears: Int = 0,
    val description: String = "",
    val rating: Float = 0f,
    @SerializedName("profile_photo") val profilePhoto: String = "",
    val iban: String = "",
    @SerializedName("payment_purpose") val paymentPurpose: String = "",
    @SerializedName("monday_hours") val mondayHours: String = "",
    @SerializedName("tuesday_hours") val tuesdayHours: String = "",
    @SerializedName("wednesday_hours") val wednesdayHours: String = "",
    @SerializedName("thursday_hours") val thursdayHours: String = "",
    @SerializedName("friday_hours") val fridayHours: String = "",
    @SerializedName("saturday_hours") val saturdayHours: String = "",
    @SerializedName("sunday_hours") val sundayHours: String = "",
    /** Nullable so Gson never leaves a non-null list field as null when a key is absent. */
    @SerializedName("work_photos") val workPhotos: List<MasterWorkPhotoResponse>? = null,
    val services: List<MasterServiceResponse>? = null,
    @SerializedName("week_timetables") val weekTimetables: List<MasterWeekTimetableResponse>? = null,
)

data class MasterProfileDraft(
    val masterId: Int? = null,
    val name: String = "",
    val specialization: String = "",
    val city: String = "",
    val address: String = "",
    val experienceYears: Int = 0,
    val description: String = "",
    val profilePhoto: String = "",
    val iban: String = "",
    val paymentPurpose: String = "",
    /** All portfolio work photo URLs from API (excludes profile avatar). */
    val workPhotoUrls: List<String> = emptyList(),
    val workPhotoUrl: String = "",
    val workPhotoCaption: String = "",
    val mondayHours: String = "",
    val tuesdayHours: String = "",
    val wednesdayHours: String = "",
    val thursdayHours: String = "",
    val fridayHours: String = "",
    val saturdayHours: String = "",
    val sundayHours: String = "",
    /** Local-only (not sent to API yet). */
    val scheduleWeeks: List<List<List<Int>>> = MasterScheduleData.empty(),
    /** Price list rows (name + price in UAH). */
    val services: List<MasterServiceItem> = emptyList(),
)

data class MasterServiceItem(
    /** Backend ID; null for rows that haven't been saved yet. */
    val id: Int? = null,
    val name: String = "",
    val price: Int = 0,
    /** Procedure length in minutes. */
    val durationMinutes: Int = 0,
    val requiresPrepayment: Boolean = false,
)
