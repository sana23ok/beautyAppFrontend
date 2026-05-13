package com.example.beautyappfrontend.ui.screens

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.data.repository.FavoriteMastersRepository
import com.example.beautyappfrontend.databinding.ActivityRegisterBinding
import com.example.beautyappfrontend.ui.AuthState
import com.example.beautyappfrontend.ui.AuthViewModel
import com.example.beautyappfrontend.utils.GoogleSignInHelper
import com.example.beautyappfrontend.utils.SessionManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private val viewModel: AuthViewModel by viewModels()
    private var pendingIsMaster: Boolean = false
    private var isWaitingForVerificationCode: Boolean = false

    companion object {
        private const val RC_GOOGLE_SIGN_IN = 9001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.server_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        observeAuthState()
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnRegister.setOnClickListener {
            val fullName = binding.etFullName.text.toString().trim()
            val email    = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirm  = binding.etConfirmPassword.text.toString().trim()
            val verificationCode = binding.etVerificationCode.text.toString().trim()
            val (firstName, lastName) = splitName(fullName)

            if (!validateRegistrationFields(fullName, email, password, confirm)) return@setOnClickListener

            pendingIsMaster = binding.cbRegisterAsMaster.isChecked
            if (!isWaitingForVerificationCode) {
                viewModel.sendRegistrationCode(
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    password = password,
                    isMaster = pendingIsMaster,
                )
            } else {
                if (!verificationCode.matches(Regex("\\d{6}"))) {
                    Toast.makeText(this, "Enter the 6-digit code from your email", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                viewModel.register(
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    password = password,
                    verificationCode = verificationCode,
                    isMaster = pendingIsMaster,
                )
            }
        }

        binding.btnGoogleSignup.setOnClickListener {
            if (getString(R.string.server_client_id).contains("YOUR_WEB_CLIENT_ID_HERE")) {
                Toast.makeText(
                    this,
                    "Add your Web client ID to local.properties → WEB_CLIENT_ID",
                    Toast.LENGTH_LONG,
                ).show()
                return@setOnClickListener
            }
            pendingIsMaster = binding.cbRegisterAsMaster.isChecked
            @Suppress("DEPRECATION")
            startActivityForResult(googleSignInClient.signInIntent, RC_GOOGLE_SIGN_IN)
        }

        binding.tvSignInLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != RC_GOOGLE_SIGN_IN) return

        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                viewModel.googleSignIn(idToken)
            } else {
                Toast.makeText(this, "Google Sign-In failed: missing token", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            GoogleSignInHelper.handleApiException(this, e.statusCode)
        }
    }

    private fun observeAuthState() {
        viewModel.authState.observe(this) { state ->
            when (state) {
                is AuthState.Loading -> {
                    binding.btnRegister.isEnabled = false
                    binding.btnGoogleSignup.isEnabled = false
                    binding.btnRegister.alpha = 0.6f
                }
                is AuthState.VerificationCodeSent -> {
                    isWaitingForVerificationCode = true
                    binding.etVerificationCode.visibility = View.VISIBLE
                    binding.tvVerificationHint.visibility = View.VISIBLE
                    binding.btnRegister.text = getString(R.string.verify_and_register)
                    binding.btnRegister.isEnabled = true
                    binding.btnGoogleSignup.isEnabled = true
                    binding.btnRegister.alpha = 1f
                    binding.etVerificationCode.requestFocus()
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                is AuthState.Success -> {
                    val session = SessionManager(this)
                    session.saveTokens(state.token, state.refreshToken)
                    session.saveUserInfo(state.user)
                    session.saveIsMaster(state.user?.isMaster == true || pendingIsMaster)
                    lifecycleScope.launch {
                        FavoriteMastersRepository.sync()
                    }
                    navigateAfterRegistration()
                }
                is AuthState.Error -> {
                    binding.btnRegister.isEnabled = true
                    binding.btnGoogleSignup.isEnabled = true
                    binding.btnRegister.alpha = 1f
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetState()
                }
                is AuthState.Idle -> {
                    binding.btnRegister.isEnabled = true
                    binding.btnGoogleSignup.isEnabled = true
                    binding.btnRegister.alpha = 1f
                }
            }
        }
    }

    private fun navigateAfterRegistration() {
        val destination = if (pendingIsMaster) ProfileActivity::class.java else HomeActivity::class.java
        val intent = Intent(this, destination)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun validateRegistrationFields(
        fullName: String,
        email: String,
        password: String,
        confirm: String,
    ): Boolean {
        if (fullName.isEmpty()) {
            Toast.makeText(this, "Please enter your full name", Toast.LENGTH_SHORT).show()
            return false
        }
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            return false
        }
        if (password.length < 8) {
            Toast.makeText(this, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show()
            return false
        }
        if (password != confirm) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun splitName(fullName: String): Pair<String, String> {
        val parts = fullName.split("\\s+".toRegex()).filter { it.isNotBlank() }
        return parts.firstOrNull().orEmpty() to parts.drop(1).joinToString(" ")
    }
}
