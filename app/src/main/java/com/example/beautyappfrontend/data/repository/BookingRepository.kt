package com.example.beautyappfrontend.data.repository

import com.example.beautyappfrontend.data.remote.RetrofitInstance
import com.example.beautyappfrontend.domain.model.AvailableSlotsResponse
import com.example.beautyappfrontend.domain.model.BookingRequest
import com.example.beautyappfrontend.domain.model.BookingResponse
import com.example.beautyappfrontend.domain.model.CancelBookingRequest

class BookingRepository {
    suspend fun getAvailableSlots(masterId: Int, serviceId: Int, date: String): AvailableSlotsResponse {
        val response = RetrofitInstance.api.getAvailableSlots(masterId, serviceId, date)
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Empty response from server")
        }
        throw Exception(response.errorBody()?.string() ?: "Failed to load available slots")
    }

    suspend fun createBooking(token: String, request: BookingRequest): BookingResponse {
        val response = RetrofitInstance.api.createBooking("Bearer $token", request)
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Empty response from server")
        }
        throw Exception(response.errorBody()?.string() ?: "Failed to create booking")
    }

    suspend fun getMasterBookings(masterId: Int, from: String, to: String): List<BookingResponse> {
        val response = RetrofitInstance.api.getMasterBookings(masterId, from, to)
        if (response.isSuccessful) {
            return response.body() ?: emptyList()
        }
        throw Exception(response.errorBody()?.string() ?: "Failed to load booked slots")
    }

    suspend fun getMyBookings(token: String): List<BookingResponse> {
        val response = RetrofitInstance.api.getMyBookings("Bearer $token")
        if (response.isSuccessful) {
            return response.body() ?: emptyList()
        }
        throw Exception(response.errorBody()?.string() ?: "Failed to load your appointments")
    }

    suspend fun cancelBooking(token: String, bookingId: Int, reason: String): BookingResponse {
        val response = RetrofitInstance.api.cancelBooking(
            "Bearer $token",
            bookingId,
            CancelBookingRequest(reason = reason),
        )
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Empty response from server")
        }
        throw Exception(response.errorBody()?.string() ?: "Failed to cancel booking")
    }
}
