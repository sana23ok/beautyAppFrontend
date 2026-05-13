package com.example.beautyappfrontend.data.repository

import android.content.Context
import com.example.beautyappfrontend.data.remote.RetrofitInstance
import com.example.beautyappfrontend.domain.model.FavoriteToggleRequest
import com.example.beautyappfrontend.domain.model.Specialist
import com.example.beautyappfrontend.utils.SessionManager

/**
 * Server-backed favorite masters with an in-memory cache for fast UI reads.
 */
object FavoriteMastersRepository {

    private lateinit var appCtx: Context

    @Volatile
    private var cached: List<Specialist> = emptyList()

    fun init(applicationContext: Context) {
        appCtx = applicationContext.applicationContext
    }

    fun clearCache() {
        cached = emptyList()
    }

    fun snapshot(): List<Specialist> = cached

    fun isFavorite(id: Int): Boolean = cached.any { it.id == id }

    private fun bearer(): String? {
        val t = SessionManager(appCtx).getToken()?.trim().orEmpty()
        if (t.isEmpty()) return null
        return "Bearer $t"
    }

    suspend fun sync(): Result<Unit> {
        val hdr = bearer() ?: run {
            cached = emptyList()
            return Result.success(Unit)
        }
        return try {
            val resp = RetrofitInstance.api.getFavoriteMasters(hdr)
            if (!resp.isSuccessful) {
                val err = resp.errorBody()?.string().orEmpty().ifBlank { "HTTP ${resp.code()}" }
                return Result.failure(Exception(err))
            }
            cached = resp.body().orEmpty()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggle(masterId: Int): Result<Boolean> {
        val hdr = bearer() ?: return Result.failure(IllegalStateException("Not logged in"))
        return try {
            val resp = RetrofitInstance.api.toggleFavoriteMaster(hdr, FavoriteToggleRequest(masterId))
            if (!resp.isSuccessful) {
                val err = resp.errorBody()?.string().orEmpty().ifBlank { "HTTP ${resp.code()}" }
                return Result.failure(Exception(err))
            }
            val body = resp.body() ?: return Result.failure(Exception("Empty response"))
            sync()
            Result.success(body.isFavorite)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun remove(masterId: Int): Result<Unit> {
        val hdr = bearer() ?: return Result.failure(IllegalStateException("Not logged in"))
        return try {
            val resp = RetrofitInstance.api.deleteFavoriteMaster(hdr, masterId)
            if (!resp.isSuccessful && resp.code() != 404) {
                val err = resp.errorBody()?.string().orEmpty().ifBlank { "HTTP ${resp.code()}" }
                return Result.failure(Exception(err))
            }
            sync()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
