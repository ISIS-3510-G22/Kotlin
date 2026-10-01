package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.ActivityRepository
import com.example.plansync.model.ActivityVisibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val ActivityCategories = listOf("Food", "Outdoors", "Culture", "Shopping")

class CreateActivityViewModel(
    private val repository: ActivityRepository = ActivityRepository()
) : ViewModel() {

    data class UiState(
        val placeName: String = "",
        val address: String = "",
        val expectedPrice: String = "",
        val category: String = ActivityCategories.first(),
        val notes: String = "",
        val visibility: ActivityVisibility = ActivityVisibility.PRIVATE,
        val isSaving: Boolean = false,
        val errorMessage: String? = null,
        val didSave: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onPlaceNameChange(value: String) = _uiState.update { it.copy(placeName = value) }

    fun onAddressChange(value: String) = _uiState.update { it.copy(address = value) }

    fun onExpectedPriceChange(value: String) = _uiState.update { it.copy(expectedPrice = value) }

    fun onCategorySelect(value: String) = _uiState.update { it.copy(category = value) }

    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value) }

    fun onVisibilitySelect(value: ActivityVisibility) = _uiState.update { it.copy(visibility = value) }

    fun saveActivity() {
        val current = _uiState.value
        if (current.placeName.isBlank() || current.address.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please fill in the place name and address.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            repository.addActivity(
                name = current.placeName.trim(),
                address = current.address.trim(),
                price = current.expectedPrice.filter { it.isDigit() }.toIntOrNull() ?: 0,
                category = current.category,
                notes = current.notes.trim(),
                visibility = current.visibility
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
