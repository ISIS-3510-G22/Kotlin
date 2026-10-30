package com.example.plansync.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.ActivityRepository
import com.example.plansync.data.AnalyticsRepository
import com.example.plansync.data.InsightsRepository
import com.example.plansync.data.PlanDraftRepository
import com.example.plansync.data.PlanRepository
import com.example.plansync.model.Activity
import com.example.plansync.model.PendingAction
import com.example.plansync.service.LocationService
import com.example.plansync.service.NetworkMonitor
import com.example.plansync.service.OfflineSyncService
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class CreatePlanViewModel(application: Application) : AndroidViewModel(application) {

    private val planRepository      = PlanRepository()
    private val activityRepository  = ActivityRepository()
    private val analyticsRepository = AnalyticsRepository()
    private val insightsRepository  = InsightsRepository()
    private val locationService     = LocationService(application)
    private val draftRepository     = PlanDraftRepository(application)
    private val networkMonitor      = NetworkMonitor.get(application)

    // Stable for the lifetime of this ViewModel instance — identifies this funnel session.
    private val flowSessionId = UUID.randomUUID().toString()
    private var lastStepLogged = 0

    data class UiState(
        val planName: String = "",
        val selectedDateMillis: Long? = null,
        val selectedHour: Int = 10,
        val selectedMinute: Int = 0,
        val locationText: String = "",
        val participantCount: Int = 1,
        val isPublic: Boolean = false,
        val availableActivities: List<Activity> = emptyList(),
        val recommendedActivities: List<Activity> = emptyList(),
        val selectedActivities: List<Activity> = emptyList(),
        val showDatePicker: Boolean = false,
        val showTimePicker: Boolean = false,
        val showActivityPicker: Boolean = false,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val isSaved: Boolean = false,
        val pendingSync: Boolean = false,  // queued offline, will sync when back online
        val origin: String? = null,
        val sourceId: String? = null,
        val popularDayLabel: String? = null
    ) {
        val totalEstimatedCost: Double
            get() = selectedActivities.sumOf { it.price.toDouble() }

        val costPerPerson: Double
            get() = if (participantCount > 0) totalEstimatedCost / participantCount
                    else totalEstimatedCost

        val formattedDate: String
            get() = selectedDateMillis?.let {
                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(it))
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

    init {
        loadAvailableActivities()
        loadRecommendedActivities()
        loadDraft()
        loadPopularDays()
        // Reload activity list on reconnect so the picker isn't stuck on the cached set
        viewModelScope.launch {
            networkMonitor.isOnline
                .distinctUntilChanged()
                .filter { it }
                .collect { loadAvailableActivities() }
        }
    }

    // ── Init ───────────────────────────────────────────────────────────────────

    private fun loadAvailableActivities() {
        viewModelScope.launch {
            activityRepository.getActivities()
                .onSuccess { activities ->
                    activityRepository.cacheActivities(activities)
                    _uiState.update { it.copy(availableActivities = activities) }
                }
                .onFailure {
                    val cached = activityRepository.getCachedActivities()
                    _uiState.update { it.copy(availableActivities = cached) }
                }
        }
    }

    private fun loadRecommendedActivities() {
        viewModelScope.launch {
            activityRepository.getRecommended()
                .onSuccess { activities ->
                    _uiState.update { it.copy(recommendedActivities = activities) }
                }
        }
    }

    private fun loadPopularDays() {
        viewModelScope.launch {
            val top = insightsRepository.getPopularDays().firstOrNull()
            if (top != null) _uiState.update { it.copy(popularDayLabel = top.day) }
        }
    }

    private fun loadDraft() {
        viewModelScope.launch {
            val draft = draftRepository.load()
            if (draft.planName.isBlank() && draft.selectedDateMillis == null) return@launch
            val activityIds = draft.selectedActivityIds.toSet()
            val matchedActivities = activityRepository.getActivities()
                .getOrDefault(emptyList())
                .filter { it.id in activityIds }
            _uiState.update { current ->
                current.copy(
                    planName           = draft.planName,
                    selectedDateMillis = draft.selectedDateMillis,
                    selectedHour       = draft.selectedHour,
                    selectedMinute     = draft.selectedMinute,
                    locationText       = draft.locationText,
                    participantCount   = draft.participantCount,
                    isPublic           = draft.isPublic,
                    selectedActivities = matchedActivities
                )
            }
        }
    }

    private fun saveDraft() {
        viewModelScope.launch {
            val s = _uiState.value
            draftRepository.save(
                PlanDraftRepository.Draft(
                    planName           = s.planName,
                    selectedDateMillis = s.selectedDateMillis,
                    selectedHour       = s.selectedHour,
                    selectedMinute     = s.selectedMinute,
                    locationText       = s.locationText,
                    participantCount   = s.participantCount,
                    isPublic           = s.isPublic,
                    selectedActivityIds = s.selectedActivities.map { it.id },
                    origin             = s.origin,
                    sourceId           = s.sourceId
                )
            )
        }
    }

    // ── Origin (set once from nav arg) ─────────────────────────────────────────

    fun setOrigin(origin: String?, sourceId: String?) {
        _uiState.update { it.copy(origin = origin, sourceId = sourceId) }
    }

    // ── Location ───────────────────────────────────────────────────────────────

    fun onLocationDetected(location: String) {
        _uiState.update { it.copy(locationText = location) }
        saveDraft()
    }

    fun detectLocation() {
        viewModelScope.launch {
            val loc = locationService.currentLocation()
            val address = if (loc != null)
                locationService.reverseGeocode(loc.latitude, loc.longitude)
            else "Unable to detect location"
            _uiState.update { it.copy(locationText = address) }
            saveDraft()
        }
    }

    // ── Form field changes ─────────────────────────────────────────────────────

    fun onPlanNameChange(name: String) {
        val wasBlank = _uiState.value.planName.isBlank()
        _uiState.update { it.copy(planName = name) }
        saveDraft()
        if (wasBlank && name.isNotBlank()) logStep(1)
    }

    fun onIsPublicChanged(v: Boolean) {
        _uiState.update { it.copy(isPublic = v) }
        saveDraft()
    }

    fun onParticipantCountChange(count: Int) {
        _uiState.update { it.copy(participantCount = count.coerceAtLeast(1)) }
        saveDraft()
    }

    // ── Date / Time picker ─────────────────────────────────────────────────────

    fun onShowDatePicker()  { _uiState.update { it.copy(showDatePicker = true) } }
    fun onHideDatePicker()  { _uiState.update { it.copy(showDatePicker = false) } }
    fun onShowTimePicker()  { _uiState.update { it.copy(showTimePicker = true) } }
    fun onHideTimePicker()  { _uiState.update { it.copy(showTimePicker = false) } }

    fun onDateSelected(millis: Long) {
        _uiState.update { it.copy(selectedDateMillis = millis, showDatePicker = false) }
        saveDraft()
        logStep(2)
    }

    fun onTimeSelected(hour: Int, minute: Int) {
        _uiState.update { it.copy(selectedHour = hour, selectedMinute = minute, showTimePicker = false) }
        saveDraft()
    }

    // ── Activity picker ────────────────────────────────────────────────────────

    fun onShowActivityPicker()  { _uiState.update { it.copy(showActivityPicker = true) } }
    fun onHideActivityPicker()  { _uiState.update { it.copy(showActivityPicker = false) } }

    fun onActivityToggled(activity: Activity) {
        val wasEmpty = _uiState.value.selectedActivities.isEmpty()
        _uiState.update { state ->
            val updated = if (state.selectedActivities.any { it.id == activity.id })
                state.selectedActivities.filter { it.id != activity.id }
            else state.selectedActivities + activity
            state.copy(selectedActivities = updated)
        }
        saveDraft()
        if (wasEmpty && _uiState.value.selectedActivities.isNotEmpty()) logStep(3)
    }

    fun preSelectActivity(activityId: String) {
        viewModelScope.launch {
            activityRepository.getActivities().onSuccess { activities ->
                val activity = activities.find { it.id == activityId } ?: return@onSuccess
                _uiState.update { it.copy(selectedActivities = listOf(activity)) }
                saveDraft()
            }
        }
    }

    fun onErrorDismissed() { _uiState.update { it.copy(errorMessage = null) } }

    // ── Analytics helpers ──────────────────────────────────────────────────────

    private fun logStep(step: Int) {
        if (step <= lastStepLogged) return
        lastStepLogged = step
        val s = _uiState.value
        analyticsRepository.logEvent(
            type          = AnalyticsRepository.CREATE_PLAN_STEP,
            step          = step,
            flowSessionId = flowSessionId,
            origin        = s.origin,
            sourceId      = s.sourceId
        )
    }

    override fun onCleared() {
        super.onCleared()
        // Log abandoned if the user left mid-funnel without saving.
        if (!_uiState.value.isSaved && lastStepLogged > 0) {
            val s = _uiState.value
            analyticsRepository.logEvent(
                type          = AnalyticsRepository.CREATE_PLAN_ABANDONED,
                step          = lastStepLogged,
                flowSessionId = flowSessionId,
                origin        = s.origin,
                sourceId      = s.sourceId
            )
        }
    }

    // ── Save ───────────────────────────────────────────────────────────────────

    private fun planDateTime(pickedMillis: Long, hour: Int, minute: Int): Date {
        val picked = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        picked.timeInMillis = pickedMillis
        val local = Calendar.getInstance()
        local.clear()
        local.set(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH), picked.get(Calendar.DAY_OF_MONTH), hour, minute)
        return local.time
    }

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
        if (planDateTime(state.selectedDateMillis, state.selectedHour, state.selectedMinute).before(Date())) {
            _uiState.update { it.copy(errorMessage = "The plan date and time must be in the future.") }
            return
        }

        val tags = state.selectedActivities.flatMap { it.tags }.filter { it.isNotBlank() }.distinct()

        val planId = UUID.randomUUID().toString()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val isOnline = networkMonitor.isCurrentlyOnline()
            if (!isOnline) {
                // Queue the write; it will be uploaded by OfflineSyncService when back online.
                OfflineSyncService.createPlanOffline(
                    PendingAction.CreatePlan(
                        planId      = planId,
                        name        = state.planName,
                        dateMillis  = state.selectedDateMillis,
                        hour        = state.selectedHour,
                        minute      = state.selectedMinute,
                        isPublic    = state.isPublic,
                        activityIds = state.selectedActivities.map { it.id },
                        tags        = tags,
                        origin      = state.origin,
                        sourceId    = state.sourceId
                    )
                )
                draftRepository.clear()
                _uiState.update { it.copy(isLoading = false, pendingSync = true) }
                return@launch
            }

            planRepository.createPlan(
                planId      = planId,
                name        = state.planName,
                date        = Timestamp(planDateTime(state.selectedDateMillis, state.selectedHour, state.selectedMinute)),
                isPublic    = state.isPublic,
                activityIds = state.selectedActivities.map { it.id },
                tags        = tags,
                origin      = state.origin,
                sourceId    = state.sourceId
            ).onSuccess {
                analyticsRepository.logEvent(
                    type          = AnalyticsRepository.PLAN_CREATED,
                    planId        = planId,
                    flowSessionId = flowSessionId,
                    origin        = state.origin,
                    sourceId      = state.sourceId
                )
                draftRepository.clear()
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
            }
        }
    }
}
