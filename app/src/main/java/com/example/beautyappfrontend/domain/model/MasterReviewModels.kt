package com.example.beautyappfrontend.domain.model

import com.google.gson.annotations.SerializedName

data class MasterReviewItem(
    val id: Int = 0,
    @SerializedName("author_name") val authorName: String = "",
    @SerializedName("author_avatar") val authorAvatar: String = "",
    val rating: Int = 0,
    val comment: String = "",
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("is_verified") val isVerified: Boolean = true,
    @SerializedName("report_count") val reportCount: Int = 0,
)

data class ReviewReportRequest(
    val reason: String,
    val text: String = "",
)

data class MasterReviewsEnvelope(
    val results: List<MasterReviewItem> = emptyList(),
    val count: Int = 0,
    val average: Double? = null,
    @SerializedName("can_review") val canReview: Boolean = false,
    @SerializedName("your_review") val yourReview: MasterReviewItem? = null,
)

data class MasterReviewWriteRequest(
    val rating: Int,
    val comment: String = "",
)
