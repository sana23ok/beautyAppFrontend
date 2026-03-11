package com.example.beautyappfrontend.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beautyappfrontend.data.repository.SpecialistRepository
import com.example.beautyappfrontend.domain.model.Specialist
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: SpecialistRepository
) : ViewModel() {

    private val _specialists = MutableLiveData<List<Specialist>>()
    val specialists: LiveData<List<Specialist>> = _specialists

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val data = repository.getSpecialists()
                _specialists.postValue(data)
            } catch (e: Exception) {
                e.printStackTrace()
                _specialists.postValue(emptyList())
            }
        }
    }
}
