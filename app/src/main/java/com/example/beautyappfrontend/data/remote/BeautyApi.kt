package com.example.beautyappfrontend.data.remote

import com.example.beautyappfrontend.domain.model.AnalysisResponse
import com.example.beautyappfrontend.domain.model.AppearanceTestRequest
import com.example.beautyappfrontend.domain.model.AppearanceTestResponse
import com.example.beautyappfrontend.domain.model.AuthResponse
import com.example.beautyappfrontend.domain.model.AuthUserInfo
import com.example.beautyappfrontend.domain.model.AvailableSlotsResponse
import com.example.beautyappfrontend.domain.model.BookingRequest
import com.example.beautyappfrontend.domain.model.BookingResponse
import com.example.beautyappfrontend.domain.model.CancelBookingRequest
import com.example.beautyappfrontend.domain.model.ConversationDetailResponse
import com.example.beautyappfrontend.domain.model.ConversationResponse
import com.example.beautyappfrontend.domain.model.GoogleAuthRequest
import com.example.beautyappfrontend.domain.model.LoginRequest
import com.example.beautyappfrontend.domain.model.MasterProfileRequest
import com.example.beautyappfrontend.domain.model.MasterProfileResponse
import com.example.beautyappfrontend.domain.model.MasterServiceRequest
import com.example.beautyappfrontend.domain.model.MasterServiceResponse
import com.example.beautyappfrontend.domain.model.MasterWeekTimetableResponse
import com.example.beautyappfrontend.domain.model.MasterWeekTimetableWriteRequest
import com.example.beautyappfrontend.domain.model.MasterWorkPhotoResponse
import com.example.beautyappfrontend.domain.model.MessageResponse
import com.example.beautyappfrontend.domain.model.RegisterRequest
import com.example.beautyappfrontend.domain.model.SendMessageRequest
import com.example.beautyappfrontend.domain.model.Specialist
import com.example.beautyappfrontend.domain.model.StartConversationRequest
import com.example.beautyappfrontend.domain.model.TestResponse
import com.example.beautyappfrontend.domain.model.MarkReadResponse
import com.example.beautyappfrontend.domain.model.UnreadTotalResponse
import com.example.beautyappfrontend.domain.model.AvatarUploadResponse
import com.example.beautyappfrontend.domain.model.UserProfileUpdateRequest
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface BeautyApi {

    @GET("api/test/")
    suspend fun checkConnection(): TestResponse

    @GET("api/masters/")
    suspend fun getSpecialists(): List<Specialist>

    @GET("api/masters/")
    suspend fun searchSpecialists(
        @retrofit2.http.Query("q")               query: String?          = null,
        @retrofit2.http.Query("location")        location: String?       = null,
        @retrofit2.http.Query("specialisation")  specialisation: String? = null,
        @retrofit2.http.Query("experience")      experience: String?     = null,
        @retrofit2.http.Query("price_max")       priceMax: Int?          = null,
        @retrofit2.http.Query("page")            page: Int?              = null
    ): Response<List<Specialist>>

    // TODO: add {id} to URL when backend is ready
    @GET("api/test_results/")
    suspend fun getAnalysisResult(@Path("id") id: Int): Response<AnalysisResponse>

    @POST("api/auth/login/")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/auth/register/")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/google/")
    suspend fun googleAuth(@Body request: GoogleAuthRequest): Response<AuthResponse>

    @GET("api/auth/me/")
    suspend fun getCurrentUser(@Header("Authorization") authHeader: String): Response<AuthUserInfo>

    @PATCH("api/auth/me/")
    suspend fun updateCurrentUser(
        @Header("Authorization") authHeader: String,
        @Body request: UserProfileUpdateRequest,
    ): Response<AuthUserInfo>

    @DELETE("api/auth/me/")
    suspend fun deleteCurrentUser(
        @Header("Authorization") authHeader: String,
    ): Response<Unit>

    @POST("api/auth/become_master/")
    suspend fun becomeMaster(
        @Header("Authorization") authHeader: String,
    ): Response<AuthUserInfo>

    @Multipart
    @POST("api/auth/upload_avatar/")
    suspend fun uploadAvatar(
        @Header("Authorization") authHeader: String,
        @Part photo: MultipartBody.Part,
    ): Response<AvatarUploadResponse>

    @POST("api/masters/")
    suspend fun createMasterProfile(
        @Header("Authorization") authHeader: String,
        @Body request: MasterProfileRequest,
    ): Response<MasterProfileResponse>

    @GET("api/masters/me/")
    suspend fun getMyMasterProfile(
        @Header("Authorization") authHeader: String,
    ): Response<MasterProfileResponse>

    @PATCH("api/masters/me/")
    suspend fun updateMyMasterProfile(
        @Header("Authorization") authHeader: String,
        @Body request: MasterProfileRequest,
    ): Response<MasterProfileResponse>

    @POST("api/masters/me/services/")
    suspend fun createMyService(
        @Header("Authorization") authHeader: String,
        @Body request: MasterServiceRequest,
    ): Response<MasterServiceResponse>

    @PATCH("api/masters/me/services/{id}/")
    suspend fun patchMyService(
        @Header("Authorization") authHeader: String,
        @Path("id") id: Int,
        @Body request: MasterServiceRequest,
    ): Response<MasterServiceResponse>

    @DELETE("api/masters/me/services/{id}/")
    suspend fun deleteMyService(
        @Header("Authorization") authHeader: String,
        @Path("id") id: Int,
    ): Response<Unit>

    @GET("api/masters/me/work_photos/")
    suspend fun getMyWorkPhotos(
        @Header("Authorization") authHeader: String,
    ): Response<List<MasterWorkPhotoResponse>>

    @Multipart
    @POST("api/masters/me/work_photos/")
    suspend fun uploadMyWorkPhoto(
        @Header("Authorization") authHeader: String,
        @Part photo: MultipartBody.Part,
    ): Response<MasterWorkPhotoResponse>

    @DELETE("api/masters/me/work_photos/{id}/")
    suspend fun deleteMyWorkPhoto(
        @Header("Authorization") authHeader: String,
        @Path("id") id: Int,
    ): Response<Unit>

    @GET("api/masters/me/week-schedules/")
    suspend fun getMyWeekSchedules(
        @Header("Authorization") authHeader: String,
    ): Response<List<MasterWeekTimetableResponse>>

    @POST("api/masters/me/week-schedules/")
    suspend fun createMyWeekSchedule(
        @Header("Authorization") authHeader: String,
        @Body body: MasterWeekTimetableWriteRequest,
    ): Response<MasterWeekTimetableResponse>

    @PATCH("api/masters/me/week-schedules/{id}/")
    suspend fun patchMyWeekSchedule(
        @Header("Authorization") authHeader: String,
        @Path("id") id: Int,
        @Body body: MasterWeekTimetableWriteRequest,
    ): Response<MasterWeekTimetableResponse>

    @GET("api/masters/{id}/")
    suspend fun getMasterProfile(@Path("id") id: Int): Response<MasterProfileResponse>

    @GET("api/bookings/available-slots/")
    suspend fun getAvailableSlots(
        @Query("master_id") masterId: Int,
        @Query("service_id") serviceId: Int,
        @Query("date") date: String,
    ): Response<AvailableSlotsResponse>

    @GET("api/bookings/master/{id}/")
    suspend fun getMasterBookings(
        @Path("id") masterId: Int,
        @Query("from") from: String,
        @Query("to") to: String,
    ): Response<List<BookingResponse>>

    @GET("api/bookings/my/")
    suspend fun getMyBookings(
        @Header("Authorization") authHeader: String,
    ): Response<List<BookingResponse>>

    @POST("api/bookings/")
    suspend fun createBooking(
        @Header("Authorization") authHeader: String,
        @Body request: BookingRequest,
    ): Response<BookingResponse>

    @POST("api/bookings/{id}/cancel/")
    suspend fun cancelBooking(
        @Header("Authorization") authHeader: String,
        @Path("id") bookingId: Int,
        @Body request: CancelBookingRequest,
    ): Response<BookingResponse>

    @POST("api/appearance_test/analyse/")
    suspend fun submitAppearanceTest(@Body request: AppearanceTestRequest): Response<AppearanceTestResponse>

    // Chat API
    @GET("api/chat/unread_total/")
    suspend fun getUnreadTotal(
        @Header("Authorization") authHeader: String,
    ): Response<UnreadTotalResponse>

    @GET("api/chat/conversations/")
    suspend fun getConversations(
        @Header("Authorization") authHeader: String,
    ): Response<List<ConversationResponse>>

    @POST("api/chat/conversations/")
    suspend fun startConversation(
        @Header("Authorization") authHeader: String,
        @Body request: StartConversationRequest,
    ): Response<ConversationDetailResponse>

    @GET("api/chat/conversations/{id}/")
    suspend fun getConversation(
        @Header("Authorization") authHeader: String,
        @Path("id") id: Int,
    ): Response<ConversationDetailResponse>

    @GET("api/chat/conversations/{id}/messages/")
    suspend fun getMessages(
        @Header("Authorization") authHeader: String,
        @Path("id") conversationId: Int,
    ): Response<List<MessageResponse>>

    @POST("api/chat/conversations/{id}/messages/")
    suspend fun sendMessage(
        @Header("Authorization") authHeader: String,
        @Path("id") conversationId: Int,
        @Body request: SendMessageRequest,
    ): Response<MessageResponse>

    @POST("api/chat/conversations/{id}/read/")
    suspend fun markMessagesRead(
        @Header("Authorization") authHeader: String,
        @Path("id") conversationId: Int,
    ): Response<MarkReadResponse>
}