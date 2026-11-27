package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.CrewRepository
import com.example.plansync.data.SplitRepository
import com.example.plansync.model.Expense
import com.example.plansync.service.OfflineSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlanExpensesViewModel(
    private val crewRepository: CrewRepository = CrewRepository()
) : ViewModel() {

    data class UiState(
        val expenses: List<Expense> = emptyList(),
        val names: Map<String, String> = emptyMap(),
        val isLoading: Boolean = false,
        val isOnline: Boolean = true,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var currentPlanId: String? = null

    init {
        viewModelScope.launch {
            OfflineSyncService.isOnline.collect { online ->
                val cameBackOnline = online && !_uiState.value.isOnline
                _uiState.update { it.copy(isOnline = online) }
                if (cameBackOnline) {
                    OfflineSyncService.syncNow()
                    currentPlanId?.let(::load)
                }
            }
        }
    }

    fun load(planId: String) {
        currentPlanId = planId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val expenses = OfflineSyncService.loadExpenses(planId).getOrElse { error ->
                val message = if (error.message == SplitRepository.CONNECT_TO_LOAD) {
                    "Connect to the internet to load this plan's expenses."
                } else {
                    error.message
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = message) }
                return@launch
            }
            val names = crewRepository.getUsersByIds(expenses.map { it.paidById })
                .getOrDefault(emptyList())
                .associate { it.id to it.fullName }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    expenses = expenses.sortedByDescending { expense -> expense.createdAt },
                    names = names
                )
            }
        }
    }
}
