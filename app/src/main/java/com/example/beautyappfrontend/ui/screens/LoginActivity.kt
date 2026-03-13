package com.example.beautyappfrontend.ui.screens

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.databinding.ActivityLoginBinding
import com.example.beautyappfrontend.ui.AuthState
import com.example.beautyappfrontend.ui.AuthViewModel
import com.example.beautyappfrontend.utils.DebugLogger
import com.example.beautyappfrontend.utils.SessionManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager
    private lateinit var googleSignInClient: GoogleSignInClient
    private val viewModel: AuthViewModel by viewModels()

    companion object {
        private const val RC_GOOGLE_SIGN_IN = 9001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        session = SessionManager(this)

        // Already logged in — skip straight to Home
        if (session.isLoggedIn()) {
            navigateToHome()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.server_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        observeAuthState()

        binding.btnLogin.setOnClickListener {
            val email    = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                Toast.makeText(this, "Please enter your password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.login(email, password)
        }

        binding.btnGoogleSignin.setOnClickListener {
            // #region agent log
            DebugLogger.log(
                runId = "pre-fix",
                hypothesisId = "H1",
                location = "LoginActivity.kt:69",
                message = "Launching Google sign-in",
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
                    location = "LoginActivity.kt:80",
                    message = "Blocked Google sign-in because client id is placeholder",
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

        binding.tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.tvForgotPassword.setOnClickListener {
            // TODO: forgot password flow
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
                location = "LoginActivity.kt:95",
                message = "Received Google sign-in activity result",
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
                    location = "LoginActivity.kt:108",
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
                    location = "LoginActivity.kt:120",
                    message = "Google sign-in threw ApiException",
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
                    binding.btnLogin.isEnabled = false
                    binding.btnGoogleSignin.isEnabled = false
                    binding.btnLogin.alpha = 0.6f
                }
                is AuthState.Success -> {
                    session.saveToken(state.token)
                    session.saveUserInfo(state.user)
                    session.saveIsMaster(state.user?.isMaster == true)
                    navigateToHome()
                }
                is AuthState.Error -> {
                    binding.btnLogin.isEnabled = true
                    binding.btnGoogleSignin.isEnabled = true
                    binding.btnLogin.alpha = 1f
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetState()
                }
                is AuthState.Idle -> {
                    binding.btnLogin.isEnabled = true
                    binding.btnGoogleSignin.isEnabled = true
                    binding.btnLogin.alpha = 1f
                }
            }
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
