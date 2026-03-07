package com.example.beautyappfrontend.ui.screens

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.databinding.ActivityProfileBinding
import com.example.beautyappfrontend.utils.SessionManager

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var session: SessionManager

    companion object {
        private const val TAG = "ProfileActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        populateUserData()
        setupClickListeners()
        setupBottomNav()
    }

    private fun populateUserData() {
        val name     = session.getDisplayName()
        val username = session.getUsername()
        val email    = session.getEmail()
        val phone    = session.getPhoneNumber()
        val token    = session.getToken()

        Log.d(TAG, "── Profile data from SharedPreferences ──")
        Log.d(TAG, "  displayName : '$name'")
        Log.d(TAG, "  username    : '$username'")
        Log.d(TAG, "  email       : '$email'")
        Log.d(TAG, "  phone       : '$phone'")
        Log.d(TAG, "  token empty : ${token.isNullOrEmpty()}")

        binding.tvName.text  = name.ifEmpty { "—" }
        binding.tvEmail.text = email.ifEmpty { "—" }
        binding.tvPhone.text = phone.ifEmpty { "" }
        binding.tvPhone.visibility = if (phone.isNotEmpty())
            android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun setupClickListeners() {
        binding.btnEdit.setOnClickListener {
            Toast.makeText(this, "Edit profile coming soon", Toast.LENGTH_SHORT).show()
        }
        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Log out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log out") { _, _ ->
                    session.clearSession()
                    Log.d(TAG, "Session cleared — navigating to LoginActivity")
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        // <include> bindings expose the root view via .root
        binding.appointment1.root.setOnClickListener {
            Toast.makeText(this, "Appointment details coming soon", Toast.LENGTH_SHORT).show()
        }
        binding.appointment2.root.setOnClickListener {
            Toast.makeText(this, "Appointment details coming soon", Toast.LENGTH_SHORT).show()
        }
        binding.appointment3.root.setOnClickListener {
            Toast.makeText(this, "Appointment details coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_profile
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home    -> { navigateTo(HomeActivity::class.java); true }
                R.id.nav_search  -> { navigateTo(SearchPageActivity::class.java); true }
                R.id.nav_chat    -> { navigateTo(ChatActivity::class.java); true }
                R.id.nav_profile -> true
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
