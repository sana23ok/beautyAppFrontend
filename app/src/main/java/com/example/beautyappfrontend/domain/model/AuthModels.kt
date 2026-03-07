package com.example.beautyappfrontend.domain.model

import com.google.gson.annotations.SerializedName

// ── Requests ──────────────────────────────────────────────────────────────────

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
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
    val email: String? = null,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    val avatar: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null
)

data class AuthResponse(
    val user: AuthUserInfo? = null,
    val tokens: AuthTokens? = null
) {
    val authToken: String
        get() = tokens?.access ?: ""
}
