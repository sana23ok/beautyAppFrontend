package com.example.beautyappfrontend.data.remote

import com.example.beautyappfrontend.domain.model.AnalysisResponse
import com.example.beautyappfrontend.domain.model.TestResponse
import com.example.beautyappfrontend.domain.model.Specialist
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface BeautyApi {

    // Твій старий метод для тесту
    @GET("api/test/")
    suspend fun checkConnection(): TestResponse

    // Новий метод для отримання майстрів
    @GET("api/masters/")
    suspend fun getSpecialists(): List<Specialist>

    // {id}/ ad id when it's ready
    @GET("api/test_resuts/")
    suspend fun getAnalysisResult(@Path("id") id: Int): Response<AnalysisResponse>
}