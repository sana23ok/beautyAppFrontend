package com.example.beautyappfrontend.data.remote

import android.content.Context
import com.example.beautyappfrontend.BuildConfig
import com.example.beautyappfrontend.utils.SessionManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {

    private lateinit var appContext: Context

    /**
     * Call from [com.example.beautyappfrontend.BeautyApp.onCreate] before any Retrofit usage.
     */
    fun init(applicationContext: Context) {
        appContext = applicationContext.applicationContext
    }

    private fun session(): SessionManager = SessionManager(appContext)

    private val BASE_URL: String = BuildConfig.API_BASE_URL

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .authenticator(TokenAuthenticator(session()))
            .build()
    }

    val api: BeautyApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BeautyApi::class.java)
    }
}
