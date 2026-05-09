package com.example.beautyappfrontend.domain.model

import com.google.gson.annotations.SerializedName

// ── Requests ──────────────────────────────────────────────────────────────────

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    @SerializedName("verification_code") val verificationCode: String,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    @SerializedName("phone_number") val phoneNumber: String = "",
    @SerializedName("is_master") val isMaster: Boolean = false,
)

data class SendVerificationCodeRequest(
    val email: String,
    val password: String,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    @SerializedName("phone_number") val phoneNumber: String = "",
    @SerializedName("is_master") val isMaster: Boolean = false,
)

data class GoogleAuthRequest(
    @SerializedName("id_token") val idToken: String
)

// ── Response ──────────────────────────────────────────────────────────────────
// Backend returns:
// {
//   "user":   { "id": 3, "email": "...", "first_name": "", "last_name": "", ... },
//   "tokens": { "access": "eyJ...", "refresh": "eyJ..." }
// }

data class AuthTokens(
    val access: String? = null,
    val refresh: String? = null
)

data class AuthUserInfo(
    val id: Int? = null,
    val username: String? = null,
    val email: String? = null,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    val avatar: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("is_master") val isMaster: Boolean? = null,
    @SerializedName("client_profile_id") val clientProfileId: Int? = null,
    @SerializedName("master_profile_id") val masterProfileId: Int? = null,
)

data class UserProfileUpdateRequest(
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    val avatar: String? = null,
)

data class AuthResponse(
    val user: AuthUserInfo? = null,
    val tokens: AuthTokens? = null
) {
    val authToken: String
        get() = tokens?.access ?: ""
}

data class VerificationCodeResponse(
    val message: String = "",
)

data class AvatarUploadResponse(
    val url: String = "",
)
