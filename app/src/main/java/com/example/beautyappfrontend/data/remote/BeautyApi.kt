package com.example.beautyappfrontend.data.remote

import com.example.beautyappfrontend.domain.model.TestResponse
import com.example.beautyappfrontend.domain.model.Specialist
import retrofit2.Response
import retrofit2.http.GET

interface BeautyApi {

    // Твій старий метод для тесту
    @GET("api/test/")
    suspend fun checkConnection(): TestResponse

    // Новий метод для отримання майстрів
    @GET("api/specialists/")
    suspend fun getSpecialists(): List<Specialist>
}