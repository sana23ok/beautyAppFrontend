package com.example.beautyappfrontend.domain.model

data class Specialist(
    val id: Int,
    val name: String,           // Наприклад: "Анна Іваненко"
    val specialization: String, // Наприклад: "Перукар-стиліст"
    val rating: Double,         // Наприклад: 4.9
    val imageUrl: String        // URL фото профілю
)

data class TestResponse(
    val message: String,
    val status: String
)