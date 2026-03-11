package com.example.beautyappfrontend.domain.model

import com.google.gson.annotations.SerializedName

data class AnalysisResponse(
    @SerializedName("client") val client: ClientInfo,
    @SerializedName("analysis_result") val analysisResult: AnalysisResult,
    @SerializedName("look_alike_style") val styleDescription: String
)

data class ClientInfo(
    val id: Int,
    val name: String,
    @SerializedName("image_url") val imageUrl: String?,
    val location: String,
    val age: Int
    // add attributes if needed
)

data class AnalysisResult(
    @SerializedName("color_type") val colorType: ColorType,
    @SerializedName("body_type") val bodyType: BodyType
)

data class ColorType(
    val season: String,
    val description: String,
    val palette: List<String>, // list of hex codes ["#808000", ...]
    val advice: Advice
)

data class BodyType(
    val shape: String,
    val description: String,
    val advice: Advice
)

data class Advice(
    @SerializedName("best_colors") val best: List<String>?, // For ColorType
    @SerializedName("least_colors") val avoid: List<String>?, // For ColorType
    @SerializedName("best_clothes") val bestClothes: List<String>?, // For BodyType
    @SerializedName("avoid_clothes") val avoidClothes: List<String>? // For BodyType
)