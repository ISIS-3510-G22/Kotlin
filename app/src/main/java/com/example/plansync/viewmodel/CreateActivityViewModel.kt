package com.example.plansync.viewmodel

import android.app.Application
import android.util.ArrayMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.ActivityRepository
import com.example.plansync.data.PlacesRepository
import com.example.plansync.model.Activity
import com.example.plansync.model.ActivityVisibility
import com.example.plansync.model.PendingAction
import com.example.plansync.model.PlaceResult
import com.example.plansync.service.LocationService
import com.example.plansync.service.NetworkMonitor
import com.example.plansync.service.OfflineSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val ActivityCategories = listOf("food", "outdoors", "culture", "shopping")

private const val MAX_CACHED_SEARCHES = 30
private const val MAX_TAG_LENGTH = 20
private val validTag = Regex("^[\\p{L}\\p{N}]+([ -][\\p{L}\\p{N}]+)*$")

class CreateActivityViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ActivityRepository()
    private val locationService = LocationService(application)
    private val placesRepository = PlacesRepository(application)
    private val networkMonitor = NetworkMonitor.get(application)
    private var searchJob: Job? = null
    private val searchCache = ArrayMap<String, List<PlaceResult>>()

    data class UiState(
        val activityId: String? = null,
        val originalTags: List<String> = emptyList(),
        val placeName: String = "",
        val address: String = "",
        val placeSuggestions: List<PlaceResult> = emptyList(),
        val pickedLat: Double? = null,
        val pickedLng: Double? = null,
        val expectedPrice: String = "",
        val tags: List<String> = emptyList(),
        val customTag: String = "",
        val tagError: String? = null,
        val notes: String = "",
        val visibility: ActivityVisibility = ActivityVisibility.PRIVATE,
        val isSaving: Boolean = false,
        val isOnline: Boolean = true,
        val errorMessage: String? = null,
        val didSave: Boolean = false,
        val savedOffline: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState(isOnline = networkMonitor.isCurrentlyOnline()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online, placeSuggestions = if (online) it.placeSuggestions else emptyList()) }
            }
        }
    }

    fun load(activityId: String) {
        if (_uiState.value.activityId == activityId) return
        viewModelScope.launch {
            val fromNetwork = if (_uiState.value.isOnline) repository.getActivity(activityId) else null
            val activity = fromNetwork?.getOrNull() ?: repository.getCachedActivity(activityId)
            if (activity != null) {
                showForEditing(activity)
            } else {
                val message = fromNetwork?.exceptionOrNull()?.message
                    ?: "This activity isn't saved on your phone. Connect to the internet to edit it."
                _uiState.update { it.copy(errorMessage = message) }
            }
        }
    }

    private fun showForEditing(activity: Activity) = _uiState.update {
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

    fun onPlaceNameChange(value: String) = _uiState.update { it.copy(placeName = value) }

    fun onAddressChange(value: String) {
        _uiState.update { it.copy(address = value, pickedLat = null, pickedLng = null) }
        searchJob?.cancel()
        if (value.trim().length < 3 || !_uiState.value.isOnline) {
            _uiState.update { it.copy(placeSuggestions = emptyList()) }
            return
        }
        val query = value.trim().lowercase()
        searchCache[query]?.let { cached ->
            _uiState.update { it.copy(placeSuggestions = cached) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            val results = placesRepository.search(value.trim(), locationService.currentLocation())
                .onSuccess { rememberSearch(query, it) }
                .getOrDefault(emptyList())
            _uiState.update { it.copy(placeSuggestions = results) }
        }
    }

    private fun rememberSearch(query: String, results: List<PlaceResult>) {
        if (searchCache.size >= MAX_CACHED_SEARCHES) searchCache.removeAt(0)
        searchCache[query] = results
    }

    fun onPlaceSelected(place: PlaceResult) {
        searchJob?.cancel()
        _uiState.update { it.copy(placeSuggestions = emptyList()) }
        viewModelScope.launch {
            placesRepository.details(place.id)
                .onSuccess { details ->
                    _uiState.update {
                        it.copy(
                            placeName = it.placeName.ifBlank { details.name },
                            address = details.address,
                            pickedLat = details.lat,
                            pickedLng = details.lng
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }
        }
    }

    fun onExpectedPriceChange(value: String) = _uiState.update { it.copy(expectedPrice = value) }

    fun onTagToggle(tag: String) = _uiState.update {
        it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag)
    }

    fun onCustomTagChange(value: String) = _uiState.update { it.copy(customTag = value, tagError = null) }

    fun addCustomTag() {
        val tag = _uiState.value.customTag.trim().replace(Regex("\\s+"), " ").lowercase()
        val error = when {
            tag.isEmpty() -> "Type a tag first."
            tag.length > MAX_TAG_LENGTH -> "Tags can have up to $MAX_TAG_LENGTH characters."
            !validTag.matches(tag) -> "Tags can only have letters, numbers, spaces and hyphens."
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(tagError = error) }
            return
        }
        _uiState.update {
            it.copy(tags = if (tag in it.tags) it.tags else it.tags + tag, customTag = "", tagError = null)
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

            val isPendingActivity = current.activityId != null &&
                current.activityId in OfflineSyncService.pendingActivityIds()
            if (!current.isOnline || isPendingActivity) {
                saveOffline(current)
                if (current.isOnline) OfflineSyncService.syncNow()
                return@launch
            }

            val geocoded = if (current.pickedLat == null) locationService.coordinatesFor(current.address.trim()) else null
            val lat = current.pickedLat ?: geocoded?.latitude
            val lng = current.pickedLng ?: geocoded?.longitude
            repository.saveActivity(
                activityId = current.activityId,
                name = current.placeName.trim(),
                address = current.address.trim(),
                price = current.expectedPrice.filter { it.isDigit() }.toIntOrNull() ?: 0,
                tags = current.tags,
                newCustomTags = current.tags.filter { it !in ActivityCategories && it !in current.originalTags },
                notes = current.notes.trim(),
                visibility = current.visibility,
                lat = lat,
                lng = lng
            )
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, didSave = true) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = error.message) }
                }
        }
    }

    private suspend fun saveOffline(current: UiState) {
        OfflineSyncService.saveActivityOffline(
            PendingAction.SaveActivity(
                activityId = current.activityId ?: repository.newActivityId(),
                isNew = current.activityId == null,
                name = current.placeName.trim(),
                address = current.address.trim(),
                price = current.expectedPrice.filter { it.isDigit() }.toIntOrNull() ?: 0,
                tags = current.tags,
                newCustomTags = current.tags.filter { it !in ActivityCategories && it !in current.originalTags },
                notes = current.notes.trim(),
                visibility = current.visibility,
                lat = current.pickedLat,
                lng = current.pickedLng
            )
        )
        _uiState.update { it.copy(isSaving = false, didSave = true, savedOffline = !current.isOnline) }
    }
}
