package com.example.beautyappfrontend.domain.model

import com.google.gson.annotations.SerializedName

data class AppearanceTestRequest(
    val undertone: String,
    @SerializedName("hair_color") val hairColor: String,
    @SerializedName("eyes_color") val eyesColor: String,
    @SerializedName("tanning_reaction") val tanningReaction: String,
    val bust: Int,
    val waist: Int,
    val hips: Int
)

data class AppearanceTestResponse(
    @SerializedName("analysis_result") val analysisResult: AnalysisResult,
    @SerializedName("look_alike_style") val styleDescription: String
)
