package com.example.beautyappfrontend.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beautyappfrontend.data.repository.AnalysisRepository
import com.example.beautyappfrontend.domain.model.AnalysisResponse
import kotlinx.coroutines.launch

class AnalysisViewModel : ViewModel() {
    private val repository = AnalysisRepository()

    private val _analysisResult = MutableLiveData<AnalysisResponse?>()
    val analysisResult: LiveData<AnalysisResponse?> = _analysisResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadAnalysis(clientId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.getAnalysis(clientId)
                _analysisResult.value = result
            } catch (e: Exception) {
                // Обробити помилку (наприклад, показати Toast)
            } finally {
                _isLoading.value = false
            }
        }
    }
}