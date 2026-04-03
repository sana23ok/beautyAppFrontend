package com.example.beautyappfrontend.data.remote

import com.example.beautyappfrontend.BuildConfig
import com.example.beautyappfrontend.utils.SessionManager
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.TimeUnit

/**
 * When the server returns 401 (e.g. expired access JWT), exchanges the refresh token for a new
 * access token and retries the request once. Requires [SessionManager.getRefreshToken] to be set
 * after login/register (see [SessionManager.saveTokens]).
 */
class TokenAuthenticator(
    private val session: SessionManager,
) : Authenticator {

    private val gson = Gson()
    private val refreshClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.contains("auth/refresh")) {
            return null
        }
        if (responseCount(response) >= 2) {
            return null
        }

        synchronized(lock) {
            val sessionAccess = session.getToken()
            val requestAuth = response.request.header("Authorization")
            val bearer = requestAuth?.removePrefix("Bearer")?.trim()
            if (!sessionAccess.isNullOrBlank() && bearer != null && sessionAccess != bearer) {
                return response.request.newBuilder()
                    .removeHeader("Authorization")
                    .header("Authorization", "Bearer $sessionAccess")
                    .build()
            }

            val refresh = session.getRefreshToken() ?: return null
            val newAccess = refreshAccessToken(refresh) ?: return null
            session.saveToken(newAccess)
            return response.request.newBuilder()
                .removeHeader("Authorization")
                .header("Authorization", "Bearer $newAccess")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var n = 1
        var p = response.priorResponse
        while (p != null) {
            n++
            p = p.priorResponse
        }
        return n
    }

    private fun refreshAccessToken(refresh: String): String? {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        val url = "$base/api/auth/refresh/"
        val bodyJson = gson.toJson(mapOf("refresh" to refresh))
        val body = bodyJson.toRequestBody(JSON)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "application/json")
            .build()
        return try {
            refreshClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val text = resp.body?.string() ?: return null
                gson.fromJson(text, RefreshBody::class.java).access
            }
        } catch (_: Exception) {
            null
        }
    }

    private data class RefreshBody(
        @SerializedName("access") val access: String? = null,
    )

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val lock = Any()
    }
}
