package com.example.beautyappfrontend.data.remote

import com.example.beautyappfrontend.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    // Port 8000 — same as: python manage.py runserver 0.0.0.0:8000
    // Emulator default: http://10.0.2.2:8000/  |  Real device: set api.base.url in local.properties
    private val BASE_URL: String = BuildConfig.API_BASE_URL

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