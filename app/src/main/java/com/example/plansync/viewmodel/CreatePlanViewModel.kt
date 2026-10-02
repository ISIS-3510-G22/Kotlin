package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.ActivityRepository
import com.example.plansync.data.PlanRepository
import com.example.plansync.model.Activity
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel layer — MVVM architecture.
 *
 * Owns UI state for the Create Plan screen.
 * Observer pattern: exposes [uiState] as StateFlow, collected by
 * CreatePlanScreen via collectAsStateWithLifecycle().
 *
 * Design patterns:
 *  - Repository pattern: delegates persistence to PlanRepository / ActivityRepository.
 *  - Derived state: totalEstimatedCost and costPerPerson are computed from
 *    selectedActivities, so the UI always shows a consistent value (smart feature).
 */
class CreatePlanViewModel(
    private val planRepository: PlanRepository = PlanRepository(),
    private val activityRepository: ActivityRepository = ActivityRepository()
) : ViewModel() {

    data class UiState(
        val planName: String = "",
        val selectedDateMillis: Long? = null,
        val selectedHour: Int = 10,
        val selectedMinute: Int = 0,
        val locationText: String = "",
        val participantCount: Int = 1,
        val isPublic: Boolean = false,
        val availableActivities: List<Activity> = emptyList(),
        val selectedActivities: List<Activity> = emptyList(),
        val showDatePicker: Boolean = false,
        val showTimePicker: Boolean = false,
        val showActivityPicker: Boolean = false,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val isSaved: Boolean = false
    ) {
        /** Smart feature: total cost derived from selected activities' prices. */
        val totalEstimatedCost: Double
            get() = selectedActivities.sumOf { it.price.toDouble() }

        /** Smart feature: cost per person updates live as participant count changes. */
        val costPerPerson: Double
            get() = if (participantCount > 0) totalEstimatedCost / participantCount
                    else totalEstimatedCost

        val formattedDate: String
            get() = selectedDateMillis?.let {
                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(it))
            } ?: ""

        val formattedTime: String
            get() {
                val h = selectedHour % 12
                val display = if (h == 0) 12 else h
                val amPm = if (selectedHour < 12) "AM" else "PM"
                return "$display:${selectedMinute.toString().padStart(2, '0')} $amPm"
            }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadAvailableActivities() }

    // ── Load ───────────────────────────────────────────────────────────────────

    private fun loadAvailableActivities() {
        viewModelScope.launch {
            activityRepository.getActivities()
                .onSuccess { activities ->
                    _uiState.update { it.copy(availableActivities = activities) }
                }
        }
    }

    /**
     * Sensor feature: called by the UI once GPS coordinates are resolved.
     * Pre-populates the meetup location field.
     */
    fun onLocationDetected(location: String) {
        _uiState.update { it.copy(locationText = location) }
    }

    // ── Form field changes ─────────────────────────────────────────────────────

    fun onPlanNameChange(name: String)  { _uiState.update { it.copy(planName = name) } }
    fun onIsPublicChanged(v: Boolean)   { _uiState.update { it.copy(isPublic = v) } }

    /** Smart feature: updates group size, cost-per-person recomputes reactively. */
    fun onParticipantCountChange(count: Int) {
        _uiState.update { it.copy(participantCount = count.coerceAtLeast(1)) }
    }

    // ── Date / Time picker ─────────────────────────────────────────────────────

    fun onShowDatePicker()               { _uiState.update { it.copy(showDatePicker = true) } }
    fun onHideDatePicker()               { _uiState.update { it.copy(showDatePicker = false) } }
    fun onDateSelected(millis: Long)     { _uiState.update { it.copy(selectedDateMillis = millis, showDatePicker = false) } }
    fun onShowTimePicker()               { _uiState.update { it.copy(showTimePicker = true) } }
    fun onHideTimePicker()               { _uiState.update { it.copy(showTimePicker = false) } }
    fun onTimeSelected(hour: Int, minute: Int) {
        _uiState.update { it.copy(selectedHour = hour, selectedMinute = minute, showTimePicker = false) }
    }

    // ── Activity picker ────────────────────────────────────────────────────────

    fun onShowActivityPicker()           { _uiState.update { it.copy(showActivityPicker = true) } }
    fun onHideActivityPicker()           { _uiState.update { it.copy(showActivityPicker = false) } }

    fun onActivityToggled(activity: Activity) {
        _uiState.update { state ->
            val updated = if (state.selectedActivities.any { it.id == activity.id }) {
                state.selectedActivities.filter { it.id != activity.id }
            } else {
                state.selectedActivities + activity
            }
            state.copy(selectedActivities = updated)
        }
    }

    /**
     * Pre-selects an activity by id — called when navigating from "Add to Plan"
     * on the activity detail screen so the activity comes pre-populated.
     */
    fun preSelectActivity(activityId: String) {
        viewModelScope.launch {
            activityRepository.getActivities().onSuccess { activities ->
                val activity = activities.find { it.id == activityId } ?: return@onSuccess
                _uiState.update { it.copy(selectedActivities = listOf(activity)) }
            }
        }
    }

    fun onErrorDismissed() { _uiState.update { it.copy(errorMessage = null) } }

    // ── Save ───────────────────────────────────────────────────────────────────

    /**
     * Validates the form, then calls [PlanRepository.createPlan] to write
     * the document to Firestore. Sets [UiState.isSaved] on success to trigger
     * back navigation via LaunchedEffect in the composable.
     */
    fun onSavePlan() {
        val state = _uiState.value
        if (state.planName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Plan name is required.") }
            return
        }
        if (state.selectedDateMillis == null) {
            _uiState.update { it.copy(errorMessage = "Please select a date.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            planRepository.createPlan(
                name        = state.planName,
                date        = Timestamp(
                    java.util.Calendar.getInstance().apply {
                        timeInMillis = state.selectedDateMillis
                        set(java.util.Calendar.HOUR_OF_DAY, state.selectedHour)
                        set(java.util.Calendar.MINUTE, state.selectedMinute)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }.time
                ),
                isPublic    = state.isPublic,
                activityIds = state.selectedActivities.map { it.id },
                tags        = state.selectedActivities.map { it.category }.distinct()
            ).onSuccess {
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
            }
        }
    }
}
