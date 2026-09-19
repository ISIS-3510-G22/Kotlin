package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.PlanRepository
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
        val errorMessage: String? = null
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
                            filteredPlans = plans.filter { plan -> plan.status == current.selectedTab }
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
                filteredPlans = current.allPlans.filter { plan -> plan.status == tab }
            )
        }
    }
}
