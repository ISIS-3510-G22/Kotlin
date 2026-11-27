package com.example.plansync.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.ActivityRepository
import com.example.plansync.data.AnalyticsRepository
import com.example.plansync.data.InsightsRepository
import com.example.plansync.data.PlanDraftRepository
import com.example.plansync.data.PlanRepository
import com.example.plansync.data.planDateTime
import com.example.plansync.model.Activity
import com.example.plansync.model.PendingAction
import com.example.plansync.service.LocationService
import com.example.plansync.service.NetworkMonitor
import com.example.plansync.service.OfflineSyncService
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class CreatePlanViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    // draftId is populated by Navigation Compose from the route argument.
    private val initialDraftId: String? = savedStateHandle["draftId"]

    private val planRepository      = PlanRepository()
    private val activityRepository  = ActivityRepository()
    private val analyticsRepository = AnalyticsRepository()
    private val insightsRepository  = InsightsRepository()
    private val locationService     = LocationService(application)
    private val draftRepository     = PlanDraftRepository()
    private val networkMonitor      = NetworkMonitor.get(application)

    private val flowSessionId = UUID.randomUUID().toString()
    private var lastStepLogged = 0
    private var currentDraftId: String = flowSessionId

    data class UiState(
        val planName: String = "",
        val selectedDateMillis: Long? = null,
        val selectedHour: Int = 10,
        val selectedMinute: Int = 0,
        val locationText: String = "",
        val participantCount: Int = 1,
        val participantText: String = "",
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
        val pendingSync: Boolean = false,
        val isOnline: Boolean = true,
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
        // Resume a specific draft when navigated here from My Drafts; otherwise start fresh.
        if (initialDraftId != null) resumeDraft(initialDraftId)
        loadPopularDays()
        networkMonitor.isOnline
            .onEach { online -> _uiState.update { it.copy(isOnline = online) } }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            networkMonitor.isOnline
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    OfflineSyncService.syncNow()
                    loadAvailableActivities()
                    _uiState.update { it.copy(pendingSync = OfflineSyncService.hasPendingActions()) }
                }
        }
        viewModelScope.launch {
            OfflineSyncService.syncErrors.collect { msg ->
                _uiState.update { it.copy(errorMessage = msg) }
            }
        }
    }

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

    fun resumeDraft(draftId: String) {
        viewModelScope.launch {
            val draft = draftRepository.loadById(draftId) ?: return@launch
            currentDraftId = draftId
            applyDraft(draft)
        }
    }

    private suspend fun applyDraft(draft: PlanDraftRepository.Draft) {
        lastStepLogged = draft.lastStep
        val activityIds = draft.selectedActivityIds.toSet()
        val matched = activityRepository.getActivities()
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
                participantText    = if (draft.participantCount <= 1) "" else draft.participantCount.toString(),
                isPublic           = draft.isPublic,
                selectedActivities = matched,
                origin             = draft.origin,
                sourceId           = draft.sourceId
            )
        }
    }

    private fun saveDraft() {
        viewModelScope.launch {
            val s = _uiState.value
            draftRepository.save(
                PlanDraftRepository.Draft(
                    id                  = currentDraftId,
                    planName            = s.planName,
                    selectedDateMillis  = s.selectedDateMillis,
                    selectedHour        = s.selectedHour,
                    selectedMinute      = s.selectedMinute,
                    locationText        = s.locationText,
                    participantCount    = s.participantCount,
                    isPublic            = s.isPublic,
                    selectedActivityIds = s.selectedActivities.map { it.id },
                    origin              = s.origin,
                    sourceId            = s.sourceId,
                    lastStep            = lastStepLogged
                )
            )
        }
    }

    fun setOrigin(origin: String?, sourceId: String?) {
        _uiState.update { it.copy(origin = origin, sourceId = sourceId) }
    }

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

    fun onParticipantCountChange(text: String) {
        val digits = text.filter(Char::isDigit)
        val count  = digits.toIntOrNull() ?: 0
        _uiState.update { it.copy(participantText = digits, participantCount = count.coerceAtLeast(1)) }
        saveDraft()
    }

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

    fun onPendingSyncDismissed() { _uiState.update { it.copy(pendingSync = false) } }

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
                draftRepository.delete(currentDraftId)
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
                draftRepository.delete(currentDraftId)
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
            }
        }
    }
}
