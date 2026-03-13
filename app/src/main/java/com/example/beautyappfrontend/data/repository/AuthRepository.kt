package com.example.beautyappfrontend.data.repository

import android.util.Log
import com.example.beautyappfrontend.data.remote.RetrofitInstance
import com.example.beautyappfrontend.domain.model.AuthResponse
import com.example.beautyappfrontend.domain.model.AuthUserInfo
import com.example.beautyappfrontend.domain.model.GoogleAuthRequest
import com.example.beautyappfrontend.domain.model.LoginRequest
import com.example.beautyappfrontend.domain.model.RegisterRequest
import com.example.beautyappfrontend.domain.model.UserProfileUpdateRequest
import com.example.beautyappfrontend.utils.DebugLogger
import com.google.gson.Gson

class AuthRepository {

    companion object {
        private const val TAG = "AuthRepository"
        private val gson = Gson()
    }

    suspend fun login(email: String, password: String): AuthResponse {
        val request = LoginRequest(email, password)
        Log.d(TAG, ">>> LOGIN REQUEST body: ${gson.toJson(request)}")

        val response = RetrofitInstance.api.login(request)
        Log.d(TAG, "<<< LOGIN RESPONSE HTTP ${response.code()}")

        if (response.isSuccessful) {
            val body = response.body() ?: throw Exception("Empty response from server")
            Log.d(TAG, "<<< LOGIN RESPONSE body: ${gson.toJson(body)}")
            if (body.authToken.isEmpty()) {
                throw Exception("Server did not return a token. Response: ${gson.toJson(body)}")
            }
            return body
        }

        val errorBody = response.errorBody()?.string() ?: "(empty)"
        Log.e(TAG, "<<< LOGIN ERROR body: $errorBody")
        throw Exception("HTTP ${response.code()}: $errorBody")
    }

    suspend fun register(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        phoneNumber: String = "",
        isMaster: Boolean = false,
    ): AuthResponse {
        val request = RegisterRequest(
            email = email,
            password = password,
            firstName = firstName,
            lastName = lastName,
            phoneNumber = phoneNumber,
            isMaster = isMaster,
        )
        Log.d(TAG, ">>> REGISTER REQUEST body: ${gson.toJson(request)}")

        val response = RetrofitInstance.api.register(request)
        Log.d(TAG, "<<< REGISTER RESPONSE HTTP ${response.code()}")

        if (response.isSuccessful) {
            val body = response.body() ?: throw Exception("Empty response from server")
            Log.d(TAG, "<<< REGISTER RESPONSE body: ${gson.toJson(body)}")
            if (body.authToken.isEmpty()) {
                throw Exception("Server did not return a token. Response: ${gson.toJson(body)}")
            }
            return body
        }

        val errorBody = response.errorBody()?.string() ?: "(empty)"
        Log.e(TAG, "<<< REGISTER ERROR body: $errorBody")
        throw Exception("HTTP ${response.code()}: $errorBody")
    }

    suspend fun getCurrentUser(token: String): AuthUserInfo {
        val response = RetrofitInstance.api.getCurrentUser("Bearer $token")
        Log.d(TAG, "<<< GET ME RESPONSE HTTP ${response.code()}")

        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Empty response from server")
        }

        val errorBody = response.errorBody()?.string() ?: "(empty)"
        Log.e(TAG, "<<< GET ME ERROR body: $errorBody")
        throw Exception("HTTP ${response.code()}: $errorBody")
    }

    suspend fun updateCurrentUser(token: String, request: UserProfileUpdateRequest): AuthUserInfo {
        Log.d(TAG, ">>> UPDATE ME REQUEST body: ${gson.toJson(request)}")
        val response = RetrofitInstance.api.updateCurrentUser("Bearer $token", request)
        Log.d(TAG, "<<< UPDATE ME RESPONSE HTTP ${response.code()}")

        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Empty response from server")
        }

        val errorBody = response.errorBody()?.string() ?: "(empty)"
        Log.e(TAG, "<<< UPDATE ME ERROR body: $errorBody")
        throw Exception("HTTP ${response.code()}: $errorBody")
    }

    suspend fun googleSignIn(idToken: String): AuthResponse {
        val request = GoogleAuthRequest(idToken)
        Log.d(TAG, ">>> GOOGLE AUTH REQUEST")
        // #region agent log
        DebugLogger.log(
            runId = "pre-fix",
            hypothesisId = "H4",
            location = "AuthRepository.kt:64",
            message = "Sending backend Google auth request",
            data = mapOf("idTokenPresent" to idToken.isNotBlank()),
        )
        // #endregion

        val response = RetrofitInstance.api.googleAuth(request)
        Log.d(TAG, "<<< GOOGLE AUTH RESPONSE HTTP ${response.code()}")
        // #region agent log
        DebugLogger.log(
            runId = "pre-fix",
            hypothesisId = "H4",
            location = "AuthRepository.kt:75",
            message = "Received backend Google auth response",
            data = mapOf(
                "httpCode" to response.code(),
                "isSuccessful" to response.isSuccessful,
            ),
        )
        // #endregion

        if (response.isSuccessful) {
            val body = response.body() ?: throw Exception("Empty response from server")
            Log.d(TAG, "<<< GOOGLE AUTH RESPONSE body: ${gson.toJson(body)}")
            // #region agent log
            DebugLogger.log(
                runId = "pre-fix",
                hypothesisId = "H5",
                location = "AuthRepository.kt:88",
                message = "Parsed backend Google auth response body",
                data = mapOf(
                    "hasAccessToken" to body.authToken.isNotEmpty(),
                    "hasUser" to (body.user != null),
                ),
            )
            // #endregion
            if (body.authToken.isEmpty()) {
                throw Exception("Server did not return a token. Response: ${gson.toJson(body)}")
            }
            return body
        }

        val errorBody = response.errorBody()?.string() ?: "(empty)"
        Log.e(TAG, "<<< GOOGLE AUTH ERROR body: $errorBody")
        throw Exception("HTTP ${response.code()}: $errorBody")
    }
}
