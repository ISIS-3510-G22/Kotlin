package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.ActivityRepository
import com.example.plansync.data.PlanRepository
import com.example.plansync.model.Activity
import com.example.plansync.model.PendingAction
import com.example.plansync.model.Plan
import com.example.plansync.service.OfflineSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AddToPlanViewModel(
    private val activityRepository: ActivityRepository = ActivityRepository(),
    private val planRepository: PlanRepository = PlanRepository()
) : ViewModel() {

    data class UiState(
        val activity: Activity? = null,
        val plans: List<Plan> = emptyList(),
        val selectedPlanId: String? = null,
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        val isOnline: Boolean = true,
        val errorMessage: String? = null,
        val didSave: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var currentActivityId: String? = null

    init {
        viewModelScope.launch {
            OfflineSyncService.isOnline.collect { online ->
                val cameBackOnline = online && !_uiState.value.isOnline
                _uiState.update { it.copy(isOnline = online) }
                if (cameBackOnline) {
                    OfflineSyncService.syncNow()
                    currentActivityId?.let(::load)
                }
            }
        }
    }

    fun load(activityId: String) {
        currentActivityId = activityId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val online = OfflineSyncService.isOnlineNow()
            val activity = activityRepository.getActivity(activityId).getOrNull()
                ?: activityRepository.getCachedActivity(activityId)
            val plans = OfflineSyncService.loadPlansForAddToPlan(online)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    activity = activity,
                    plans = plans.getOrDefault(emptyList()),
                    errorMessage = when {
                        activity == null -> "Connect to the internet to load this activity."
                        plans.isFailure -> "Connect to the internet to load your plans."
                        else -> null
                    }
                )
            }
        }
    }

    fun selectPlan(planId: String) = _uiState.update { it.copy(selectedPlanId = planId) }

    fun addToPlan() {
        val current = _uiState.value
        val activity = current.activity ?: return
        val planId = current.selectedPlanId ?: return
        if (current.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }

            if (!OfflineSyncService.isOnlineNow()) {
                OfflineSyncService.addActivityToPlanOffline(
                    PendingAction.AddActivityToPlan(planId, activity.id, activity.name, activity.tags)
                )
                _uiState.update { it.copy(isSaving = false, didSave = true) }
                return@launch
            }

            planRepository.addActivityToPlan(planId, activity.id, activity.tags)
                .onSuccess { _uiState.update { it.copy(isSaving = false, didSave = true) } }
                .onFailure { error -> _uiState.update { it.copy(isSaving = false, errorMessage = error.message) } }
        }
    }
}
