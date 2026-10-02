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

enum class ActivityTab { FOR_YOU, LIKED, PRIVATE }

class MyActivitiesViewModel(
    private val repository: ActivityRepository = ActivityRepository()
) : ViewModel() {

    data class UiState(
        val allActivities: List<Activity> = emptyList(),
        val selectedTab: ActivityTab = ActivityTab.FOR_YOU,
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
                            filteredActivities = filterFor(current.selectedTab, activities)
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    fun selectTab(tab: ActivityTab) {
        _uiState.update { current ->
            current.copy(
                selectedTab = tab,
                filteredActivities = filterFor(tab, current.allActivities)
            )
        }
    }

    private fun filterFor(tab: ActivityTab, activities: List<Activity>): List<Activity> {
        val uid = repository.currentUserId
        return activities.filter {
            when (tab) {
                ActivityTab.FOR_YOU -> it.visibility == ActivityVisibility.PUBLIC
                ActivityTab.LIKED -> uid in it.likedBy
                ActivityTab.PRIVATE -> it.ownerId == uid && it.visibility == ActivityVisibility.PRIVATE
            }
        }
    }
}
