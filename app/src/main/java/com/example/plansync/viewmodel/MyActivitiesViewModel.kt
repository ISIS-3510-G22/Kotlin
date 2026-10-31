package com.example.plansync.viewmodel

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.ActivityPreferences
import com.example.plansync.data.ActivityRepository
import com.example.plansync.model.Activity
import com.example.plansync.service.LocationService
import com.example.plansync.service.NetworkMonitor
import com.example.plansync.service.OfflineSyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ActivityTab { ALL, FOR_YOU, LIKED, CREATED }

class MyActivitiesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ActivityRepository()
    private val locationService = LocationService(application)
    private val networkMonitor = NetworkMonitor.get(application)
    private val preferences = ActivityPreferences(application)
    private var userLocation: Location? = null

    data class UiState(
        val allActivities: List<Activity> = emptyList(),
        val selectedTab: ActivityTab = ActivityTab.ALL,
        val filteredActivities: List<Activity> = emptyList(),
        val recommendedIds: List<String> = emptyList(),
        val availableTags: List<String> = emptyList(),
        val selectedTags: Set<String> = emptySet(),
        val distancesKm: Map<String, Float> = emptyMap(),
        val pendingIds: Set<String> = emptySet(),
        val lastUpdated: Long? = null,
        val isOnline: Boolean = true,
        val isLoading: Boolean = false,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState(isOnline = networkMonitor.isCurrentlyOnline()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                val reconnected = online && !_uiState.value.isOnline
                _uiState.update { it.copy(isOnline = online) }
                if (reconnected) loadActivities()
            }
        }
    }

    fun loadActivities() {
        viewModelScope.launch {
            val uid = repository.currentUserId
            val cached = repository.getCachedActivities()
            val lastUpdated = uid?.let { preferences.getLastUpdated(it) }
            val cachedRecommended = uid?.let { preferences.getRecommendedIds(it) }.orEmpty()
            _uiState.update {
                it.copy(
                    isLoading = cached.isEmpty(),
                    lastUpdated = lastUpdated,
                    recommendedIds = cachedRecommended,
                    errorMessage = null
                )
            }
            if (cached.isNotEmpty()) show(cached)

            val isOnline = _uiState.value.isOnline
            val (result, recommended, location) = withContext(Dispatchers.IO) {
                val activities = async { if (isOnline) fetchAndCache() else Result.success(cached) }
                val recommended = async { if (isOnline) fetchRecommended() else cachedRecommended }
                val location = async { locationService.currentLocation() }
                Triple(activities.await(), recommended.await(), location.await())
            }
            location?.let { userLocation = it }
            _uiState.update { it.copy(recommendedIds = recommended) }

            result
                .onSuccess { activities -> show(activities) }
                .onFailure { error ->
                    if (cached.isEmpty()) _uiState.update { it.copy(errorMessage = error.message) } else refresh()
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun fetchAndCache(): Result<List<Activity>> {
        OfflineSyncService.syncNow()
        return repository.getActivities()
            .map { OfflineSyncService.applyPending(it) }
            .onSuccess {
                repository.cacheActivities(it)
                saveLastUpdated()
            }
    }

    private suspend fun fetchRecommended(): List<String> {
        val uid = repository.currentUserId ?: return emptyList()
        return repository.getRecommendedIds()
            .onSuccess { preferences.setRecommendedIds(uid, it) }
            .getOrElse { preferences.getRecommendedIds(uid) }
    }

    private suspend fun saveLastUpdated() {
        val uid = repository.currentUserId ?: return
        val now = System.currentTimeMillis()
        preferences.setLastUpdated(uid, now)
        _uiState.update { it.copy(lastUpdated = now) }
    }

    private suspend fun show(activities: List<Activity>) {
        _uiState.update {
            it.copy(
                allActivities = activities,
                availableTags = tagsByPopularity(activities),
                pendingIds = OfflineSyncService.pendingActivityIds()
            )
        }
        refresh()
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

    fun toggleTag(tag: String) {
        _uiState.update {
            it.copy(selectedTags = if (tag in it.selectedTags) it.selectedTags - tag else it.selectedTags + tag)
        }
        viewModelScope.launch { refresh() }
    }

    fun clearTags() {
        _uiState.update { it.copy(selectedTags = emptySet()) }
        viewModelScope.launch { refresh() }
    }

    private fun tagsByPopularity(activities: List<Activity>): List<String> =
        activities.flatMap { it.tags }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }

    private suspend fun refresh() {
        val current = _uiState.value
        val location = userLocation
        val (distances, filtered) = withContext(Dispatchers.Default) {
            val distances = location?.let {
                current.allActivities
                    .filter { it.lat != null && it.lng != null }
                    .associate { activity -> activity.id to locationService.distanceKm(location, activity.lat!!, activity.lng!!) }
            }.orEmpty()
            distances to filterFor(current, distances)
        }
        _uiState.update {
            if (it.selectedTab != current.selectedTab || it.selectedTags != current.selectedTags) it
            else it.copy(distancesKm = distances, filteredActivities = filtered)
        }
    }

    private fun filterFor(state: UiState, distances: Map<String, Float>): List<Activity> {
        val uid = repository.currentUserId
        val activities = state.allActivities
        return when (state.selectedTab) {
            ActivityTab.ALL -> activities
                .filter { state.selectedTags.isEmpty() || it.tags.any { tag -> tag in state.selectedTags } }
                .sortedBy { distances[it.id] ?: Float.MAX_VALUE }
            ActivityTab.FOR_YOU -> {
                val rank = state.recommendedIds.withIndex().associate { (i, id) -> id to i }
                activities.filter { it.id in rank }.sortedBy { rank[it.id] }
            }
            ActivityTab.LIKED -> activities.filter { uid in it.likedBy }
            ActivityTab.CREATED -> activities.filter { it.ownerId == uid }
        }
    }
}
