package com.example.beautyappfrontend.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import com.example.beautyappfrontend.domain.model.AuthUserInfo
import com.example.beautyappfrontend.domain.model.MasterProfileDraft
import com.example.beautyappfrontend.domain.model.MasterProfileResponse
import com.example.beautyappfrontend.domain.model.MasterScheduleData
import com.example.beautyappfrontend.domain.model.MasterServiceItem

class SessionManager(context: Context) {

    private val prefs = context.getSharedPreferences("beauty_app_auth", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scheduleWeeksType = object : TypeToken<List<List<List<Int>>>>() {}.type
    private val masterServicesType = object : TypeToken<List<MasterServiceItem>>() {}.type

    // ── Token ─────────────────────────────────────────────────────────────────

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun saveRefreshToken(refresh: String) {
        prefs.edit().putString(KEY_REFRESH_TOKEN, refresh).apply()
    }

    /**
     * Persists access + optional refresh JWT. If [refresh] is null/blank, refresh token is removed.
     */
    fun saveTokens(access: String, refresh: String?) {
        val e = prefs.edit().putString(KEY_TOKEN, access)
        if (refresh.isNullOrBlank()) {
            e.remove(KEY_REFRESH_TOKEN)
        } else {
            e.putString(KEY_REFRESH_TOKEN, refresh)
        }
        e.apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

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
            .putBoolean(KEY_IS_MASTER, user.isMaster == true)
            .putInt(KEY_MASTER_ID, user.masterProfileId ?: -1)
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

    fun saveAvatarUrl(url: String) {
        prefs.edit().putString(KEY_AVATAR, url).apply()
    }

    fun saveMasterProfilePhoto(url: String) {
        prefs.edit().putString(KEY_MASTER_PROFILE_PHOTO, url).apply()
    }

    fun getPhoneNumber(): String = prefs.getString(KEY_PHONE, "") ?: ""

    fun saveIsMaster(isMaster: Boolean) {
        prefs.edit().putBoolean(KEY_IS_MASTER, isMaster).apply()
    }

    fun isMaster(): Boolean = prefs.getBoolean(KEY_IS_MASTER, false)

    fun saveMasterProfile(profile: MasterProfileResponse) {
        prefs.edit()
            .putInt(KEY_MASTER_ID, profile.id)
            .putString(KEY_MASTER_NAME, profile.name)
            .putString(KEY_MASTER_SPECIALIZATION, profile.specialization)
            .putString(KEY_MASTER_CITY, profile.city)
            .putString(KEY_MASTER_ADDRESS, profile.address)
            .putInt(KEY_MASTER_EXPERIENCE, profile.experienceYears)
            .putString(KEY_MASTER_DESCRIPTION, profile.description)
            .putString(KEY_MASTER_PROFILE_PHOTO, profile.profilePhoto)
            .putString(KEY_MASTER_WORK_PHOTO_URL, profile.workPhotos.orEmpty().firstOrNull()?.photoUrl ?: "")
            .putString(KEY_MASTER_WORK_PHOTO_CAPTION, profile.workPhotos.orEmpty().firstOrNull()?.caption ?: "")
            .putString(KEY_MASTER_WORK_PHOTOS_JSON, workPhotoUrlsToJson(profile.workPhotos.orEmpty().map { it.photoUrl }))
            .putString(KEY_MASTER_MONDAY_HOURS, profile.mondayHours)
            .putString(KEY_MASTER_TUESDAY_HOURS, profile.tuesdayHours)
            .putString(KEY_MASTER_WEDNESDAY_HOURS, profile.wednesdayHours)
            .putString(KEY_MASTER_THURSDAY_HOURS, profile.thursdayHours)
            .putString(KEY_MASTER_FRIDAY_HOURS, profile.fridayHours)
            .putString(KEY_MASTER_SATURDAY_HOURS, profile.saturdayHours)
            .putString(KEY_MASTER_SUNDAY_HOURS, profile.sundayHours)
            .apply()
    }

    fun saveMasterDraft(draft: MasterProfileDraft) {
        prefs.edit()
            .putInt(KEY_MASTER_ID, draft.masterId ?: -1)
            .putString(KEY_MASTER_NAME, draft.name)
            .putString(KEY_MASTER_SPECIALIZATION, draft.specialization)
            .putString(KEY_MASTER_CITY, draft.city)
            .putString(KEY_MASTER_ADDRESS, draft.address)
            .putInt(KEY_MASTER_EXPERIENCE, draft.experienceYears)
            .putString(KEY_MASTER_DESCRIPTION, draft.description)
            .putString(KEY_MASTER_PROFILE_PHOTO, draft.profilePhoto)
            .putString(KEY_MASTER_WORK_PHOTO_URL, draft.workPhotoUrl)
            .putString(KEY_MASTER_WORK_PHOTO_CAPTION, draft.workPhotoCaption)
            .putString(KEY_MASTER_WORK_PHOTOS_JSON, workPhotoUrlsToJson(draft.workPhotoUrls))
            .putString(KEY_MASTER_MONDAY_HOURS, draft.mondayHours)
            .putString(KEY_MASTER_TUESDAY_HOURS, draft.tuesdayHours)
            .putString(KEY_MASTER_WEDNESDAY_HOURS, draft.wednesdayHours)
            .putString(KEY_MASTER_THURSDAY_HOURS, draft.thursdayHours)
            .putString(KEY_MASTER_FRIDAY_HOURS, draft.fridayHours)
            .putString(KEY_MASTER_SATURDAY_HOURS, draft.saturdayHours)
            .putString(KEY_MASTER_SUNDAY_HOURS, draft.sundayHours)
            .putString(KEY_MASTER_SCHEDULE_WEEKS_JSON, scheduleWeeksToJson(draft.scheduleWeeks))
            .putString(KEY_MASTER_SERVICES_JSON, gson.toJson(draft.services))
            .apply()
    }

    fun getMasterDraft(): MasterProfileDraft {
        val masterId = prefs.getInt(KEY_MASTER_ID, -1).takeIf { it > 0 }
        val profilePic = prefs.getString(KEY_MASTER_PROFILE_PHOTO, "")?.trim().orEmpty()
        val legacyWorkUrl = prefs.getString(KEY_MASTER_WORK_PHOTO_URL, "") ?: ""
        val urlsFromJson = parseWorkPhotoUrls(prefs.getString(KEY_MASTER_WORK_PHOTOS_JSON, null))
            .filter { it.isNotBlank() && !it.equals(profilePic, ignoreCase = true) }
        val workPhotoUrls = when {
            urlsFromJson.isNotEmpty() -> urlsFromJson
            legacyWorkUrl.isNotBlank() && !legacyWorkUrl.equals(profilePic, ignoreCase = true) ->
                listOf(legacyWorkUrl)
            else -> emptyList()
        }
        return MasterProfileDraft(
            masterId = masterId,
            name = prefs.getString(KEY_MASTER_NAME, "") ?: "",
            specialization = prefs.getString(KEY_MASTER_SPECIALIZATION, "") ?: "",
            city = prefs.getString(KEY_MASTER_CITY, "") ?: "",
            address = prefs.getString(KEY_MASTER_ADDRESS, "") ?: "",
            experienceYears = prefs.getInt(KEY_MASTER_EXPERIENCE, 0),
            description = prefs.getString(KEY_MASTER_DESCRIPTION, "") ?: "",
            profilePhoto = prefs.getString(KEY_MASTER_PROFILE_PHOTO, "") ?: "",
            workPhotoUrls = workPhotoUrls,
            workPhotoUrl = workPhotoUrls.firstOrNull() ?: legacyWorkUrl,
            workPhotoCaption = prefs.getString(KEY_MASTER_WORK_PHOTO_CAPTION, "") ?: "",
            mondayHours = prefs.getString(KEY_MASTER_MONDAY_HOURS, "") ?: "",
            tuesdayHours = prefs.getString(KEY_MASTER_TUESDAY_HOURS, "") ?: "",
            wednesdayHours = prefs.getString(KEY_MASTER_WEDNESDAY_HOURS, "") ?: "",
            thursdayHours = prefs.getString(KEY_MASTER_THURSDAY_HOURS, "") ?: "",
            fridayHours = prefs.getString(KEY_MASTER_FRIDAY_HOURS, "") ?: "",
            saturdayHours = prefs.getString(KEY_MASTER_SATURDAY_HOURS, "") ?: "",
            sundayHours = prefs.getString(KEY_MASTER_SUNDAY_HOURS, "") ?: "",
            scheduleWeeks = parseScheduleWeeks(prefs.getString(KEY_MASTER_SCHEDULE_WEEKS_JSON, null)),
            services = parseMasterServices(prefs.getString(KEY_MASTER_SERVICES_JSON, null)),
        )
    }

    // ── Clear ─────────────────────────────────────────────────────────────────

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    private fun workPhotoUrlsToJson(urls: List<String>): String {
        val arr = JSONArray()
        urls.filter { it.isNotBlank() }.forEach { arr.put(it) }
        return arr.toString()
    }

    private fun parseWorkPhotoUrls(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun scheduleWeeksToJson(sw: List<List<List<Int>>>): String = gson.toJson(sw)

    private fun parseScheduleWeeks(json: String?): List<List<List<Int>>> {
        if (json.isNullOrBlank()) return MasterScheduleData.empty()
        return try {
            gson.fromJson<List<List<List<Int>>>>(json, scheduleWeeksType) ?: MasterScheduleData.empty()
        } catch (_: Exception) {
            MasterScheduleData.empty()
        }
    }

    private fun parseMasterServices(json: String?): List<MasterServiceItem> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<MasterServiceItem>>(json, masterServicesType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val TAG            = "SessionManager"
        private const val KEY_TOKEN      = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID    = "user_id"
        private const val KEY_USERNAME   = "username"
        private const val KEY_EMAIL      = "email"
        private const val KEY_FIRST_NAME = "first_name"
        private const val KEY_LAST_NAME  = "last_name"
        private const val KEY_AVATAR     = "avatar"
        private const val KEY_PHONE      = "phone"
        private const val KEY_IS_MASTER  = "is_master"
        private const val KEY_MASTER_ID = "master_id"
        private const val KEY_MASTER_NAME = "master_name"
        private const val KEY_MASTER_SPECIALIZATION = "master_specialization"
        private const val KEY_MASTER_CITY = "master_city"
        private const val KEY_MASTER_ADDRESS = "master_address"
        private const val KEY_MASTER_EXPERIENCE = "master_experience"
        private const val KEY_MASTER_DESCRIPTION = "master_description"
        private const val KEY_MASTER_PROFILE_PHOTO = "master_profile_photo"
        private const val KEY_MASTER_WORK_PHOTO_URL = "master_work_photo_url"
        private const val KEY_MASTER_WORK_PHOTO_CAPTION = "master_work_photo_caption"
        private const val KEY_MASTER_WORK_PHOTOS_JSON = "master_work_photos_json"
        private const val KEY_MASTER_MONDAY_HOURS = "master_monday_hours"
        private const val KEY_MASTER_TUESDAY_HOURS = "master_tuesday_hours"
        private const val KEY_MASTER_WEDNESDAY_HOURS = "master_wednesday_hours"
        private const val KEY_MASTER_THURSDAY_HOURS = "master_thursday_hours"
        private const val KEY_MASTER_FRIDAY_HOURS = "master_friday_hours"
        private const val KEY_MASTER_SATURDAY_HOURS = "master_saturday_hours"
        private const val KEY_MASTER_SUNDAY_HOURS = "master_sunday_hours"
        private const val KEY_MASTER_SCHEDULE_WEEKS_JSON = "master_schedule_weeks_json"
        private const val KEY_MASTER_SERVICES_JSON = "master_services_json"
    }
}
