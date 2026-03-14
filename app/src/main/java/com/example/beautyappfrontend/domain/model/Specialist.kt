package com.example.beautyappfrontend.domain.model

import com.google.gson.annotations.SerializedName

data class Specialist(
    val id: Int,
    @SerializedName("user_id") val userId: Int? = null,
    val name: String,
    val specialization: String,
    val rating: Double = 0.0,
    @SerializedName("profile_photo") val imageUrl: String = "",
    val city: String = "",
    val address: String = "",
    val description: String = "",
    @SerializedName("experience_years") val experienceYears: Int = 0,
) {
    val location: String
        get() = listOf(city, address).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "—" }
}

data class TestResponse(
    val message: String,
    val status: String
)