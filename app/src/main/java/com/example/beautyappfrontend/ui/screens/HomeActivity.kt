package com.example.beautyappfrontend.ui.screens

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.data.remote.RetrofitInstance
import com.example.beautyappfrontend.databinding.ActivityHomeBinding
import com.example.beautyappfrontend.domain.model.AppearanceTestRequest
import com.example.beautyappfrontend.domain.model.AppearanceTestResponse
import com.google.gson.Gson
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    private data class QuizOption(val label: String, val value: String)
    private data class Question(val key: String, val options: List<QuizOption>)

    private val gson = Gson()
    private val selectedAnswers = mutableMapOf<String, String>()
    private val chipsByQuestion = mutableMapOf<String, MutableList<TextView>>()

    private var isTestExpanded = true
    private var isResultsExpanded = false

    private val questions = listOf(
        Question(
            key = "undertone",
            options = listOf(
                QuizOption("Warm", "warm"),
                QuizOption("Cool", "cool"),
                QuizOption("Neutral", "neutral"),
                QuizOption("Olive", "olive")
            )
        ),
        Question(
            key = "hair_color",
            options = listOf(
                QuizOption("Blonde", "blonde"),
                QuizOption("Red / Ginger", "red"),
                QuizOption("Light Brown", "light brown"),
                QuizOption("Dark Brown", "dark brown"),
                QuizOption("Black", "black")
            )
        ),
        Question(
            key = "tanning_reaction",
            options = listOf(
                QuizOption("Tans Easily", "tans easily"),
                QuizOption("Burns, then Tans", "burns then tans"),
                QuizOption("Burns Easily", "burns easily")
            )
        ),
        Question(
            key = "eyes_color",
            options = listOf(
                QuizOption("Blue", "light blue"),
                QuizOption("Green", "green"),
                QuizOption("Brown", "brown"),
                QuizOption("Hazel", "hazel"),
                QuizOption("Grey", "light grey")
            )
        )
    )

    private val containers: List<LinearLayout> by lazy {
        listOf(
            binding.containerQ1,
            binding.containerQ2,
            binding.containerQ3,
            binding.containerQ4
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        buildQuestions()
        setupSectionToggles()
        restoreSavedState()

        binding.btnAnalyse.setOnClickListener { onAnalyseClicked() }
        binding.btnRetakeTest.setOnClickListener { expandTestForRetake() }

        setupBottomNav()
    }

    private fun buildQuestions() {
        questions.forEachIndexed { index, question ->
            val container = containers[index]
            val questionChips = mutableListOf<TextView>()

            question.options.forEach { option ->
                val chip = layoutInflater.inflate(
                    R.layout.item_quiz_chip,
                    container,
                    false
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
        binding.testContent.visibility = if (isTestExpanded) View.VISIBLE else View.GONE
        binding.resultsContent.visibility = if (isResultsExpanded) View.VISIBLE else View.GONE
        binding.ivTestChevron.rotation = if (isTestExpanded) 90f else 0f
        binding.ivResultsChevron.rotation = if (isResultsExpanded) 90f else 0f
    }

    private fun selectChip(
        container: LinearLayout,
        selected: TextView,
        questionKey: String,
        value: String
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

        val bustText = binding.etBust.text.toString().trim()
        val waistText = binding.etWaist.text.toString().trim()
        val hipsText = binding.etHips.text.toString().trim()

        if (bustText.isEmpty() || waistText.isEmpty() || hipsText.isEmpty()) {
            Toast.makeText(
                this,
                "Please enter bust, waist and hips measurements",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val bust = bustText.toIntOrNull() ?: 0
        val waist = waistText.toIntOrNull() ?: 0
        val hips = hipsText.toIntOrNull() ?: 0

        if (bust <= 0 || waist <= 0 || hips <= 0) {
            Toast.makeText(this, "Measurements must be positive numbers", Toast.LENGTH_SHORT).show()
            return
        }

        val request = AppearanceTestRequest(
            undertone = selectedAnswers["undertone"]!!,
            hairColor = selectedAnswers["hair_color"]!!,
            eyesColor = selectedAnswers["eyes_color"]!!,
            tanningReaction = selectedAnswers["tanning_reaction"]!!,
            bust = bust,
            waist = waist,
            hips = hips
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
                        renderResult(result)
                        isTestExpanded = false
                        isResultsExpanded = true
                        updateSectionState()
                        Toast.makeText(
                            this@HomeActivity,
                            "Result saved on Home",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(this@HomeActivity, "Empty response from server", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@HomeActivity, "Server error: ${response.code()}", Toast.LENGTH_SHORT).show()
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
        result: AppearanceTestResponse
    ) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_REQUEST, gson.toJson(request))
            .putString(KEY_LAST_RESULT, gson.toJson(result))
            .apply()
    }

    private fun restoreSavedState() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        prefs.getString(KEY_LAST_REQUEST, null)?.let { json ->
            runCatching {
                gson.fromJson(json, AppearanceTestRequest::class.java)
            }.getOrNull()?.let { request ->
                restoreAnswers(request)
            }
        }

        prefs.getString(KEY_LAST_RESULT, null)?.let { json ->
            runCatching {
                gson.fromJson(json, AppearanceTestResponse::class.java)
            }.getOrNull()?.let { result ->
                renderResult(result)
                isTestExpanded = false
                isResultsExpanded = true
                updateSectionState()
            }
        }
    }

    private fun restoreAnswers(request: AppearanceTestRequest) {
        selectedAnswers["undertone"] = request.undertone
        selectedAnswers["hair_color"] = request.hairColor
        selectedAnswers["eyes_color"] = request.eyesColor
        selectedAnswers["tanning_reaction"] = request.tanningReaction

        binding.etBust.setText(request.bust.toString())
        binding.etWaist.setText(request.waist.toString())
        binding.etHips.setText(request.hips.toString())

        updateSelectedChip("undertone", request.undertone)
        updateSelectedChip("hair_color", request.hairColor)
        updateSelectedChip("eyes_color", request.eyesColor)
        updateSelectedChip("tanning_reaction", request.tanningReaction)
    }

    private fun updateSelectedChip(questionKey: String, selectedValue: String) {
        chipsByQuestion[questionKey]?.forEach { chip ->
            val isSelected = chip.tag == selectedValue
            chip.setBackgroundResource(
                if (isSelected) R.drawable.bg_option_selected else R.drawable.bg_option_box
            )
        }
    }

    private fun renderResult(result: AppearanceTestResponse) {
        val colorType = result.analysisResult.colorType
        val bodyType = result.analysisResult.bodyType

        binding.tvResultsSubtitle.text = "Latest result saved"
        binding.tvTestSubtitle.text = "Your latest result is saved below"
        binding.tvNoResults.visibility = View.GONE
        binding.resultsDetails.visibility = View.VISIBLE

        binding.tvStyleSummary.text = result.styleDescription
        binding.tvSeasonName.text = "${colorType.season} (${colorType.description})"
        binding.tvBodyShape.text = "${bodyType.shape} (${bodyType.description})"

        val bestColors = colorType.advice.best?.joinToString(", ") ?: "No data"
        binding.tvColorAdvice.text = "Best colours: $bestColors"

        val bestClothes = bodyType.advice.bestClothes?.joinToString("\n• ") ?: "No data"
        binding.tvBodyAdvice.text = "What to wear:\n• $bestClothes"

        renderPalette(colorType.palette)
    }

    private fun renderPalette(colors: List<String>) {
        binding.paletteContainer.removeAllViews()

        for (hexColor in colors) {
            val colorView = View(this)
            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
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

    private fun expandTestForRetake() {
        isTestExpanded = true
        isResultsExpanded = false
        updateSectionState()
        binding.testContent.post {
            binding.testContent.requestFocus()
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
}
