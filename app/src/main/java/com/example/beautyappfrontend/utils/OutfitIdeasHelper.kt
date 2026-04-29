package com.example.beautyappfrontend.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Loads [assets/outfits_manifest.json] built from `outfits_by_body_type.csv`.
 * Full image URL = BuildConfig.CLOUDINARY_OUTFIT_BASE_URL + "/" + entry.image
 *
 * Quiz/API body proportion labels map to CSV folders:
 * Hourglass, Apple → same; Rectangle, Trapezoid, Inverted Triangle → Column;
 * Triangle → Pear; Oval → Apple.
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

    /** Maps API/shape label from recommendations (quiz body proportion) to folders in CSV. */
    fun manifestKeyForShape(apiShape: String): String? =
        when (apiShape.trim()) {
            "Hourglass" -> "Hourglass"
            "Apple" -> "Apple"
            "Rectangle" -> "Column"
            "Triangle" -> "Pear"
            "Inverted Triangle" -> "Column"
            "Oval" -> "Apple"
            "Trapezoid" -> "Column"
            "Pear" -> "Pear"
            "Bump Friendly" -> "Bump Friendly"
            else -> null
        }

    fun photosForShape(context: Context, apiShape: String, limit: Int = 18): List<OutfitPhotoEntry> {
        val key = manifestKeyForShape(apiShape) ?: return emptyList()
        return manifest(context)[key].orEmpty().take(limit)
    }
}
