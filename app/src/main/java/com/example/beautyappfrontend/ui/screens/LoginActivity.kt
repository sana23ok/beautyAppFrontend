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
import com.example.beautyappfrontend.utils.GoogleSignInHelper
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
        setupClickListeners()
    }

    private fun setupClickListeners() {
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
            if (getString(R.string.server_client_id).contains("YOUR_WEB_CLIENT_ID_HERE")) {
                Toast.makeText(
                    this,
                    "Add your Web client ID to local.properties → WEB_CLIENT_ID",
                    Toast.LENGTH_LONG,
                ).show()
                return@setOnClickListener
            }
            @Suppress("DEPRECATION")
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
                    binding.btnLogin.isEnabled = false
                    binding.btnGoogleSignin.isEnabled = false
                    binding.btnLogin.alpha = 0.6f
                }
                is AuthState.Success -> {
                    session.saveTokens(state.token, state.refreshToken)
                    session.saveUserInfo(state.user)
                    session.saveIsMaster(state.user?.isMaster == true)
                    navigateToHome()
                }
                is AuthState.VerificationCodeSent -> {
                    binding.btnLogin.isEnabled = true
                    binding.btnGoogleSignin.isEnabled = true
                    binding.btnLogin.alpha = 1f
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
