package com.example.beautyappfrontend

import android.app.Application
import com.example.beautyappfrontend.data.remote.RetrofitInstance

class BeautyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RetrofitInstance.init(applicationContext)
    }
}
