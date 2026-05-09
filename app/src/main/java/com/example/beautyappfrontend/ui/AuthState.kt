package com.example.beautyappfrontend.ui

import com.example.beautyappfrontend.domain.model.AuthUserInfo

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class VerificationCodeSent(val message: String) : AuthState()
    data class Success(
        val token: String,
        val refreshToken: String? = null,
        val user: AuthUserInfo? = null,
    ) : AuthState()
    data class Error(val message: String) : AuthState()
}
