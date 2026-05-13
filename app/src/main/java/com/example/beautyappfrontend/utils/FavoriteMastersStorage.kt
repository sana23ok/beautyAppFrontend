package com.example.beautyappfrontend.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-user, on-device favourite-masters list. Persists master ids and a
 * lightweight summary (name/specialization/photo/city/rating) so the favourites
 * tab can render immediately without a network round-trip to fetch each master.
 *
 * Storage key is scoped by current logged-in user (email/username) so multiple
 * accounts on the same device do not share lists.
 */
class FavoriteMastersStorage(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val session = SessionManager(context)

    data class Summary(
        val id: Int,
        val name: String,
        val specialization: String,
        val profilePhoto: String?,
        val city: String?,
        val rating: Double,
    )

    fun ids(): Set<Int> {
        val raw = prefs.getString(key(), null) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it).getInt("id") }.toSet()
        }.getOrDefault(emptySet())
    }

    fun isFavorite(id: Int): Boolean = ids().contains(id)

    fun list(): List<Summary> {
        val raw = prefs.getString(key(), null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { idx ->
                val o = arr.getJSONObject(idx)
                Summary(
                    id = o.getInt("id"),
                    name = o.optString("name"),
                    specialization = o.optString("specialization"),
                    profilePhoto = o.optString("profile_photo").takeIf { it.isNotBlank() },
                    city = o.optString("city").takeIf { it.isNotBlank() },
                    rating = o.optDouble("rating", 0.0),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Toggles state and returns true if the master is now a favourite. */
    fun toggle(summary: Summary): Boolean {
        val current = list().toMutableList()
        val existing = current.indexOfFirst { it.id == summary.id }
        return if (existing >= 0) {
            current.removeAt(existing)
            persist(current)
            false
        } else {
            current.add(0, summary)
            persist(current)
            true
        }
    }

    fun remove(id: Int) {
        persist(list().filterNot { it.id == id })
    }

    private fun persist(items: List<Summary>) {
        val arr = JSONArray()
        items.forEach { s ->
            val o = JSONObject()
            o.put("id", s.id)
            o.put("name", s.name)
            o.put("specialization", s.specialization)
            o.put("profile_photo", s.profilePhoto ?: "")
            o.put("city", s.city ?: "")
            o.put("rating", s.rating)
            arr.put(o)
        }
        prefs.edit().putString(key(), arr.toString()).apply()
    }

    private fun key(): String {
        val u = session.getUsername().trim().ifEmpty { session.getEmail().trim() }
        return if (u.isNotEmpty()) "$KEY_PREFIX$u" else "${KEY_PREFIX}anonymous"
    }

    companion object {
        private const val PREFS_NAME = "favorite_masters"
        private const val KEY_PREFIX = "fav_"
    }
}
