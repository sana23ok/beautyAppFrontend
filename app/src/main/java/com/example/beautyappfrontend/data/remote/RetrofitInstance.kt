package com.example.beautyappfrontend.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    // Для емулятора Android Studio адреса localhost це 10.0.2.2
    private const val BASE_URL = "http://10.0.2.2:8000/"

    // Logs every request URL + headers + body AND every response body to Logcat.
    // Filter Logcat by tag "OkHttp" to see the full HTTP traffic.
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    val api: BeautyApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BeautyApi::class.java)
    }
}