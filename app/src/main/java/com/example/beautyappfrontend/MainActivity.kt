package com.example.beautyappfrontend

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.beautyappfrontend.ui.screens.WelcomeScreen
import com.example.beautyappfrontend.ui.theme.BeautyAppFrontendTheme
import com.example.beautyappfrontend.ui.screens.SearchActivity // Ми створимо цей файл нижче

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BeautyAppFrontendTheme {
                WelcomeScreen(
                    onNavigateToSearch = {
                        val intent = Intent(this, SearchActivity::class.java)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}