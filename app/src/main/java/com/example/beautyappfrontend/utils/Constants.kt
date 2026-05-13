package com.example.beautyappfrontend.utils

/**
 * User-facing body type label: maps API/calculated keys and strips parentheticals
 * (e.g. CSV leftovers "Triangle (Pear)" → "Triangle").
 */
fun bodyShapeLabelForDisplay(raw: String): String {
    val trimmed = raw.trim()
    val mapped = when (trimmed) {
        "Pear" -> "Triangle"
        "Column" -> "Rectangle"
        "Apple" -> "Round"
        else -> trimmed
    }
    return mapped
        .replace(Regex("\\s*\\([^)]*\\)"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
