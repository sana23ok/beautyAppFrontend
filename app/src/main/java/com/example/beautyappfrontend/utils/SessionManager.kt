package com.example.beautyappfrontend.utils

import android.content.Context
import android.util.Log
import com.example.beautyappfrontend.domain.model.AuthUserInfo

class SessionManager(context: Context) {

    private val prefs = context.getSharedPreferences("beauty_app_auth", Context.MODE_PRIVATE)

    // ── Token ─────────────────────────────────────────────────────────────────

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun isLoggedIn(): Boolean = !getToken().isNullOrEmpty()

    // ── User info ─────────────────────────────────────────────────────────────

    fun saveUserInfo(user: AuthUserInfo?) {
        if (user == null) {
            Log.w(TAG, "saveUserInfo called with null — no user data saved")
            return
        }
        Log.d(TAG, "saveUserInfo: id=${user.id} username=${user.username} " +
                "email=${user.email} firstName=${user.firstName} " +
                "lastName=${user.lastName} phone=${user.phoneNumber}")
        prefs.edit()
            .putInt(KEY_USER_ID, user.id ?: -1)
            .putString(KEY_USERNAME, user.username)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_FIRST_NAME, user.firstName)
            .putString(KEY_LAST_NAME, user.lastName)
            .putString(KEY_AVATAR, user.avatar)
            .putString(KEY_PHONE, user.phoneNumber)
            .apply()
    }

    // Priority: first+last name → username → email
    fun getDisplayName(): String {
        val first    = prefs.getString(KEY_FIRST_NAME, "") ?: ""
        val last     = prefs.getString(KEY_LAST_NAME, "") ?: ""
        val username = prefs.getString(KEY_USERNAME, "") ?: ""
        val email    = prefs.getString(KEY_EMAIL, "") ?: ""
        return when {
            first.isNotEmpty() || last.isNotEmpty() -> "$first $last".trim()
            username.isNotEmpty() -> username
            else -> email
        }
    }

    fun getUsername(): String = prefs.getString(KEY_USERNAME, "") ?: ""

    fun getEmail(): String = prefs.getString(KEY_EMAIL, "") ?: ""

    fun getAvatarUrl(): String? = prefs.getString(KEY_AVATAR, null)

    fun getPhoneNumber(): String = prefs.getString(KEY_PHONE, "") ?: ""

    // ── Clear ─────────────────────────────────────────────────────────────────

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG            = "SessionManager"
        private const val KEY_TOKEN      = "access_token"
        private const val KEY_USER_ID    = "user_id"
        private const val KEY_USERNAME   = "username"
        private const val KEY_EMAIL      = "email"
        private const val KEY_FIRST_NAME = "first_name"
        private const val KEY_LAST_NAME  = "last_name"
        private const val KEY_AVATAR     = "avatar"
        private const val KEY_PHONE      = "phone"
    }
}
