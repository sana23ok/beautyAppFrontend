package com.example.beautyappfrontend.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Loads [assets/outfits_manifest.json] built from `outfits_by_body_type.csv`.
 * Full image URL = BuildConfig.CLOUDINARY_OUTFIT_BASE_URL + "/" + entry.image
 *
 * Calculated shape → folder: Inverted Triangle uses Column (same outfit set as Rectangle).
 */
object OutfitIdeasHelper {

    private val gson = Gson()
    private var cached: Map<String, List<OutfitPhotoEntry>>? = null

    data class OutfitPhotoEntry(val item: String, val image: String)

    fun manifest(context: Context): Map<String, List<OutfitPhotoEntry>> {
        cached?.let { return it }
        val json = context.assets.open("outfits_manifest.json").bufferedReader().use { it.readText() }
        val type = object : TypeToken<Map<String, List<OutfitPhotoEntry>>>() {}.type
        cached = gson.fromJson(json, type)
        return cached!!
    }

    /** Maps calculated/API body shape to manifest folder key. */
    fun manifestKeyForShape(apiShape: String): String? =
        when (apiShape.trim()) {
            "Hourglass" -> "Hourglass"
            "Apple" -> "Apple"
            "Pear" -> "Pear"
            "Column" -> "Column"
            "Inverted Triangle" -> "Column"
            "Bump_Friendly", "Bump Friendly" -> "Bump Friendly"
            "Rectangle", "Trapezoid" -> "Column"
            "Triangle" -> "Pear"
            "Oval" -> "Apple"
            else -> null
        }

    fun photosForShape(context: Context, apiShape: String, limit: Int = 18): List<OutfitPhotoEntry> {
        val key = manifestKeyForShape(apiShape) ?: return emptyList()
        return manifest(context)[key].orEmpty().take(limit)
    }
}
