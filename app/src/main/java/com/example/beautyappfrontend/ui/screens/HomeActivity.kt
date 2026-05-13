package com.example.beautyappfrontend.ui.screens

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.data.remote.RetrofitInstance
import coil.load
import com.example.beautyappfrontend.BuildConfig
import com.example.beautyappfrontend.databinding.ActivityHomeBinding
import com.example.beautyappfrontend.domain.model.AppearanceTestRequest
import com.example.beautyappfrontend.domain.model.AppearanceTestResponse
import com.example.beautyappfrontend.domain.model.BodyMeasurements
import com.example.beautyappfrontend.domain.model.ExtendedRecommendations
import com.example.beautyappfrontend.domain.model.RecommendedMaster
import com.example.beautyappfrontend.ui.OutfitGridAdapter
import com.example.beautyappfrontend.utils.ChatBadgeHelper
import com.example.beautyappfrontend.utils.ClothingColorSwatches
import com.example.beautyappfrontend.utils.FavoriteMastersStorage
import com.example.beautyappfrontend.utils.OutfitIdeasHelper
import com.example.beautyappfrontend.utils.SessionManager
import com.example.beautyappfrontend.utils.bodyShapeLabelForDisplay
import com.google.android.flexbox.FlexboxLayout
import com.google.gson.Gson
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    private data class QuizOption(val label: String, val value: String)
    private data class Question(val key: String, val options: List<QuizOption>, val multiSelect: Boolean = false)

    private val gson = Gson()
    private lateinit var sessionManager: SessionManager
    private lateinit var favoriteMasters: FavoriteMastersStorage
    private val selectedAnswers = mutableMapOf<String, String>()
    private val selectedGoals = mutableSetOf<String>()
    private val chipsByQuestion = mutableMapOf<String, MutableList<TextView>>()

    private var hasSavedResult = false
    private var isTestExpanded = true
    private var isResultsExpanded = false

    /** Cache for collapse/expand of Outfit Ideas card. */
    private var outfitIdeasFull: List<OutfitIdeasHelper.OutfitPhotoEntry> = emptyList()
    private var outfitsExpanded: Boolean = false
    private var currentMasters: List<RecommendedMaster> = emptyList()

    companion object {
        private const val PREFS_NAME = "appearance_test_prefs"
        private const val KEY_LAST_REQUEST = "last_request_v2"
        private const val KEY_LAST_RESULT = "last_result_v2"

        /** Initial grid size for the Outfit Ideas card; user can expand to full set. */
        private const val OUTFIT_PREVIEW_COUNT = 6
    }

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
            key = "preferred_style",
            options = listOf(
                QuizOption("Classic", "classic"),
                QuizOption("Vintage", "vintage"),
                QuizOption("Street Style", "street"),
                QuizOption("Minimalist", "minimalist"),
                QuizOption("Bohemian", "bohemian"),
                QuizOption("Sporty", "sporty"),
                QuizOption("Glamorous", "glamorous"),
                QuizOption("Romantic", "romantic"),
            ),
        ),
        Question(
            key = "goals",
            options = listOf(
                QuizOption("Change hairstyle", "hairstyle"),
                QuizOption("Improve style", "style"),
                QuizOption("Improve makeup", "makeup"),
                QuizOption("Improve nails", "nails"),
                QuizOption("Overall look", "overall"),
            ),
            multiSelect = true,
        ),
    )

    private val containers: List<LinearLayout> by lazy {
        listOf(
            binding.containerQ1,
            binding.containerQ2,
            binding.containerQ3,
            binding.containerQ4,
            binding.containerQ5,
            binding.containerQ8,
            binding.containerQ9,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sessionManager = SessionManager(this)
        favoriteMasters = FavoriteMastersStorage(this)

        buildQuestions()
        setupSectionToggles()
        restoreSavedState()

        binding.btnAnalyse.setOnClickListener { onAnalyseClicked() }

        binding.btnExploreMoreLooks.setOnClickListener {
            outfitsExpanded = !outfitsExpanded
            renderOutfitsGrid()
        }

        binding.ivProfileIcon.setOnClickListener {
            navigateTo(ProfileActivity::class.java)
        }

        setupBottomNav()
    }

    override fun onResume() {
        super.onResume()
        ChatBadgeHelper.updateBadge(binding.bottomNav, sessionManager.getToken(), lifecycleScope)
        if (currentMasters.isNotEmpty()) {
            renderRecommendedMasters(currentMasters)
        }
    }

    private fun buildQuestions() {
        questions.forEachIndexed { index, question ->
            if (index >= containers.size) return@forEachIndexed
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

                if (question.multiSelect) {
                    chip.setOnClickListener {
                        toggleMultiSelectChip(chip, question.key, option.value)
                    }
                } else {
                    chip.setOnClickListener {
                        selectChip(container, chip, question.key, option.value)
                    }
                }

                questionChips += chip
                container.addView(chip)
            }

            chipsByQuestion[question.key] = questionChips
        }
    }

    private fun toggleMultiSelectChip(chip: TextView, questionKey: String, value: String) {
        if (questionKey == "goals") {
            if (selectedGoals.contains(value)) {
                selectedGoals.remove(value)
                chip.setBackgroundResource(R.drawable.bg_option_box)
            } else {
                selectedGoals.add(value)
                chip.setBackgroundResource(R.drawable.bg_option_selected)
            }
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
        val requiredKeys = listOf("hair_color", "eyes_color", "skin_tone", "undertone", "torso_length")
        val missing = requiredKeys.filter { it !in selectedAnswers }
        if (missing.isNotEmpty()) {
            Toast.makeText(this, "Please answer all required questions (1-5)", Toast.LENGTH_SHORT).show()
            return
        }

        val bust = binding.etBust.text.toString().toIntOrNull()
        val waist = binding.etWaist.text.toString().toIntOrNull()
        val hips = binding.etHips.text.toString().toIntOrNull()

        if (bust == null || waist == null || hips == null) {
            Toast.makeText(this, "Please enter bust, waist and hips measurements", Toast.LENGTH_SHORT).show()
            return
        }

        val calculatedShape = calculateBodyShape(bust, waist, hips)
        val bodyProportion = shapeToBodyProportion(calculatedShape)

        val request = AppearanceTestRequest(
            hairColor = selectedAnswers["hair_color"]!!,
            eyesColor = selectedAnswers["eyes_color"]!!,
            skinTone = selectedAnswers["skin_tone"]!!,
            undertone = selectedAnswers["undertone"]!!,
            torsoLength = selectedAnswers["torso_length"]!!,
            bodyProportion = bodyProportion,
            preferredStyle = selectedAnswers["preferred_style"],
            goals = selectedGoals.toList().takeIf { it.isNotEmpty() },
            bodyMeasurements = BodyMeasurements(bust, waist, hips),
        )

        submitToBackend(request)
    }

    /**
     * B = bust, W = waist, H = hips (cm). Order: Hourglass → Pear → Inverted Triangle → Apple → Column.
     */
    private fun calculateBodyShape(bust: Int, waist: Int, hips: Int): String {
        if (bust == 0 || hips == 0) return "Column"

        if (Math.abs(bust - hips) <= 5 && bust - waist >= 18 && hips - waist >= 18) return "Hourglass"
        if (hips - bust >= 6 && hips - waist >= 15) return "Pear"
        if (bust - hips >= 6 && bust - waist >= 15) return "Inverted Triangle"
        if (waist >= bust - 5 && waist >= hips - 5) return "Apple"
        return "Column"
    }

    private fun shapeToBodyProportion(shape: String): String = when (shape) {
        "Pear" -> "Triangle"
        "Column" -> "Rectangle"
        else -> shape
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
            binding.tvTestSubtitle.text = "Answer five questions and enter measurements for your personalised result"
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
        request.preferredStyle?.let { selectedAnswers["preferred_style"] = it }

        updateSelectedChip("hair_color", request.hairColor)
        updateSelectedChip("eyes_color", request.eyesColor)
        updateSelectedChip("skin_tone", request.skinTone)
        updateSelectedChip("undertone", request.undertone)
        updateSelectedChip("torso_length", request.torsoLength)
        request.preferredStyle?.let { updateSelectedChip("preferred_style", it) }

        selectedGoals.clear()
        request.goals?.let { goals ->
            selectedGoals.addAll(goals)
            goals.forEach { goal -> updateMultiSelectChip("goals", goal) }
        }

        request.bodyMeasurements?.let { m ->
            m.bust?.let { binding.etBust.setText(it.toString()) }
            m.waist?.let { binding.etWaist.setText(it.toString()) }
            m.hips?.let { binding.etHips.setText(it.toString()) }
        }
    }

    private fun updateMultiSelectChip(questionKey: String, selectedValue: String) {
        chipsByQuestion[questionKey]?.forEach { chip ->
            if (chip.tag == selectedValue) {
                chip.setBackgroundResource(R.drawable.bg_option_selected)
            }
        }
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


        binding.tvSeasonName.text = colorType.season

        // Use the season-description blurb (do_colours) right under the season name
        // so the card matches the design mock: "Light Summer" followed by a tone
        // description, *then* the "Best colours for you" row.
        val descriptionText = colorType.doColours?.trim().orEmpty()
        binding.tvSeasonDescription.text = descriptionText
        binding.tvSeasonDescription.visibility =
            if (descriptionText.isEmpty()) View.GONE else View.VISIBLE
        binding.tvDoColours.visibility = View.GONE

        val rawShape = result.calculatedBodyShape ?: bodyType.shape
        binding.tvBodyShape.text = bodyShapeLabelForDisplay(rawShape)

        val bestRaw = colorType.advice.best.orEmpty()
        binding.tvBestColorsText.text = bestRaw.joinToString(", ") { ClothingColorSwatches.titleCase(it) }
        renderClothingSwatchRow(binding.paletteContainer, bestRaw)

        val avoidRaw = colorType.advice.avoid.orEmpty()
        binding.tvAvoidColorsText.text = avoidRaw.joinToString(", ") { ClothingColorSwatches.titleCase(it) }
        renderClothingSwatchRow(binding.avoidPaletteContainer, avoidRaw)

        val dontText = colorType.dontColours?.trim().orEmpty()
        binding.tvDontColours.text = dontText
        binding.tvDontColours.visibility = if (dontText.isEmpty()) View.GONE else View.VISIBLE

        renderWearChips(bodyType.advice.bestClothes)
        renderAvoidChips(bodyType.advice.avoidClothes)

        result.extendedRecommendations?.let { ext ->
            renderAccessoriesCard(ext)
        }

        renderOutfitIdeas(result.calculatedBodyShape ?: bodyType.shape)

        result.recommendedMasters?.let { masters ->
            renderRecommendedMasters(masters)
        }

        result.extendedRecommendations?.let { ext ->
//            binding.cardDetailRecommendations.visibility = View.VISIBLE
//            binding.tvDetailRecommendations.text = formatExtendedRecommendations(ext)
        } ?: run {
            binding.cardDetailRecommendations.visibility = View.GONE
        }
    }

    private fun renderClothingSwatchRow(container: LinearLayout, names: List<String>) {
        container.removeAllViews()
        if (names.isEmpty()) return
        for (name in names) {
            addColorSquare(container, ClothingColorSwatches.hexFor(name))
        }
    }

    private fun addColorSquare(container: LinearLayout, hexColor: String) {
        val size = (40 * resources.displayMetrics.density).toInt()
        val margin = (4 * resources.displayMetrics.density).toInt()
        val radius = (8 * resources.displayMetrics.density)

        val colorView = View(this)
        val params = LinearLayout.LayoutParams(size, size)
        params.setMargins(margin, 0, margin, 0)
        colorView.layoutParams = params

        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.cornerRadius = radius
        try {
            drawable.setColor(Color.parseColor(hexColor))
        } catch (_: Exception) {
            drawable.setColor(Color.GRAY)
        }
        colorView.background = drawable
        container.addView(colorView)
    }

    private fun renderWearChips(items: List<String>?) {
        binding.containerWearChips.removeAllViews()
        items?.filter { it.isNotBlank() }?.forEach { text ->
            addRecommendationChip(binding.containerWearChips, text, false)
        }
    }

    private fun renderAvoidChips(items: List<String>?) {
        binding.containerAvoidChips.removeAllViews()
        items?.filter { it.isNotBlank() }?.forEach { text ->
            addRecommendationChip(binding.containerAvoidChips, text, true)
        }
    }

    private fun addRecommendationChip(container: FlexboxLayout, text: String, isAvoid: Boolean) {
        val chip = TextView(this).apply {
            this.text = text
            setBackgroundResource(if (isAvoid) R.drawable.bg_chip_avoid else R.drawable.bg_chip_do)
            setTextColor(
                if (isAvoid) Color.parseColor("#5C1F1B") else Color.parseColor("#2F4D1E"),
            )
            textSize = 13f
            val padH = (12 * resources.displayMetrics.density).toInt()
            val padV = (8 * resources.displayMetrics.density).toInt()
            setPadding(padH, padV, padH, padV)
            val iconRes = if (isAvoid) R.drawable.ic_warning_triangle else R.drawable.ic_check_circle
            setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
            compoundDrawablePadding = (6 * resources.displayMetrics.density).toInt()
        }
        val lp = FlexboxLayout.LayoutParams(
            FlexboxLayout.LayoutParams.WRAP_CONTENT,
            FlexboxLayout.LayoutParams.WRAP_CONTENT,
        )
        lp.setMargins(0, 0, 8, 8)
        chip.layoutParams = lp
        container.addView(chip)
    }

    private fun renderAccessoriesCard(ext: ExtendedRecommendations) {
        val metalText = ext.recommendedJewelryMetal.takeIf { it.isNotBlank() }
        val shoesText = ext.recommendedShoes.takeIf { it.isNotBlank() }

        if (metalText == null && shoesText == null) {
            binding.cardAccessories.visibility = View.GONE
            return
        }

        binding.cardAccessories.visibility = View.VISIBLE

        binding.containerMetalColors.removeAllViews()
        val metalLower = metalText?.lowercase() ?: ""
        
        val metalData = when {
            metalLower.contains("rose") && metalLower.contains("gold") -> 
                listOf(
                    "rose_gold" to "Rose Gold",
                    "copper" to "Copper",
                    "brass" to "Brass"
                )
            metalLower.contains("gold") -> 
                listOf(
                    "gold" to "Gold",
                    "copper" to "Copper",
                    "brass" to "Brass"
                )
            metalLower.contains("silver") -> 
                listOf(
                    "silver" to "Silver",
                    "rose_gold" to "Rose Gold",
                    "white_gold" to "White Gold"
                )
            else -> 
                listOf(
                    "silver" to "Silver",
                    "gold" to "Gold",
                    "copper" to "Copper"
                )
        }

        val metalsBaseUrl = "https://res.cloudinary.com/dbbgctiio/image/upload/metals"
        for ((metalKey, _) in metalData) {
            addMetalImage(binding.containerMetalColors, "$metalsBaseUrl/$metalKey.png")
        }

        val metalLabels = metalData.map { it.second }.joinToString(", ")
        binding.tvMetalLabels.text = metalLabels

        binding.containerAccessoryChips.removeAllViews()
        shoesText?.let {
            addRecommendationChip(binding.containerAccessoryChips, "Shoes: $it", false)
        }
    }

    private fun addMetalImage(container: LinearLayout, imageUrl: String) {
        val size = (50 * resources.displayMetrics.density).toInt()
        val margin = (6 * resources.displayMetrics.density).toInt()

        val imageView = ImageView(this)
        val params = LinearLayout.LayoutParams(size, size)
        params.setMargins(margin, 0, margin, 0)
        imageView.layoutParams = params
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP

        imageView.load(imageUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_launcher_foreground)
            error(R.drawable.ic_launcher_foreground)
        }

        container.addView(imageView)
    }

    private fun renderRecommendedMasters(masters: List<RecommendedMaster>) {
        currentMasters = masters
        if (masters.isEmpty()) {
            binding.cardRecommendedMasters.visibility = View.GONE
            return
        }

        binding.cardRecommendedMasters.visibility = View.VISIBLE
        binding.tvMastersSubtitle.text = "Curated based on your preferences"
        binding.containerMasters.removeAllViews()

        val capped = masters.take(12)
        for (master in capped) {
            binding.containerMasters.addView(createMasterCard(master))
        }
    }

    private fun createMasterCard(master: RecommendedMaster): View {
        val view = layoutInflater.inflate(R.layout.item_recommended_master, binding.containerMasters, false)
        view.findViewById<TextView>(R.id.tvMasterName).text = master.name
        view.findViewById<TextView>(R.id.tvMasterSpec).text =
            master.specialization.ifBlank { master.city.orEmpty() }

        val photo = view.findViewById<ImageView>(R.id.ivMasterPhoto)
        if (!master.profilePhoto.isNullOrBlank()) {
            photo.load(master.profilePhoto) {
                crossfade(true)
                placeholder(R.drawable.bg_profile_photo)
            }
        } else {
            photo.setImageResource(R.drawable.ic_nav_profile)
        }

        // Display rating as a percentage badge to mirror the screenshot mock-up:
        // a 5.0★ master → 100%, 4.0★ → 80%, etc. Hide for no-rating masters.
        val ratingPill = view.findViewById<TextView>(R.id.tvMasterRating)
        val pct = (master.rating.coerceIn(0.0, 5.0) / 5.0 * 100.0).roundToInt()
        if (master.rating > 0.0) {
            ratingPill.visibility = View.VISIBLE
            ratingPill.text = "\u2605 $pct%"
        } else {
            ratingPill.visibility = View.GONE
        }

        val heart = view.findViewById<ImageButton>(R.id.btnMasterFavorite)
        updateHeart(heart, favoriteMasters.isFavorite(master.id))
        heart.setOnClickListener {
            val now = favoriteMasters.toggle(
                FavoriteMastersStorage.Summary(
                    id = master.id,
                    name = master.name,
                    specialization = master.specialization,
                    profilePhoto = master.profilePhoto,
                    city = master.city,
                    rating = master.rating,
                ),
            )
            updateHeart(heart, now)
        }

        view.setOnClickListener {
            val intent = Intent(this, MasterDetailActivity::class.java)
            intent.putExtra(MasterDetailActivity.EXTRA_MASTER_ID, master.id)
            startActivity(intent)
        }
        return view
    }

    private fun updateHeart(button: ImageButton, isFavorite: Boolean) {
        button.setImageResource(
            if (isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline,
        )
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
        val manifestKey = OutfitIdeasHelper.manifestKeyForShape(apiBodyShape)
        val entries =
            if (manifestKey != null) OutfitIdeasHelper.photosForShape(this, apiBodyShape) else emptyList()

        if (manifestKey == null || entries.isEmpty()) {
            binding.cardOutfitIdeas.visibility = View.GONE
            binding.tvOutfitCloudinaryHint.visibility = View.GONE
            binding.btnExploreMoreLooks.visibility = View.GONE
            outfitIdeasFull = emptyList()
            return
        }

        binding.cardOutfitIdeas.visibility = View.VISIBLE
        val base = BuildConfig.CLOUDINARY_OUTFIT_BASE_URL.trim()
        if (base.isEmpty()) {
            binding.tvOutfitCloudinaryHint.visibility = View.VISIBLE
            binding.tvOutfitCloudinaryHint.text =
                "Add cloudinary.outfit.base.url to local.properties — delivery URL prefix for outfit images."
            binding.recyclerOutfitPhotos.visibility = View.GONE
            binding.btnExploreMoreLooks.visibility = View.GONE
            return
        }

        binding.tvOutfitCloudinaryHint.visibility = View.GONE
        binding.recyclerOutfitPhotos.visibility = View.VISIBLE

        outfitIdeasFull = entries
        outfitsExpanded = false
        renderOutfitsGrid()
    }

    private fun renderOutfitsGrid() {
        val entries = outfitIdeasFull
        if (entries.isEmpty()) return

        val visible =
            if (outfitsExpanded || entries.size <= OUTFIT_PREVIEW_COUNT) entries
            else entries.take(OUTFIT_PREVIEW_COUNT)

        val base = BuildConfig.CLOUDINARY_OUTFIT_BASE_URL.trim().trimEnd('/')
        binding.recyclerOutfitPhotos.layoutManager = GridLayoutManager(this, 3)
        binding.recyclerOutfitPhotos.adapter = OutfitGridAdapter(visible, base)

        val canExpand = entries.size > OUTFIT_PREVIEW_COUNT
        binding.btnExploreMoreLooks.visibility = if (canExpand) View.VISIBLE else View.GONE
        binding.btnExploreMoreLooks.text =
            if (outfitsExpanded) "Show Less" else "Explore More Looks"
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
