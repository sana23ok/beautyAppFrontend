package com.example.beautyappfrontend.utils

import java.util.Locale

/**
 * Maps clothing colour labels from [recommendations_final.csv] to display hex swatches.
 * Lookup order: exact → strip parenthetical → longest-substring.
 */
object ClothingColorSwatches {

    private const val FALLBACK = "#9E9E9E"

    // Keys are lowercase. Longer keys take priority in substring matching.
    private val hexByKey: Map<String, String> = listOf(
        // ── Jewel / cool winter palette ──────────────────────────────
        "jewel tones" to "#6B3FA0",
        "icy blue" to "#ADD8E6",
        "lavender" to "#E6E6FA",
        "silver" to "#C0C0C0",
        "emerald" to "#50C878",
        "fuchsia" to "#FF1493",
        "true red" to "#CC0000",
        "black" to "#1A1A1A",
        "stark black" to "#1A1A1A",
        "pure white" to "#F5F5F5",
        // ── Soft / light summer palette ──────────────────────────────
        "soft pinks" to "#FFB6C1",
        "plums" to "#8E4585",
        "teal" to "#008080",
        "neutral beige" to "#C4B7A6",
        "powder blue" to "#B0E0E6",
        "dusty rose" to "#BC8F8F",
        "soft periwinkle" to "#CCCCFF",
        "soft rose" to "#FFB7C5",
        "mauve" to "#E0B0FF",
        "dusty blue" to "#6699CC",
        "soft sage" to "#B2C8B2",
        // ── Autumn / warm palette ─────────────────────────────────────
        "earth tones" to "#8B7355",
        "olive" to "#808000",
        "coral" to "#FF7F50",
        "peach" to "#FFDAB9",
        "mustard" to "#FFDB58",
        "warm red" to "#CC3300",
        "dusty terracotta" to "#CC7A67",
        "terracotta" to "#E2725B",
        "camel" to "#C19A6B",
        "rust" to "#B7410E",
        "burnt orange" to "#CC5500",
        "golden brown" to "#996515",
        "gold" to "#FFD700",
        "rose gold" to "#B76E79",
        // ── Warm/neon avoids ─────────────────────────────────────────
        "orange" to "#FF8C00",
        "harsh yellow" to "#FFD700",
        "fluorescents" to "#CCFF00",
        "bright neons" to "#FF69B4",
        "bright warm neons" to "#FF8C00",
        "neon pink" to "#FF6EC7",
        "neon green" to "#39FF14",
        "electric blue" to "#7DF9FF",
        "lime green" to "#32CD32",
        // ── Muted / pastel avoids ────────────────────────────────────
        "muted colors" to "#A89890",
        "dusty pastels" to "#D8C8D0",
        "muddy browns" to "#6D4C41",
        "pale pastels" to "#E8D8E0",
        "bright pastels" to "#F4C2D8",
        "icy pastels" to "#E0F0FF",
        "icy winter pastels" to "#DCE8F0",
        "icy cool tones" to "#B8D8E8",
        "icy tones" to "#B8D8E8",
        "very pale wash-out pastels" to "#EEEAE8",
        "light dusty cool colors" to "#C8C4D0",
        // ── Dark / heavy avoids ──────────────────────────────────────
        "heavy dark colors" to "#2F2F3F",
        "dark brown" to "#5C3317",
        "dark cool burgundy" to "#6B1F3A",
        "burgundy" to "#800020",
        "wine" to "#722F37",
        // ── Earthy compound phrases ───────────────────────────────────
        "warm earthy tones" to "#8B6952",
        "earthy warm tones" to "#8B6952",
        "muted or earthy colors" to "#9A8570",
        "muted dusty colors" to "#A89890",
        "cool bright pinks" to "#FF85A1",
        "clear warm orange" to "#FF6600",
        "heavy dark" to "#2F2F3F",
        // ── Other common hues ────────────────────────────────────────
        "brown" to "#8B4513",
        "muddy brown" to "#5C4033",
        "sage" to "#9DC183",
        "navy" to "#000080",
        "royal blue" to "#4169E1",
        "ruby red" to "#E0115F",
        "hot pink" to "#FF69B4",
        "cool blue" to "#4682B4",
        "icy gray" to "#C0C0C0",
        "icy grey" to "#C0C0C0",
        "grey" to "#808080",
        "gray" to "#808080",
        "dark grey" to "#404040",
        "dark gray" to "#404040",
        "charcoal" to "#36454F",
        "blush pink" to "#DE5D83",
        "periwinkle" to "#CCCCFF",
        "seafoam" to "#A2D8C3",
        "jade" to "#00A86B",
        "mint" to "#98FF98",
        "taupe" to "#8B7D6B",
        "mushroom" to "#CFBA9F",
        "oatmeal" to "#D9CBBF",
        "ivory" to "#FFFFF0",
        "cream" to "#FFFDD0",
        "slate" to "#708090",
        "denim" to "#6F8FAF",
        "aubergine" to "#614051",
        "mahogany" to "#C04000",
        "chocolate brown" to "#7B3F00",
        "brick red" to "#CB4154",
    ).associate { (k, v) -> k.lowercase(Locale.US) to v }

    private val sortedKeys: List<String> by lazy {
        hexByKey.keys.sortedByDescending { it.length }
    }

    fun hexFor(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return FALLBACK
        val lower = t.lowercase(Locale.US)
        // 1. Exact
        hexByKey[lower]?.let { return it }
        // 2. Strip parenthetical suffix "xxx (yyy)" → try "xxx"
        val head = lower.substringBefore('(').trim().trimEnd(',').trim()
        if (head.isNotEmpty() && head != lower) hexByKey[head]?.let { return it }
        // 3. Longest-substring
        for (k in sortedKeys) {
            if (lower.contains(k)) return hexByKey.getValue(k)
        }
        return FALLBACK
    }

    /** Every word starts with a capital letter. */
    fun titleCase(phrase: String): String =
        phrase.trim()
            .split("\\s+".toRegex())
            .joinToString(" ") { w ->
                w.lowercase(Locale.getDefault())
                    .replaceFirstChar { c -> if (c.isLetter()) c.titlecase(Locale.getDefault()) else c.toString() }
            }
}
