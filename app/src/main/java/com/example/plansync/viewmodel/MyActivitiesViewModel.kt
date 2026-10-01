package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.ActivityRepository
import com.example.plansync.model.Activity
import com.example.plansync.model.ActivityVisibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MyActivitiesViewModel(
    private val repository: ActivityRepository = ActivityRepository()
) : ViewModel() {

    data class UiState(
        val allActivities: List<Activity> = emptyList(),
        val selectedTab: ActivityVisibility = ActivityVisibility.PUBLIC,
        val filteredActivities: List<Activity> = emptyList(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun loadActivities() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            repository.getActivities()
                .onSuccess { activities ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            allActivities = activities,
                            filteredActivities = activities.filter { it.visibility == current.selectedTab }
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    fun selectTab(tab: ActivityVisibility) {
        _uiState.update { current ->
            current.copy(
                selectedTab = tab,
                filteredActivities = current.allActivities.filter { it.visibility == tab }
            )
        }
    }
}
