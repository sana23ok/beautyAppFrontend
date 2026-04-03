package com.example.beautyappfrontend.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beautyappfrontend.data.repository.AuthRepository
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _authState = MutableLiveData<AuthState>(AuthState.Idle)
    val authState: LiveData<AuthState> = _authState

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val response = repository.login(email, password)
                _authState.value = AuthState.Success(
                    token = response.authToken,
                    refreshToken = response.tokens?.refresh,
                    user = response.user,
                )
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun register(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        phoneNumber: String = "",
        isMaster: Boolean = false,
    ) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val response = repository.register(
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    password = password,
                    phoneNumber = phoneNumber,
                    isMaster = isMaster,
                )
                _authState.value = AuthState.Success(
                    token = response.authToken,
                    refreshToken = response.tokens?.refresh,
                    user = response.user,
                )
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Registration failed")
            }
        }
    }

    fun googleSignIn(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val response = repository.googleSignIn(idToken)
                _authState.value = AuthState.Success(
                    token = response.authToken,
                    refreshToken = response.tokens?.refresh,
                    user = response.user,
                )
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Google Sign-In failed")
            }
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}
