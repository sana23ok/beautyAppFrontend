package com.example.beautyappfrontend.data.repository

import android.util.Log
import com.example.beautyappfrontend.data.remote.RetrofitInstance
import com.example.beautyappfrontend.domain.model.MasterProfileRequest
import com.example.beautyappfrontend.domain.model.MasterProfileResponse
import com.example.beautyappfrontend.domain.model.MasterScheduleData
import com.example.beautyappfrontend.domain.model.MasterWeekTimetableResponse
import com.example.beautyappfrontend.domain.model.MasterWeekTimetableWriteRequest
import com.example.beautyappfrontend.domain.model.normalizeScheduleWeeks
import com.example.beautyappfrontend.utils.MasterProfileSchedule
import com.example.beautyappfrontend.utils.MasterScheduleFormat
import com.google.gson.Gson

class MasterRepository {

    companion object {
        private const val TAG = "MasterRepository"
        private val gson = Gson()
        private const val MAX_ERROR_SNIPPET = 400

        /**
         * Django with DEBUG=True returns full HTML trace pages; avoid logging megabytes to Logcat.
         * Detects common cases (e.g. missing migration table) and returns a short user-facing message.
         */
        private fun formatHttpError(httpCode: Int, errorBody: String?): String {
            val raw = errorBody.orEmpty()
            if (raw.isBlank()) return "HTTP $httpCode"
            val lower = raw.lowercase()
            if ("no such table" in lower && "masterservice" in lower.replace("_", "")) {
                return "HTTP $httpCode: database missing masters_masterservice — run: python manage.py migrate"
            }
            if ("no such table" in lower) {
                return "HTTP $httpCode: database missing a table — run: python manage.py migrate (see server log)"
            }
            if ("<html" in lower || "<!doctype html" in lower) {
                return "HTTP $httpCode: server error (Django HTML debug page). Fix the backend (often: run migrations) and set DEBUG=False in production."
            }
            val snippet = raw.trim().take(MAX_ERROR_SNIPPET)
            return if (raw.length > MAX_ERROR_SNIPPET) "HTTP $httpCode: $snippet…" else "HTTP $httpCode: $snippet"
        }
    }

    suspend fun createMasterProfile(token: String, request: MasterProfileRequest): MasterProfileResponse {
        Log.d(TAG, ">>> MASTER PROFILE REQUEST body: ${gson.toJson(request)}")
        val response = RetrofitInstance.api.createMasterProfile("Bearer $token", request)
        Log.d(TAG, "<<< MASTER PROFILE RESPONSE HTTP ${response.code()}")

        if (response.isSuccessful) {
            val body = response.body() ?: throw Exception("Empty response from server")
            Log.d(TAG, "<<< MASTER PROFILE RESPONSE body: ${gson.toJson(body)}")
            return body
        }

        val errorBody = response.errorBody()?.string()
        val msg = formatHttpError(response.code(), errorBody)
        Log.e(TAG, "<<< MASTER PROFILE ERROR: $msg")
        throw Exception(msg)
    }

    suspend fun getMyMasterProfile(token: String): MasterProfileResponse {
        val response = RetrofitInstance.api.getMyMasterProfile("Bearer $token")
        Log.d(TAG, "<<< GET MY MASTER RESPONSE HTTP ${response.code()}")

        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Empty response from server")
        }

        val errorBody = response.errorBody()?.string()
        val msg = formatHttpError(response.code(), errorBody)
        Log.e(TAG, "<<< GET MY MASTER ERROR: $msg")
        throw Exception(msg)
    }

    suspend fun updateMyMasterProfile(token: String, request: MasterProfileRequest): MasterProfileResponse {
        Log.d(TAG, ">>> UPDATE MASTER PROFILE REQUEST body: ${gson.toJson(request)}")
        val response = RetrofitInstance.api.updateMyMasterProfile("Bearer $token", request)
        Log.d(TAG, "<<< UPDATE MASTER PROFILE RESPONSE HTTP ${response.code()}")

        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Empty response from server")
        }

        val errorBody = response.errorBody()?.string()
        val msg = formatHttpError(response.code(), errorBody)
        Log.e(TAG, "<<< UPDATE MASTER PROFILE ERROR: $msg")
        throw Exception(msg)
    }

    /** GET /api/masters/me/week-schedules/ */
    suspend fun getMyWeekSchedules(token: String): List<MasterWeekTimetableResponse> {
        val response = RetrofitInstance.api.getMyWeekSchedules("Bearer $token")
        if (response.isSuccessful) {
            return response.body() ?: emptyList()
        }
        val errorBody = response.errorBody()?.string()
        throw Exception(formatHttpError(response.code(), errorBody))
    }

    /**
     * Upserts four weekly rows (current + 3 weeks) and updates the master template hours from week 0.
     * Call [updateMyMasterProfile] with hours derived from the grid separately via [MasterProfileRequest].
     */
    suspend fun upsertWeekTimetablesFromGrid(token: String, scheduleWeeks: List<List<List<Int>>>) {
        val normalized = normalizeScheduleWeeks(scheduleWeeks)
        val existing = getMyWeekSchedules(token)
        val byMonday = existing.associateBy { it.weekStart.trim().take(10) }

        for (offset in 0 until MasterScheduleData.WEEK_COUNT) {
            val monday = MasterProfileSchedule.mondayDateKeyForWeekOffset(offset)
            val week = normalized.getOrNull(offset) ?: continue
            val dayStrings = MasterScheduleFormat.weekGridToDayStrings(week)
            val body = MasterWeekTimetableWriteRequest(
                weekStart = monday,
                mondayHours = dayStrings[0],
                tuesdayHours = dayStrings[1],
                wednesdayHours = dayStrings[2],
                thursdayHours = dayStrings[3],
                fridayHours = dayStrings[4],
                saturdayHours = dayStrings[5],
                sundayHours = dayStrings[6],
            )
            val row = byMonday[monday]
            val id = row?.id
            if (id != null && id > 0) {
                val r = RetrofitInstance.api.patchMyWeekSchedule("Bearer $token", id, body)
                if (!r.isSuccessful) {
                    val err = r.errorBody()?.string()
                    throw Exception(formatHttpError(r.code(), err))
                }
            } else {
                val r = RetrofitInstance.api.createMyWeekSchedule("Bearer $token", body)
                if (!r.isSuccessful) {
                    val err = r.errorBody()?.string()
                    throw Exception(formatHttpError(r.code(), err))
                }
            }
        }
    }

    /** Public master card — GET /api/masters/{id}/ (no auth). */
    suspend fun getMasterProfile(masterId: Int): MasterProfileResponse {
        val response = RetrofitInstance.api.getMasterProfile(masterId)
        Log.d(TAG, "<<< GET MASTER $masterId HTTP ${response.code()}")
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Empty response from server")
        }
        val errorBody = response.errorBody()?.string()
        val msg = formatHttpError(response.code(), errorBody)
        Log.e(TAG, "<<< GET MASTER ERROR: $msg")
        throw Exception(msg)
    }
}
