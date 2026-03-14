package com.example.beautyappfrontend.data.remote

import com.example.beautyappfrontend.domain.model.AnalysisResponse
import com.example.beautyappfrontend.domain.model.AppearanceTestRequest
import com.example.beautyappfrontend.domain.model.AppearanceTestResponse
import com.example.beautyappfrontend.domain.model.AuthResponse
import com.example.beautyappfrontend.domain.model.AuthUserInfo
import com.example.beautyappfrontend.domain.model.ConversationDetailResponse
import com.example.beautyappfrontend.domain.model.ConversationResponse
import com.example.beautyappfrontend.domain.model.GoogleAuthRequest
import com.example.beautyappfrontend.domain.model.LoginRequest
import com.example.beautyappfrontend.domain.model.MasterProfileRequest
import com.example.beautyappfrontend.domain.model.MasterProfileResponse
import com.example.beautyappfrontend.domain.model.MessageResponse
import com.example.beautyappfrontend.domain.model.RegisterRequest
import com.example.beautyappfrontend.domain.model.SendMessageRequest
import com.example.beautyappfrontend.domain.model.Specialist
import com.example.beautyappfrontend.domain.model.StartConversationRequest
import com.example.beautyappfrontend.domain.model.TestResponse
import com.example.beautyappfrontend.domain.model.UserProfileUpdateRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

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

    @GET("api/masters/{id}/")
    suspend fun getMasterProfile(@Path("id") id: Int): Response<MasterProfileResponse>

    @POST("api/appearance_test/analyse/")
    suspend fun submitAppearanceTest(@Body request: AppearanceTestRequest): Response<AppearanceTestResponse>

    // Chat API
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
}