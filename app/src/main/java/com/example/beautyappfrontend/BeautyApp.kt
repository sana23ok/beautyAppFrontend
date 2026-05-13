package com.example.beautyappfrontend

import android.app.Application
import com.example.beautyappfrontend.data.remote.RetrofitInstance
import com.example.beautyappfrontend.data.repository.FavoriteMastersRepository

class BeautyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RetrofitInstance.init(applicationContext)
        FavoriteMastersRepository.init(applicationContext)
    }
}
