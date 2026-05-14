package com.example.beautyappfrontend.domain.model

import com.google.gson.annotations.SerializedName

data class ModUser(
    val id: Int = 0,
    val email: String = "",
    @SerializedName("first_name") val firstName: String = "",
    @SerializedName("last_name") val lastName: String = "",
    @SerializedName("is_master") val isMaster: Boolean = false,
    @SerializedName("is_staff") val isStaff: Boolean = false,
    val avatar: String? = null,
    @SerializedName("date_joined") val dateJoined: String = "",
) {
    val displayName: String
        get() {
            val full = "$firstName $lastName".trim()
            return if (full.isNotBlank()) full else email
        }

    val roleLabel: String
        get() = when {
            isStaff -> "Staff"
            isMaster -> "Master"
            else -> "Client"
        }
}

data class ModReview(
    val id: Int = 0,
    @SerializedName("author_email") val authorEmail: String = "",
    @SerializedName("author_name") val authorName: String = "",
    @SerializedName("master_id") val masterId: Int = 0,
    @SerializedName("master_name") val masterName: String = "",
    val rating: Int = 0,
    val comment: String = "",
    @SerializedName("created_at") val createdAt: String = "",
)
