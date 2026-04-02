package com.example.beautyappfrontend.domain.model

import com.google.gson.annotations.SerializedName

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
)

data class MasterServiceResponse(
    val id: Int? = null,
    val name: String = "",
    /** API may send integer or decimal; Gson is more reliable with Double. */
    val price: Double = 0.0,
    @SerializedName("duration_minutes") val durationMinutes: Int = 0,
)

data class MasterProfileRequest(
    val name: String,
    val specialization: String,
    val city: String,
    val address: String = "",
    @SerializedName("experience_years") val experienceYears: Int,
    val description: String = "",
    @SerializedName("profile_photo") val profilePhoto: String = "",
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
    @SerializedName("monday_hours") val mondayHours: String = "",
    @SerializedName("tuesday_hours") val tuesdayHours: String = "",
    @SerializedName("wednesday_hours") val wednesdayHours: String = "",
    @SerializedName("thursday_hours") val thursdayHours: String = "",
    @SerializedName("friday_hours") val fridayHours: String = "",
    @SerializedName("saturday_hours") val saturdayHours: String = "",
    @SerializedName("sunday_hours") val sundayHours: String = "",
    @SerializedName("work_photos") val workPhotos: List<MasterWorkPhotoResponse> = emptyList(),
    val services: List<MasterServiceResponse> = emptyList(),
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
    /** Price list rows (name + price in UAH). */
    val services: List<MasterServiceItem> = emptyList(),
)

data class MasterServiceItem(
    val name: String = "",
    val price: Int = 0,
    /** Procedure length in minutes. */
    val durationMinutes: Int = 0,
)
