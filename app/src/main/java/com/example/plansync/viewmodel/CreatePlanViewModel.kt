package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel layer — MVVM architecture.
 *
 * Owns UI state for the Create Plan screen.
 * Observer pattern: exposes [uiState] as StateFlow, collected by
 * CreatePlanScreen via collectAsStateWithLifecycle().
 *
 * The Composable never touches the repository directly.
 * TODO: wire onSavePlan() to Firestore once data model is ready.
 */
class CreatePlanViewModel : ViewModel() {

    /** A lightweight activity entry used while the plan is being drafted. */
    data class DraftActivity(
        val name: String,
        val address: String
    )

    data class UiState(
        val planName: String = "",
        val date: String = "",
        val meetupTime: String = "",
        val locationText: String = "",
        val participantCount: Int = 1,
        val activities: List<DraftActivity> = listOf(
            DraftActivity("Dumbo House", "55 Water St, Brooklyn"),
            DraftActivity("Devoción", "69 Grand St, Brooklyn")
        ),
        val totalEstimatedCost: Double = 10.0,
        val isSaved: Boolean = false
    ) {
        /** Smart feature: live cost per person derived from total and group size. */
        val costPerPerson: Double
            get() = if (participantCount > 0) totalEstimatedCost / participantCount else totalEstimatedCost
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onPlanNameChange(name: String) {
        _uiState.update { it.copy(planName = name) }
    }

    fun onDateChange(date: String) {
        _uiState.update { it.copy(date = date) }
    }

    fun onMeetupTimeChange(time: String) {
        _uiState.update { it.copy(meetupTime = time) }
    }

    /**
     * Sensor feature: stores the address string obtained from GPS + reverse geocoding.
     * Called by the UI layer once location permission is granted and coordinates resolved.
     */
    fun onLocationDetected(location: String) {
        _uiState.update { it.copy(locationText = location) }
    }

    /**
     * Smart feature: updates group size and recomputes cost per person reactively.
     * [UiState.costPerPerson] is a derived property — UI observes a single source of truth.
     */
    fun onParticipantCountChange(count: Int) {
        _uiState.update { it.copy(participantCount = count.coerceAtLeast(1)) }
    }

    /**
     * Saves the plan draft.
     * Sets [UiState.isSaved] to true to trigger navigation back.
     * TODO: persist to Firestore.
     */
    fun onSavePlan() {
        _uiState.update { it.copy(isSaved = true) }
    }
}
