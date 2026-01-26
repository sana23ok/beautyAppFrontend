package com.example.beautyappfrontend.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    // Для емулятора Android Studio адреса localhost це 10.0.2.2
    private const val BASE_URL = "http://10.0.2.2:8000/"

    val api: BeautyApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BeautyApi::class.java)
    }
}