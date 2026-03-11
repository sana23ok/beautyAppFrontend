package com.example.beautyappfrontend.data.remote

import com.example.beautyappfrontend.domain.model.AnalysisResponse
import com.example.beautyappfrontend.domain.model.AppearanceTestRequest
import com.example.beautyappfrontend.domain.model.AppearanceTestResponse
import com.example.beautyappfrontend.domain.model.AuthResponse
import com.example.beautyappfrontend.domain.model.GoogleAuthRequest
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

    @GET("api/masters/")
    suspend fun searchSpecialists(
        @retrofit2.http.Query("q")               query: String?          = null,
        @retrofit2.http.Query("location")        location: String?       = null,
        @retrofit2.http.Query("specialisation")  specialisation: String? = null,
        @retrofit2.http.Query("experience")      experience: String?     = null,
        @retrofit2.http.Query("price_max")       priceMax: Int?          = null,
        @retrofit2.http.Query("page")            page: Int?              = null
    ): Response<List<Specialist>>

    // TODO: add {id} to URL when backend is ready
    @GET("api/test_results/")
    suspend fun getAnalysisResult(@Path("id") id: Int): Response<AnalysisResponse>

    @POST("api/auth/login/")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/auth/register/")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/google/")
    suspend fun googleAuth(@Body request: GoogleAuthRequest): Response<AuthResponse>

    @POST("api/appearance_test/analyse/")
    suspend fun submitAppearanceTest(@Body request: AppearanceTestRequest): Response<AppearanceTestResponse>
}