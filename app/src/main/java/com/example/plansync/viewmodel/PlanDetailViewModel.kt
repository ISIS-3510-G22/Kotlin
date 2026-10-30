package com.example.plansync.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.AnalyticsRepository
import com.example.plansync.data.PlanRepository
import com.example.plansync.model.PendingAction
import com.example.plansync.service.NetworkMonitor
import com.example.plansync.service.NotificationFactory
import com.example.plansync.service.NotificationService
import com.example.plansync.service.OfflineSyncService
import com.example.plansync.model.Plan
import com.example.plansync.model.PlanStatus
import com.example.plansync.model.RsvpStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class PlanDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlanRepository()
    private val analyticsRepository = AnalyticsRepository()
    private val notificationService = NotificationService(application)

    
    data class UiState(
        val plan: Plan? = null,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val showRsvpDialog: Boolean = false,
        val pendingSync: Boolean = false
    )

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
            if (!NetworkMonitor.get(getApplication()).isCurrentlyOnline()) {
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            repository.getPlan(planId)
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
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    private fun notifyRsvp(event: String) {
        val planName = _uiState.value.plan?.title.orEmpty()
        NotificationFactory.create(event, planName = planName)?.let(notificationService::show)
    }
}
