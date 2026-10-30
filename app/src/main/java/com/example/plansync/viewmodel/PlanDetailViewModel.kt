package com.example.plansync.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.AnalyticsRepository
import com.example.plansync.data.PlanDatabase
import com.example.plansync.data.PlanRepository
import com.example.plansync.model.PendingAction
import com.example.plansync.service.NetworkMonitor
import com.example.plansync.service.NotificationFactory
import com.example.plansync.service.NotificationService
import com.example.plansync.service.OfflineSyncService
import com.example.plansync.model.Plan
import com.example.plansync.model.PlanStatus
import com.example.plansync.model.RsvpStatus
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
import kotlinx.coroutines.withContext


class PlanDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlanRepository(planDao = PlanDatabase.get(application).planDao())
    private val analyticsRepository = AnalyticsRepository()
    private val notificationService = NotificationService(application)
    private val networkMonitor = NetworkMonitor.get(application)

    private var currentPlanId: String? = null

    data class UiState(
        val plan: Plan? = null,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val showRsvpDialog: Boolean = false,
        val pendingSync: Boolean = false,
        val neverLoaded: Boolean = false,
        val isOnline: Boolean = true
    )

    init {
        networkMonitor.isOnline
            .onEach { online -> _uiState.update { it.copy(isOnline = online) } }
            .launchIn(viewModelScope)
        // Reload when coming back online (only fires on false→true transitions)
        viewModelScope.launch {
            networkMonitor.isOnline
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    OfflineSyncService.syncNow()
                    currentPlanId?.let { id -> loadPlan(id) }
                    _uiState.update { it.copy(pendingSync = OfflineSyncService.hasPendingActions()) }
                }
        }
        viewModelScope.launch {
            OfflineSyncService.syncErrors.collect { msg ->
                _uiState.update { it.copy(errorMessage = msg) }
            }
        }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    
    fun onRsvpTriggered() {
        _uiState.update { it.copy(showRsvpDialog = true) }
    }

    
    fun onRsvpDismissed() {
        _uiState.update { it.copy(showRsvpDialog = false) }
    }

    fun onRsvpGoing(planId: String) {
        analyticsRepository.logEvent(AnalyticsRepository.RSVP_CONFIRMED, planId)
        notifyRsvp(AnalyticsRepository.RSVP_CONFIRMED)
        _uiState.update { it.copy(showRsvpDialog = false) }
        setRsvpWithOfflineFallback(planId, RsvpStatus.GOING)
    }

    fun onRsvpDeclined(planId: String) {
        analyticsRepository.logEvent(AnalyticsRepository.RSVP_DECLINED, planId)
        notifyRsvp(AnalyticsRepository.RSVP_DECLINED)
        _uiState.update { it.copy(showRsvpDialog = false) }
        setRsvpWithOfflineFallback(planId, RsvpStatus.NOT_GOING)
    }

    private fun setRsvpWithOfflineFallback(planId: String, status: RsvpStatus) {
        viewModelScope.launch {
            if (!networkMonitor.isCurrentlyOnline()) {
                OfflineSyncService.enqueue(PendingAction.SetRsvp(planId, status.value))
                _uiState.update { it.copy(pendingSync = true) }
                return@launch
            }
            repository.setRsvp(planId, status).onSuccess { loadPlan(planId) }
        }
    }

    fun onRsvpDeferred(planId: String) {
        analyticsRepository.logEvent(AnalyticsRepository.RSVP_DEFERRED, planId)
        notifyRsvp(AnalyticsRepository.RSVP_DEFERRED)
        _uiState.update { it.copy(showRsvpDialog = false) }
    }

    fun loadPlan(planId: String) {
        currentPlanId = planId
        viewModelScope.launch(Dispatchers.Main) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, neverLoaded = false) }

            val result = withContext(Dispatchers.IO) { repository.getPlan(planId) }

            result
                .onSuccess { plan ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            plan = plan,
                            showRsvpDialog = plan.status == PlanStatus.PENDING_INVITE
                        )
                    }
                }
                .onFailure { error ->
                    val neverLoaded = error.message == "connect_to_load"
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (neverLoaded) null else error.message,
                            neverLoaded = neverLoaded
                        )
                    }
                }
        }
    }

    fun onErrorShown() { _uiState.update { it.copy(errorMessage = null) } }

    private fun notifyRsvp(event: String) {
        val planName = _uiState.value.plan?.title.orEmpty()
        NotificationFactory.create(event, planName = planName)?.let(notificationService::show)
    }
}
