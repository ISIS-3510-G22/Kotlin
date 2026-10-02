package com.example.plansync.viewmodel

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.ActivityRepository
import com.example.plansync.model.Activity
import com.example.plansync.model.ActivityVisibility
import com.example.plansync.service.LocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActivityTab { FOR_YOU, LIKED, PRIVATE }

class MyActivitiesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ActivityRepository()
    private val locationService = LocationService(application)
    private var userLocation: Location? = null

    data class UiState(
        val allActivities: List<Activity> = emptyList(),
        val selectedTab: ActivityTab = ActivityTab.FOR_YOU,
        val filteredActivities: List<Activity> = emptyList(),
        val distancesKm: Map<String, Float> = emptyMap(),
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
                    _uiState.update { it.copy(isLoading = false, allActivities = activities) }
                    refresh()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    fun onLocationPermissionResult() {
        viewModelScope.launch {
            userLocation = locationService.currentLocation() ?: return@launch
            refresh()
        }
    }

    fun selectTab(tab: ActivityTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        refresh()
    }

    private fun refresh() {
        _uiState.update { current ->
            val distances = userLocation?.let { location ->
                current.allActivities
                    .filter { it.lat != null && it.lng != null }
                    .associate { it.id to locationService.distanceKm(location, it.lat!!, it.lng!!) }
            }.orEmpty()
            current.copy(
                distancesKm = distances,
                filteredActivities = filterFor(current.selectedTab, current.allActivities, distances)
            )
        }
    }

    private fun filterFor(tab: ActivityTab, activities: List<Activity>, distances: Map<String, Float>): List<Activity> {
        val uid = repository.currentUserId
        return when (tab) {
            ActivityTab.FOR_YOU -> activities
                .filter { it.visibility == ActivityVisibility.PUBLIC }
                .sortedBy { distances[it.id] ?: Float.MAX_VALUE }
            ActivityTab.LIKED -> activities.filter { uid in it.likedBy }
            ActivityTab.PRIVATE -> activities.filter { it.ownerId == uid && it.visibility == ActivityVisibility.PRIVATE }
        }
    }
}
