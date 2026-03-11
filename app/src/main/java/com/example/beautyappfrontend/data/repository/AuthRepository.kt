package com.example.beautyappfrontend.data.repository

import android.util.Log
import com.example.beautyappfrontend.data.remote.RetrofitInstance
import com.example.beautyappfrontend.domain.model.AuthResponse
import com.example.beautyappfrontend.domain.model.LoginRequest
import com.example.beautyappfrontend.domain.model.RegisterRequest
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

    suspend fun register(username: String, email: String, password: String): AuthResponse {
        val request = RegisterRequest(username, email, password)
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
}
