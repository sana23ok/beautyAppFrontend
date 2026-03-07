package com.example.beautyappfrontend.ui.screens

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        setupHeadlineClicks()
        setupBottomNav()
    }

    private fun setupHeadlineClicks() {
        binding.headerPalette.setOnClickListener {
            Toast.makeText(this, "Find your colour palette", Toast.LENGTH_SHORT).show()
        }
        binding.headerQ1.setOnClickListener {
            Toast.makeText(this, "Question 1", Toast.LENGTH_SHORT).show()
        }
        binding.headerQ2.setOnClickListener {
            Toast.makeText(this, "Question 2", Toast.LENGTH_SHORT).show()
        }
        binding.headerQ3.setOnClickListener {
            Toast.makeText(this, "Question 3", Toast.LENGTH_SHORT).show()
        }
        binding.headerResults.setOnClickListener {
            Toast.makeText(this, "Results", Toast.LENGTH_SHORT).show()
        }
        binding.headerPaletteResult.setOnClickListener {
            Toast.makeText(this, "Your palette", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home    -> true // already here
                R.id.nav_search  -> { navigateTo(SearchPageActivity::class.java); true }
                R.id.nav_chat    -> { navigateTo(ChatActivity::class.java); true }
                R.id.nav_profile -> { navigateTo(ProfileActivity::class.java); true }
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
}
