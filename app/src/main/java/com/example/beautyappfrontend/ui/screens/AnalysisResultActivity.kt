package com.example.beautyappfrontend.ui.screens

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.beautyappfrontend.databinding.ActivityAnalysisResultBinding
import com.example.beautyappfrontend.domain.model.AppearanceTestResponse
import com.example.beautyappfrontend.domain.model.AnalysisResult
import com.example.beautyappfrontend.ui.AnalysisViewModel
import com.google.gson.Gson
import coil.load
import coil.transform.CircleCropTransformation

class AnalysisResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEST_RESULT = "extra_test_result"
    }

    private lateinit var binding: ActivityAnalysisResultBinding
    private val viewModel: AnalysisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val testResultJson = intent.getStringExtra(EXTRA_TEST_RESULT)
        if (testResultJson != null) {
            val result = Gson().fromJson(testResultJson, AppearanceTestResponse::class.java)
            displayTestResult(result.analysisResult, result.styleDescription)
        } else {
            observeViewModel()
            viewModel.loadAnalysis(1)
        }
    }

    private fun displayTestResult(analysisResult: AnalysisResult, styleDescription: String) {
        binding.tvClientName.text = "My Analysis"
        binding.tvStyleSummary.text = styleDescription

        val colorType = analysisResult.colorType
        binding.tvSeasonName.text = "${colorType.season} (${colorType.description})"

        val bestColors = colorType.advice.best?.joinToString(", ") ?: ""
        binding.tvColorAdvice.text = "Best colours: $bestColors"

        renderPalette(colorType.palette)

        val bodyType = analysisResult.bodyType
        binding.tvBodyShape.text = "${bodyType.shape} (${bodyType.description})"

        val bestClothes = bodyType.advice.bestClothes?.joinToString("\n• ") ?: ""
        binding.tvBodyAdvice.text = "What to wear:\n• $bestClothes"
    }

    private fun observeViewModel() {
        viewModel.analysisResult.observe(this) { response ->
            if (response != null) {
                binding.tvClientName.text = response.client.name
                binding.tvStyleSummary.text = response.styleDescription

                response.client.imageUrl?.let { url ->
                    binding.ivClientPhoto.load(url) {
                        crossfade(true)
                        transformations(CircleCropTransformation())
                    }
                }

                val colorType = response.analysisResult.colorType
                binding.tvSeasonName.text = "${colorType.season} (${colorType.description})"

                val bestColors = colorType.advice.best?.joinToString(", ") ?: ""
                binding.tvColorAdvice.text = "Best colours: $bestColors"

                renderPalette(colorType.palette)

                val bodyType = response.analysisResult.bodyType
                binding.tvBodyShape.text = "${bodyType.shape} (${bodyType.description})"

                val bestClothes = bodyType.advice.bestClothes?.joinToString("\n• ") ?: ""
                binding.tvBodyAdvice.text = "What to wear:\n• $bestClothes"
            }
        }
    }

    private fun renderPalette(colors: List<String>) {
        binding.paletteContainer.removeAllViews()
        for (hexColor in colors) {
            val colorView = View(this)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            params.setMargins(4, 0, 4, 0)
            colorView.layoutParams = params
            try {
                colorView.setBackgroundColor(Color.parseColor(hexColor))
            } catch (e: Exception) {
                colorView.setBackgroundColor(Color.GRAY)
            }
            binding.paletteContainer.addView(colorView)
        }
    }
}
