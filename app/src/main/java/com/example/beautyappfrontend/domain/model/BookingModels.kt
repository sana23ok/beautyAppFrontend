package com.example.beautyappfrontend.domain.model

import com.google.gson.annotations.SerializedName

data class AvailableSlotsResponse(
    @SerializedName("master_id") val masterId: Int,
    @SerializedName("service_id") val serviceId: Int,
    val date: String,
    val slots: List<String> = emptyList(),
)

data class BookingRequest(
    @SerializedName("master_id") val masterId: Int,
    @SerializedName("service_id") val serviceId: Int,
    @SerializedName("appointment_date") val appointmentDate: String,
    @SerializedName("start_time") val startTime: String,
    val notes: String = "",
)

data class BookingResponse(
    val id: Int,
    val client: Int = -1,
    val master: Int,
    @SerializedName("master_name") val masterName: String = "",
    @SerializedName("master_city") val masterCity: String = "",
    @SerializedName("master_address") val masterAddress: String = "",
    val service: Int,
    @SerializedName("service_name") val serviceName: String = "",
    @SerializedName("service_duration_minutes") val serviceDurationMinutes: Int = 0,
    @SerializedName("appointment_date") val appointmentDate: String,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    val status: String = "",
    val notes: String = "",
    @SerializedName("created_at") val createdAt: String = "",
)
