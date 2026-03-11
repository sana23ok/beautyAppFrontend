package com.example.beautyappfrontend.ui.screens

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.beautyappfrontend.databinding.ActivityAnalysisResultBinding
import com.example.beautyappfrontend.ui.AnalysisViewModel
import coil.load
import coil.transform.CircleCropTransformation

class AnalysisResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalysisResultBinding
    private val viewModel: AnalysisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisResultBinding.inflate(layoutInflater)
        setContentView(binding.root)


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
                binding.tvColorAdvice.text = "Найкращі кольори: $bestColors"


                renderPalette(colorType.palette)


                val bodyType = response.analysisResult.bodyType
                binding.tvBodyShape.text = "${bodyType.shape} (${bodyType.description})"

                val bestClothes = bodyType.advice.bestClothes?.joinToString("\n• ") ?: ""
                binding.tvBodyAdvice.text = "Що носити:\n• $bestClothes"
            }
        }

        viewModel.loadAnalysis(1)
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