package com.example.plansync.viewmodel

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.ActivityRepository
import com.example.plansync.model.Activity
import com.example.plansync.model.ActivityVisibility
import com.example.plansync.service.LocationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ActivityTab { FOR_YOU, LIKED, CREATED, PRIVATE }

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

            val (result, location) = withContext(Dispatchers.IO) {
                val activities = async { repository.getActivities() }
                val location = async { locationService.currentLocation() }
                activities.await() to location.await()
            }
            location?.let { userLocation = it }

            result
                .onSuccess { activities ->
                    _uiState.update { it.copy(allActivities = activities) }
                    refresh()
                    _uiState.update { it.copy(isLoading = false) }
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
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        val current = _uiState.value
        val location = userLocation
        val (distances, filtered) = withContext(Dispatchers.Default) {
            val distances = location?.let {
                current.allActivities
                    .filter { it.lat != null && it.lng != null }
                    .associate { activity -> activity.id to locationService.distanceKm(location, activity.lat!!, activity.lng!!) }
            }.orEmpty()
            distances to filterFor(current.selectedTab, current.allActivities, distances)
        }
        _uiState.update {
            if (it.selectedTab != current.selectedTab) it
            else it.copy(distancesKm = distances, filteredActivities = filtered)
        }
    }

    private fun filterFor(tab: ActivityTab, activities: List<Activity>, distances: Map<String, Float>): List<Activity> {
        val uid = repository.currentUserId
        return when (tab) {
            ActivityTab.FOR_YOU -> activities
                .filter { it.visibility == ActivityVisibility.PUBLIC }
                .sortedBy { distances[it.id] ?: Float.MAX_VALUE }
            ActivityTab.LIKED -> activities.filter { uid in it.likedBy }
            ActivityTab.CREATED -> activities.filter { it.ownerId == uid }
            ActivityTab.PRIVATE -> activities.filter { it.ownerId == uid && it.visibility == ActivityVisibility.PRIVATE }
        }
    }
}
