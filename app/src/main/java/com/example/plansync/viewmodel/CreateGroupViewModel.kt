package com.example.plansync.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.CrewRepository
import com.example.plansync.service.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreateGroupViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CrewRepository()
    private val networkMonitor = NetworkMonitor.get(application)

    data class UiState(
        val name: String = "",
        val description: String = "",
        val isSaving: Boolean = false,
        val errorMessage: String? = null,
        val didSave: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }

    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value) }

    fun createGroup() {
        val current = _uiState.value
        if (current.name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a group name.") }
            return
        }

        if (!networkMonitor.isCurrentlyOnline()) {
            _uiState.update { it.copy(errorMessage = "Group functionalities are not available while offline.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            repository.createGroup(
                name = current.name.trim(),
                description = current.description.trim()
            )
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, didSave = true) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = error.message) }
                }
        }
    }
}
