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

data class AppearanceTestResponse(
    @SerializedName("analysis_result") val analysisResult: AnalysisResult,
    @SerializedName("look_alike_style") val styleDescription: String,
    @SerializedName("inputs_summary") val inputsSummary: String? = null,
    @SerializedName("extended_recommendations") val extendedRecommendations: ExtendedRecommendations? = null,
)
