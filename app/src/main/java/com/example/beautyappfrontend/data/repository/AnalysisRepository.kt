package com.example.beautyappfrontend.data.repository

import com.example.beautyappfrontend.data.remote.RetrofitInstance
import com.example.beautyappfrontend.domain.model.AnalysisResponse

class AnalysisRepository {
    suspend fun getAnalysis(id: Int): AnalysisResponse? {
        val response = RetrofitInstance.api.getAnalysisResult(id)
        if (response.isSuccessful) {
            return response.body()
        }
        return null
    }
}