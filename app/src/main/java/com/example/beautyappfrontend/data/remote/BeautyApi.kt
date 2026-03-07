package com.example.beautyappfrontend.data.remote

import com.example.beautyappfrontend.domain.model.AnalysisResponse
import com.example.beautyappfrontend.domain.model.AuthResponse
import com.example.beautyappfrontend.domain.model.LoginRequest
import com.example.beautyappfrontend.domain.model.RegisterRequest
import com.example.beautyappfrontend.domain.model.Specialist
import com.example.beautyappfrontend.domain.model.TestResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface BeautyApi {

    @GET("api/test/")
    suspend fun checkConnection(): TestResponse

    @GET("api/masters/")
    suspend fun getSpecialists(): List<Specialist>

    // TODO: add {id} to URL when backend is ready
    @GET("api/test_results/")
    suspend fun getAnalysisResult(@Path("id") id: Int): Response<AnalysisResponse>

    @POST("api/auth/login/")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/auth/register/")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>
}