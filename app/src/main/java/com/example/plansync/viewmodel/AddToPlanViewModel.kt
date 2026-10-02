package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.ActivityRepository
import com.example.plansync.data.PlanRepository
import com.example.plansync.model.Activity
import com.example.plansync.model.Plan
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
        val errorMessage: String? = null,
        val didSave: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun load(activityId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val activity = activityRepository.getActivity(activityId)
            val plans = planRepository.getMyPlans()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    activity = activity.getOrNull(),
                    plans = plans.getOrDefault(emptyList()),
                    errorMessage = (activity.exceptionOrNull() ?: plans.exceptionOrNull())?.message
                )
            }
        }
    }

    fun selectPlan(planId: String) = _uiState.update { it.copy(selectedPlanId = planId) }

    fun addToPlan() {
        val current = _uiState.value
        val activity = current.activity ?: return
        val planId = current.selectedPlanId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            planRepository.addActivityToPlan(planId, activity.id, activity.tags)
                .onSuccess { _uiState.update { it.copy(isSaving = false, didSave = true) } }
                .onFailure { error -> _uiState.update { it.copy(isSaving = false, errorMessage = error.message) } }
        }
    }
}
