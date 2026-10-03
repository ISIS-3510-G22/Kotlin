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
        val invites: List<Plan> = emptyList(),
        val selectedTab: PlanStatus = PlanStatus.CONFIRMED,
        val filteredPlans: List<Plan> = emptyList(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val invitesError: String? = null,
        val respondError: String? = null,
        val selectedInvite: Plan? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var initialTabChosen = false

    fun loadPlans() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, invitesError = null, respondError = null) }

            repository.getMyPlans()
                .onSuccess { plans -> _uiState.update { it.copy(allPlans = plans) } }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }

            repository.getMyInvites()
                .onSuccess { invites -> _uiState.update { it.copy(invites = invites) } }
                .onFailure { error -> _uiState.update { it.copy(invitesError = error.message) } }

            if (!initialTabChosen) {
                initialTabChosen = true
                if (_uiState.value.invites.isNotEmpty()) {
                    _uiState.update { it.copy(selectedTab = PlanStatus.PENDING_INVITE) }
                }
            }

            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    filteredPlans = plansForTab(current.allPlans + current.invites, current.selectedTab)
                )
            }
        }
    }

    fun selectTab(tab: PlanStatus) {
        initialTabChosen = true
        _uiState.update { current ->
            current.copy(
                selectedTab = tab,
                respondError = null,
                filteredPlans = plansForTab(current.allPlans + current.invites, tab)
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
        respondToInvite(InviteResponse.GOING)
    }

    fun onInviteCantMake() {
        respondToInvite(InviteResponse.CANT_MAKE)
    }

    private fun respondToInvite(response: InviteResponse) {
        val invite = _uiState.value.selectedInvite
        if (invite == null) {
            return
        }
        _uiState.update { it.copy(selectedInvite = null) }

        viewModelScope.launch {
            repository.respondToInvite(invite.id, response)
                .onSuccess { loadPlans() }
                .onFailure { error -> _uiState.update { it.copy(respondError = error.message) } }
        }
    }

    private fun plansForTab(plans: List<Plan>, tab: PlanStatus): List<Plan> {
        val result = mutableListOf<Plan>()
        for (plan in plans) {
            if (plan.status == tab) {
                result.add(plan)
            }
        }

        val withDate = result.filter { it.dateTime != null }
        val withoutDate = result.filter { it.dateTime == null }
        val sorted = if (tab == PlanStatus.PAST) {
            withDate.sortedByDescending { it.dateTime }
        } else {
            withDate.sortedBy { it.dateTime }
        }
        return sorted + withoutDate
    }
}
