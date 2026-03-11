package com.example.beautyappfrontend.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.beautyappfrontend.data.repository.SpecialistRepository

class MainViewModelFactory(
    private val repository: SpecialistRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(repository) as T
    }
}
