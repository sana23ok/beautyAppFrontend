package com.example.beautyappfrontend.data.repository

import android.util.Log
import com.example.beautyappfrontend.data.remote.RetrofitInstance
import com.example.beautyappfrontend.domain.model.MasterProfileRequest
import com.example.beautyappfrontend.domain.model.MasterProfileResponse
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
