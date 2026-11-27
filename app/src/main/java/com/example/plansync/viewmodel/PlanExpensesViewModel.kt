package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.CrewRepository
import com.example.plansync.data.SplitRepository
import com.example.plansync.model.Expense
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlanExpensesViewModel(
    private val splitRepository: SplitRepository = SplitRepository(),
    private val crewRepository: CrewRepository = CrewRepository()
) : ViewModel() {

    data class UiState(
        val expenses: List<Expense> = emptyList(),
        val names: Map<String, String> = emptyMap(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun load(planId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val expenses = splitRepository.getExpenses(planId).getOrElse { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
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
