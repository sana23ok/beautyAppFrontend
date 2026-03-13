package com.example.beautyappfrontend.ui.screens

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.databinding.ActivityRegisterBinding
import com.example.beautyappfrontend.ui.AuthState
import com.example.beautyappfrontend.ui.AuthViewModel
import com.example.beautyappfrontend.utils.DebugLogger
import com.example.beautyappfrontend.utils.SessionManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private val viewModel: AuthViewModel by viewModels()
    private var pendingIsMaster: Boolean = false

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

        binding.btnRegister.setOnClickListener {
            val fullName = binding.etFullName.text.toString().trim()
            val email    = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirm  = binding.etConfirmPassword.text.toString().trim()
            val (firstName, lastName) = splitName(fullName)

            if (fullName.isEmpty()) {
                Toast.makeText(this, "Please enter your full name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 8) {
                Toast.makeText(this, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password != confirm) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            pendingIsMaster = binding.cbRegisterAsMaster.isChecked
            viewModel.register(
                firstName = firstName,
                lastName = lastName,
                email = email,
                password = password,
                isMaster = pendingIsMaster,
            )
        }

        binding.btnGoogleSignup.setOnClickListener {
            pendingIsMaster = binding.cbRegisterAsMaster.isChecked
            // #region agent log
            DebugLogger.log(
                runId = "pre-fix",
                hypothesisId = "H1",
                location = "RegisterActivity.kt:69",
                message = "Launching Google sign-up",
                data = mapOf(
                    "clientIdIsPlaceholder" to getString(R.string.server_client_id).contains("YOUR_WEB_CLIENT_ID_HERE"),
                ),
            )
            // #endregion
            if (getString(R.string.server_client_id).contains("YOUR_WEB_CLIENT_ID_HERE")) {
                // #region agent log
                DebugLogger.log(
                    runId = "post-fix",
                    hypothesisId = "H1",
                    location = "RegisterActivity.kt:80",
                    message = "Blocked Google sign-up because client id is placeholder",
                    data = emptyMap(),
                )
                // #endregion
                Toast.makeText(
                    this,
                    "Google Sign-In is not configured yet. Add your real Web client ID in strings.xml.",
                    Toast.LENGTH_LONG,
                ).show()
                return@setOnClickListener
            }
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
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            // #region agent log
            DebugLogger.log(
                runId = "pre-fix",
                hypothesisId = "H2",
                location = "RegisterActivity.kt:95",
                message = "Received Google sign-up activity result",
                data = mapOf(
                    "resultCode" to resultCode,
                    "hasIntentData" to (data != null),
                ),
            )
            // #endregion
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                // #region agent log
                DebugLogger.log(
                    runId = "pre-fix",
                    hypothesisId = "H3",
                    location = "RegisterActivity.kt:108",
                    message = "Processed Google account result",
                    data = mapOf(
                        "hasIdToken" to (idToken != null),
                        "hasServerAuthCode" to (account.serverAuthCode != null),
                    ),
                )
                // #endregion
                if (idToken != null) {
                    viewModel.googleSignIn(idToken)
                } else {
                    Toast.makeText(this, "Google Sign-In failed: missing token", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                // #region agent log
                DebugLogger.log(
                    runId = "pre-fix",
                    hypothesisId = "H2",
                    location = "RegisterActivity.kt:120",
                    message = "Google sign-up threw ApiException",
                    data = mapOf("statusCode" to e.statusCode),
                )
                // #endregion
                Toast.makeText(this, "Google Sign-In failed (${e.statusCode})", Toast.LENGTH_SHORT).show()
            }
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
                is AuthState.Success -> {
                    val session = SessionManager(this)
                    session.saveToken(state.token)
                    session.saveUserInfo(state.user)
                    session.saveIsMaster(state.user?.isMaster == true || pendingIsMaster)
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
        val destination = if (pendingIsMaster) {
            ProfileActivity::class.java
        } else {
            HomeActivity::class.java
        }
        val intent = Intent(this, destination)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun splitName(fullName: String): Pair<String, String> {
        val parts = fullName.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val firstName = parts.firstOrNull().orEmpty()
        val lastName = parts.drop(1).joinToString(" ")
        return firstName to lastName
    }
}
