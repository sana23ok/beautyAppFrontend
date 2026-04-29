package com.example.beautyappfrontend.ui.screens

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.data.remote.RetrofitInstance
import coil.load
import com.example.beautyappfrontend.BuildConfig
import com.example.beautyappfrontend.databinding.ActivityHomeBinding
import com.example.beautyappfrontend.domain.model.AppearanceTestRequest
import com.example.beautyappfrontend.domain.model.AppearanceTestResponse
import com.example.beautyappfrontend.domain.model.ExtendedRecommendations
import com.example.beautyappfrontend.utils.ChatBadgeHelper
import com.example.beautyappfrontend.utils.OutfitIdeasHelper
import com.example.beautyappfrontend.utils.SessionManager
import com.google.gson.Gson
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    private data class QuizOption(val label: String, val value: String)
    private data class Question(val key: String, val options: List<QuizOption>)

    private val gson = Gson()
    private lateinit var sessionManager: SessionManager
    private val selectedAnswers = mutableMapOf<String, String>()
    private val chipsByQuestion = mutableMapOf<String, MutableList<TextView>>()

    private var hasSavedResult = false
    private var isTestExpanded = true
    private var isResultsExpanded = false

    /** CSV labels must match backend recommendations.csv exactly. */
    private val questions = listOf(
        Question(
            key = "hair_color",
            options = listOf(
                QuizOption("Black", "Black"),
                QuizOption("Brown", "Brown"),
                QuizOption("Red", "Red"),
                QuizOption("Blonde", "Blonde"),
                QuizOption("Grey", "Grey"),
            ),
        ),
        Question(
            key = "eyes_color",
            options = listOf(
                QuizOption("Brown", "Brown"),
                QuizOption("Green", "Green"),
                QuizOption("Blue", "Blue"),
                QuizOption("Hazel", "Hazel"),
                QuizOption("Grey", "Grey"),
                QuizOption("Black", "Black"),
                QuizOption("Light Brown", "Light Brown"),
                QuizOption("Light Blue", "Light Blue"),
            ),
        ),
        Question(
            key = "skin_tone",
            options = listOf(
                QuizOption("Very Fair", "Very Fair"),
                QuizOption("Fair", "Fair"),
                QuizOption("Medium", "Medium"),
                QuizOption("Olive", "Olive"),
                QuizOption("Brown", "Brown"),
                QuizOption("Very Dark", "Very Dark"),
            ),
        ),
        Question(
            key = "undertone",
            options = listOf(
                QuizOption("Warm", "Warm"),
                QuizOption("Cool", "Cool"),
                QuizOption("Neutral", "Neutral"),
            ),
        ),
        Question(
            key = "torso_length",
            options = listOf(
                QuizOption("Short Torso", "Short Torso"),
                QuizOption("Long Torso", "Long Torso"),
                QuizOption("Balanced", "Balanced"),
            ),
        ),
        Question(
            key = "body_proportion",
            options = listOf(
                QuizOption("Rectangle", "Rectangle"),
                QuizOption("Inverted Triangle", "Inverted Triangle"),
                QuizOption("Triangle", "Triangle"),
                QuizOption("Oval", "Oval"),
                QuizOption("Trapezoid", "Trapezoid"),
                QuizOption("Hourglass", "Hourglass"),
                QuizOption("Apple", "Apple"),
            ),
        ),
    )

    private val containers: List<LinearLayout> by lazy {
        listOf(
            binding.containerQ1,
            binding.containerQ2,
            binding.containerQ3,
            binding.containerQ4,
            binding.containerQ5,
            binding.containerQ6,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sessionManager = SessionManager(this)

        buildQuestions()
        setupSectionToggles()
        restoreSavedState()

        binding.btnAnalyse.setOnClickListener { onAnalyseClicked() }

        binding.ivProfileIcon.setOnClickListener {
            navigateTo(ProfileActivity::class.java)
        }

        setupBottomNav()
    }

    override fun onResume() {
        super.onResume()
        ChatBadgeHelper.updateBadge(binding.bottomNav, sessionManager.getToken(), lifecycleScope)
    }

    private fun buildQuestions() {
        questions.forEachIndexed { index, question ->
            val container = containers[index]
            val questionChips = mutableListOf<TextView>()

            question.options.forEach { option ->
                val chip = layoutInflater.inflate(
                    R.layout.item_quiz_chip,
                    container,
                    false,
                ) as TextView

                chip.text = option.label
                chip.tag = option.value
                chip.setOnClickListener {
                    selectChip(container, chip, question.key, option.value)
                }

                questionChips += chip
                container.addView(chip)
            }

            chipsByQuestion[question.key] = questionChips
        }
    }

    private fun setupSectionToggles() {
        binding.headerTest.setOnClickListener {
            isTestExpanded = !isTestExpanded
            updateSectionState()
        }
        binding.headerResults.setOnClickListener {
            isResultsExpanded = !isResultsExpanded
            updateSectionState()
        }
        updateSectionState()
    }

    private fun updateSectionState() {
        binding.headerResults.visibility = if (hasSavedResult) View.VISIBLE else View.GONE
        binding.testContent.visibility = if (isTestExpanded) View.VISIBLE else View.GONE
        binding.resultsContent.visibility = if (hasSavedResult && isResultsExpanded) View.VISIBLE else View.GONE
        binding.ivTestChevron.rotation = if (isTestExpanded) 90f else 0f
        binding.ivResultsChevron.rotation = if (isResultsExpanded) 90f else 0f
        binding.btnAnalyse.text = if (hasSavedResult) "Retake test" else "Analyze results"
    }

    private fun selectChip(
        container: LinearLayout,
        selected: TextView,
        questionKey: String,
        value: String,
    ) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) as? TextView ?: continue
            child.setBackgroundResource(R.drawable.bg_option_box)
        }
        selected.setBackgroundResource(R.drawable.bg_option_selected)
        selectedAnswers[questionKey] = value
    }

    private fun onAnalyseClicked() {
        val missing = questions.map { it.key }.filter { it !in selectedAnswers }
        if (missing.isNotEmpty()) {
            Toast.makeText(this, "Please answer all questions", Toast.LENGTH_SHORT).show()
            return
        }

        val request = AppearanceTestRequest(
            hairColor = selectedAnswers["hair_color"]!!,
            eyesColor = selectedAnswers["eyes_color"]!!,
            skinTone = selectedAnswers["skin_tone"]!!,
            undertone = selectedAnswers["undertone"]!!,
            torsoLength = selectedAnswers["torso_length"]!!,
            bodyProportion = selectedAnswers["body_proportion"]!!,
        )

        submitToBackend(request)
    }

    private fun submitToBackend(request: AppearanceTestRequest) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnAnalyse.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.submitAppearanceTest(request)
                if (response.isSuccessful) {
                    val result = response.body()
                    if (result != null) {
                        saveLatestState(request, result)
                        hasSavedResult = true
                        renderResult(result)
                        isTestExpanded = false
                        isResultsExpanded = true
                        updateSectionState()
                        Toast.makeText(
                            this@HomeActivity,
                            "Result saved on Home",
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        Toast.makeText(this@HomeActivity, "Empty response from server", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val msg = response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                        ?: "Server error: ${response.code()}"
                    Toast.makeText(this@HomeActivity, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@HomeActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnAnalyse.isEnabled = true
            }
        }
    }

    private fun saveLatestState(
        request: AppearanceTestRequest,
        result: AppearanceTestResponse,
    ) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(getLastRequestKey(), gson.toJson(request))
            .putString(getLastResultKey(), gson.toJson(result))
            .apply()
    }

    private fun restoreSavedState() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        hasSavedResult = false

        prefs.getString(getLastRequestKey(), null)?.let { json ->
            runCatching {
                gson.fromJson(json, AppearanceTestRequest::class.java)
            }.getOrNull()?.let { request ->
                restoreAnswers(request)
            }
        }

        prefs.getString(getLastResultKey(), null)?.let { json ->
            runCatching {
                gson.fromJson(json, AppearanceTestResponse::class.java)
            }.getOrNull()?.let { result ->
                hasSavedResult = true
                renderResult(result)
                isTestExpanded = false
                isResultsExpanded = true
                updateSectionState()
            }
        }

        if (!hasSavedResult) {
            binding.tvTestSubtitle.text = "Answer six questions for your personalised palette"
            binding.tvResultsSubtitle.text = "No result yet"
            binding.tvNoResults.visibility = View.VISIBLE
            binding.resultsDetails.visibility = View.GONE
            isTestExpanded = true
            isResultsExpanded = false
            updateSectionState()
        }
    }

    private fun restoreAnswers(request: AppearanceTestRequest) {
        selectedAnswers["hair_color"] = request.hairColor
        selectedAnswers["eyes_color"] = request.eyesColor
        selectedAnswers["skin_tone"] = request.skinTone
        selectedAnswers["undertone"] = request.undertone
        selectedAnswers["torso_length"] = request.torsoLength
        selectedAnswers["body_proportion"] = request.bodyProportion

        updateSelectedChip("hair_color", request.hairColor)
        updateSelectedChip("eyes_color", request.eyesColor)
        updateSelectedChip("skin_tone", request.skinTone)
        updateSelectedChip("undertone", request.undertone)
        updateSelectedChip("torso_length", request.torsoLength)
        updateSelectedChip("body_proportion", request.bodyProportion)
    }

    private fun updateSelectedChip(questionKey: String, selectedValue: String) {
        chipsByQuestion[questionKey]?.forEach { chip ->
            val isSelected = chip.tag == selectedValue
            chip.setBackgroundResource(
                if (isSelected) R.drawable.bg_option_selected else R.drawable.bg_option_box,
            )
        }
    }

    private fun renderResult(result: AppearanceTestResponse) {
        val colorType = result.analysisResult.colorType
        val bodyType = result.analysisResult.bodyType

        hasSavedResult = true
        binding.tvResultsSubtitle.text = "Latest result saved"
        binding.tvTestSubtitle.text = "Your latest result is saved below"
        binding.tvNoResults.visibility = View.GONE
        binding.resultsDetails.visibility = View.VISIBLE

        binding.tvStyleSummary.text = result.styleDescription

        result.inputsSummary?.takeIf { it.isNotBlank() }?.let {
            binding.tvInputsSummary.visibility = View.VISIBLE
            binding.tvInputsSummary.text = it
        } ?: run {
            binding.tvInputsSummary.visibility = View.GONE
        }

        binding.tvSeasonName.text = "${colorType.season} (${colorType.description})"
        binding.tvBodyShape.text = "${bodyType.shape} (${bodyType.description})"

        val bestColors = colorType.advice.best?.joinToString(", ") ?: "No data"
        binding.tvColorAdvice.text = "Best colours:\n$bestColors"

        val avoidLine = colorType.advice.avoid?.joinToString(", ").orEmpty()
        val clothesLines = bodyType.advice.bestClothes?.joinToString("\n• ").orEmpty()
        val avoidBody = bodyType.advice.avoidClothes?.joinToString("\n• ").orEmpty()

        binding.tvBodyAdvice.text = buildString {
            append("Avoid colours:\n• ")
            append(avoidLine.ifBlank { "—" })
            append("\n\nWhat to wear:\n• ")
            append(clothesLines.ifBlank { "—" })
            if (avoidBody.isNotBlank()) {
                append("\n\nAvoid styling:\n• ")
                append(avoidBody)
            }
        }

        renderPalette(colorType.palette)

        renderOutfitIdeas(bodyType.shape)

        result.extendedRecommendations?.let { ext ->
            binding.cardDetailRecommendations.visibility = View.VISIBLE
            binding.tvDetailRecommendations.text = formatExtendedRecommendations(ext)
        } ?: run {
            binding.cardDetailRecommendations.visibility = View.GONE
        }
    }

    private fun formatExtendedRecommendations(ext: ExtendedRecommendations): String = buildString {
        appendLine("Recommended colours")
        appendLine(ext.recommendedClothingColors)
        appendLine()
        appendLine("Colours to avoid")
        appendLine(ext.avoidClothingColors)
        appendLine()
        appendLine("Fit & silhouette")
        appendLine(ext.recommendedFittingStyle)
        appendLine()
        appendLine("Materials & patterns")
        appendLine(ext.recommendedMaterials)
        appendLine(ext.recommendedPatterns)
        appendLine()
        appendLine("Accessories")
        appendLine("${ext.recommendedJewelryMetal} · ${ext.recommendedShoes}")
        appendLine()
        appendLine("Colour wheel")
        appendLine(ext.recommendedColorWheelRegion)
        appendLine(ext.avoidColorWheelRegion)
        appendLine()
        appendLine("Fabric feel")
        appendLine(ext.fabricNature)
        appendLine()
        appendLine("Balance")
        appendLine("${ext.doExaggerate}\n${ext.dontExaggerate}")
    }

    private fun renderOutfitIdeas(apiBodyShape: String) {
        binding.containerOutfitPhotos.removeAllViews()

        val manifestKey = OutfitIdeasHelper.manifestKeyForShape(apiBodyShape)
        val entries =
            if (manifestKey != null) OutfitIdeasHelper.photosForShape(this, apiBodyShape) else emptyList()

        if (manifestKey == null || entries.isEmpty()) {
            binding.cardOutfitIdeas.visibility = View.GONE
            binding.tvOutfitCloudinaryHint.visibility = View.GONE
            return
        }

        val base = BuildConfig.CLOUDINARY_OUTFIT_BASE_URL.trim()
        binding.cardOutfitIdeas.visibility = View.VISIBLE

        if (base.isEmpty()) {
            binding.tvOutfitCloudinaryHint.visibility = View.VISIBLE
            binding.tvOutfitCloudinaryHint.text =
                "Add cloudinary.outfit.base.url to local.properties — delivery URL prefix for outfit images (paths match assets/outfits_manifest.json)."
            return
        }

        binding.tvOutfitCloudinaryHint.visibility = View.GONE

        val prefix = base.trimEnd('/')
        for (entry in entries) {
            val row =
                layoutInflater.inflate(R.layout.item_outfit_photo, binding.containerOutfitPhotos, false)
            row.findViewById<TextView>(R.id.tv_outfit_title).text = entry.item
            val iv = row.findViewById<ImageView>(R.id.iv_outfit)
            val url = "$prefix/${entry.image}"
            iv.load(url) {
                crossfade(true)
            }
            binding.containerOutfitPhotos.addView(row)
        }
    }

    private fun renderPalette(colors: List<String>) {
        binding.paletteContainer.removeAllViews()

        for (hexColor in colors) {
            val colorView = View(this)
            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f,
            )
            params.setMargins(4, 0, 4, 0)
            colorView.layoutParams = params

            try {
                colorView.setBackgroundColor(Color.parseColor(hexColor))
            } catch (_: IllegalArgumentException) {
                colorView.setBackgroundColor(Color.GRAY)
            }

            binding.paletteContainer.addView(colorView)
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_search -> {
                    navigateTo(SearchPageActivity::class.java)
                    true
                }
                R.id.nav_chat -> {
                    navigateTo(ChatActivity::class.java)
                    true
                }
                R.id.nav_profile -> {
                    navigateTo(ProfileActivity::class.java)
                    true
                }
                else -> false
            }
        }
    }

    private fun <T> navigateTo(cls: Class<T>) {
        val intent = Intent(this, cls)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val options = ActivityOptions.makeCustomAnimation(this, 0, 0)
        startActivity(intent, options.toBundle())
    }

    companion object {
        private const val PREFS_NAME = "appearance_test_prefs"
        private const val KEY_LAST_REQUEST = "last_request"
        private const val KEY_LAST_RESULT = "last_result"
    }

    private fun getCurrentUserKey(): String {
        val username = sessionManager.getUsername().trim()
        if (username.isNotEmpty()) return "user_$username"

        val email = sessionManager.getEmail().trim()
        if (email.isNotEmpty()) return "user_$email"

        return "user_anonymous"
    }

    private fun getLastRequestKey(): String = "${KEY_LAST_REQUEST}_${getCurrentUserKey()}"

    private fun getLastResultKey(): String = "${KEY_LAST_RESULT}_${getCurrentUserKey()}"
}
