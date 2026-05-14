package com.example.beautyappfrontend.domain.model

import com.google.gson.annotations.SerializedName

/** Keys match recommendations.csv labels exactly (POST /api/appearance_test/analyse/). */
data class AppearanceTestRequest(
    @SerializedName("hair_color") val hairColor: String,
    @SerializedName("eyes_color") val eyesColor: String,
    @SerializedName("skin_tone") val skinTone: String,
    val undertone: String,
    @SerializedName("torso_length") val torsoLength: String,
    @SerializedName("body_proportion") val bodyProportion: String,
    @SerializedName("preferred_style") val preferredStyle: String? = null,
    val goals: List<String>? = null,
    @SerializedName("body_measurements") val bodyMeasurements: BodyMeasurements? = null,
    @SerializedName("user_city") val userCity: String? = null,
)

data class BodyMeasurements(
    val bust: Int?,
    val waist: Int?,
    val hips: Int?,
)

data class ExtendedRecommendations(
    @SerializedName("recommended_clothing_colors") val recommendedClothingColors: String = "",
    @SerializedName("avoid_clothing_colors") val avoidClothingColors: String = "",
    @SerializedName("recommended_fitting_style") val recommendedFittingStyle: String = "",
    @SerializedName("recommended_materials") val recommendedMaterials: String = "",
    @SerializedName("recommended_patterns") val recommendedPatterns: String = "",
    @SerializedName("recommended_jewelry_metal") val recommendedJewelryMetal: String = "",
    @SerializedName("recommended_shoes") val recommendedShoes: String = "",
    @SerializedName("recommended_color_wheel_region") val recommendedColorWheelRegion: String = "",
    @SerializedName("avoid_color_wheel_region") val avoidColorWheelRegion: String = "",
    @SerializedName("fabric_nature") val fabricNature: String = "",
    @SerializedName("dont_exaggerate") val dontExaggerate: String = "",
    @SerializedName("do_exaggerate") val doExaggerate: String = "",
)

data class RecommendedMaster(
    val id: Int,
    val name: String,
    val specialization: String,
    @SerializedName("profile_photo") val profilePhoto: String?,
    val rating: Double,
    val city: String?,
)

data class AppearanceTestResponse(
    @SerializedName("analysis_result") val analysisResult: AnalysisResult,
    @SerializedName("look_alike_style") val styleDescription: String,
    @SerializedName("inputs_summary") val inputsSummary: String? = null,
    @SerializedName("extended_recommendations") val extendedRecommendations: ExtendedRecommendations? = null,
    @SerializedName("recommended_masters") val recommendedMasters: List<RecommendedMaster>? = null,
    @SerializedName("calculated_body_shape") val calculatedBodyShape: String? = null,
)
