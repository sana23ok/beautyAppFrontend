package com.example.beautyappfrontend.ui.screens

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        setupBottomNav()
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_profile
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home    -> { navigateTo(HomeActivity::class.java); true }
                R.id.nav_search  -> { navigateTo(SearchPageActivity::class.java); true }
                R.id.nav_chat    -> { navigateTo(ChatActivity::class.java); true }
                R.id.nav_profile -> true // already here
                else -> false
            }
        }
    }

    private fun <T> navigateTo(cls: Class<T>) {
        val intent = Intent(this, cls)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        overridePendingTransition(0, 0)
    }
}
