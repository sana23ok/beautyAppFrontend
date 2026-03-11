package com.example.beautyappfrontend.data.repository

import com.example.beautyappfrontend.data.remote.BeautyApi
import com.example.beautyappfrontend.domain.model.Specialist

class SpecialistRepository(
    private val api: BeautyApi
) {
    suspend fun getSpecialists(): List<Specialist> {
        return api.getSpecialists()
    }
}
