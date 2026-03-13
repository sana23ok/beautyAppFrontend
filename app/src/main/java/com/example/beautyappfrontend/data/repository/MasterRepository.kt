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

        val errorBody = response.errorBody()?.string() ?: "(empty)"
        Log.e(TAG, "<<< MASTER PROFILE ERROR body: $errorBody")
        throw Exception("HTTP ${response.code()}: $errorBody")
    }

    suspend fun getMyMasterProfile(token: String): MasterProfileResponse {
        val response = RetrofitInstance.api.getMyMasterProfile("Bearer $token")
        Log.d(TAG, "<<< GET MY MASTER RESPONSE HTTP ${response.code()}")

        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Empty response from server")
        }

        val errorBody = response.errorBody()?.string() ?: "(empty)"
        Log.e(TAG, "<<< GET MY MASTER ERROR body: $errorBody")
        throw Exception("HTTP ${response.code()}: $errorBody")
    }

    suspend fun updateMyMasterProfile(token: String, request: MasterProfileRequest): MasterProfileResponse {
        Log.d(TAG, ">>> UPDATE MASTER PROFILE REQUEST body: ${gson.toJson(request)}")
        val response = RetrofitInstance.api.updateMyMasterProfile("Bearer $token", request)
        Log.d(TAG, "<<< UPDATE MASTER PROFILE RESPONSE HTTP ${response.code()}")

        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Empty response from server")
        }

        val errorBody = response.errorBody()?.string() ?: "(empty)"
        Log.e(TAG, "<<< UPDATE MASTER PROFILE ERROR body: $errorBody")
        throw Exception("HTTP ${response.code()}: $errorBody")
    }
}
