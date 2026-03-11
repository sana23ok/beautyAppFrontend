package com.example.beautyappfrontend

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.beautyappfrontend.ui.screens.WelcomeScreen
import com.example.beautyappfrontend.ui.theme.BeautyAppFrontendTheme
import com.example.beautyappfrontend.ui.screens.SearchPageActivity
import com.example.beautyappfrontend.ui.screens.AnalysisResultActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BeautyAppFrontendTheme {
                WelcomeScreen(
                    onNavigateToSearch = {
                        val intent = Intent(this, SearchPageActivity::class.java)
                        startActivity(intent)
                    },

                    onNavigateToAnalysis = {
                        val intent = Intent(this, AnalysisResultActivity::class.java)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}