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

val ActivityCategories = listOf("food", "outdoors", "culture", "shopping")

class CreateActivityViewModel(
    private val repository: ActivityRepository = ActivityRepository()
) : ViewModel() {

    data class UiState(
        val activityId: String? = null,
        val originalTags: List<String> = emptyList(),
        val placeName: String = "",
        val address: String = "",
        val expectedPrice: String = "",
        val tags: List<String> = emptyList(),
        val customTag: String = "",
        val notes: String = "",
        val visibility: ActivityVisibility = ActivityVisibility.PRIVATE,
        val isSaving: Boolean = false,
        val errorMessage: String? = null,
        val didSave: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun load(activityId: String) {
        if (_uiState.value.activityId == activityId) return
        viewModelScope.launch {
            repository.getActivity(activityId)
                .onSuccess { activity ->
                    _uiState.update {
                        it.copy(
                            activityId = activity.id,
                            originalTags = activity.tags,
                            placeName = activity.name,
                            address = activity.address,
                            expectedPrice = activity.price.toString(),
                            tags = activity.tags,
                            notes = activity.description,
                            visibility = activity.visibility
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }
        }
    }

    fun onPlaceNameChange(value: String) = _uiState.update { it.copy(placeName = value) }

    fun onAddressChange(value: String) = _uiState.update { it.copy(address = value) }

    fun onExpectedPriceChange(value: String) = _uiState.update { it.copy(expectedPrice = value) }

    fun onTagToggle(tag: String) = _uiState.update {
        it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag)
    }

    fun onCustomTagChange(value: String) = _uiState.update { it.copy(customTag = value) }

    fun addCustomTag() {
        val tag = _uiState.value.customTag.trim().lowercase()
        if (tag.isBlank()) return
        _uiState.update {
            it.copy(tags = if (tag in it.tags) it.tags else it.tags + tag, customTag = "")
        }
    }

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
            repository.saveActivity(
                activityId = current.activityId,
                name = current.placeName.trim(),
                address = current.address.trim(),
                price = current.expectedPrice.filter { it.isDigit() }.toIntOrNull() ?: 0,
                tags = current.tags,
                newCustomTags = current.tags.filter { it !in ActivityCategories && it !in current.originalTags },
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
