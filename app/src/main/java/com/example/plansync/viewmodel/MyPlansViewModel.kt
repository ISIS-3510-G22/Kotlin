package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.PlanRepository
import com.example.plansync.model.InviteResponse
import com.example.plansync.model.Plan
import com.example.plansync.model.PlanStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class MyPlansViewModel(
    private val repository: PlanRepository = PlanRepository()
) : ViewModel() {

    data class UiState(
        val allPlans: List<Plan> = emptyList(),
        val selectedTab: PlanStatus = PlanStatus.CONFIRMED,
        val filteredPlans: List<Plan> = emptyList(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val selectedInvite: Plan? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun loadPlans() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            repository.getPlans()
                .onSuccess { plans ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            allPlans = plans,
                            filteredPlans = plansForTab(plans, current.selectedTab)
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    fun selectTab(tab: PlanStatus) {
        _uiState.update { current ->
            current.copy(
                selectedTab = tab,
                filteredPlans = plansForTab(current.allPlans, tab)
            )
        }
    }

    fun onInviteTapped(plan: Plan) {
        _uiState.update { it.copy(selectedInvite = plan) }
    }

    fun onInviteDismissed() {
        _uiState.update { it.copy(selectedInvite = null) }
    }

    fun onInviteGoing() {
        val invite = _uiState.value.selectedInvite
        if (invite == null) {
            return
        }

        viewModelScope.launch {
            repository.respondToInvite(invite.id, InviteResponse.GOING)
                .onSuccess {
                    val updatedPlans = mutableListOf<Plan>()
                    for (plan in _uiState.value.allPlans) {
                        if (plan.id == invite.id) {
                            updatedPlans.add(plan.copy(status = PlanStatus.CONFIRMED))
                        } else {
                            updatedPlans.add(plan)
                        }
                    }
                    _uiState.update { current ->
                        current.copy(
                            allPlans = updatedPlans,
                            filteredPlans = plansForTab(updatedPlans, current.selectedTab),
                            selectedInvite = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message, selectedInvite = null) }
                }
        }
    }

    fun onInviteCantMake() {
        val invite = _uiState.value.selectedInvite
        if (invite == null) {
            return
        }

        viewModelScope.launch {
            repository.respondToInvite(invite.id, InviteResponse.CANT_MAKE)
                .onSuccess {
                    val remainingPlans = mutableListOf<Plan>()
                    for (plan in _uiState.value.allPlans) {
                        if (plan.id != invite.id) {
                            remainingPlans.add(plan)
                        }
                    }
                    _uiState.update { current ->
                        current.copy(
                            allPlans = remainingPlans,
                            filteredPlans = plansForTab(remainingPlans, current.selectedTab),
                            selectedInvite = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message, selectedInvite = null) }
                }
        }
    }

    private fun plansForTab(plans: List<Plan>, tab: PlanStatus): List<Plan> {
        val result = mutableListOf<Plan>()
        for (plan in plans) {
            if (plan.status == tab) {
                result.add(plan)
            }
        }
        return result
    }
}
