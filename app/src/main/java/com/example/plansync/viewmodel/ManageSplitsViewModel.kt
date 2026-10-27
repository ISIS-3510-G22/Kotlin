package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.CrewRepository
import com.example.plansync.data.SplitRepository
import com.example.plansync.model.Expense
import com.example.plansync.model.Settlement
import com.example.plansync.model.Split
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PAYMENT_TOLERANCE = 0.01

class ManageSplitsViewModel(
    private val splitRepository: SplitRepository = SplitRepository(),
    private val crewRepository: CrewRepository = CrewRepository()
) : ViewModel() {

    data class UiState(
        val splits: List<Split> = emptyList(),
        val names: Map<String, String> = emptyMap(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val isSaving: Boolean = false,
        val saveErrorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun load(planId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val myId = splitRepository.currentUserId
            if (myId == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "No active session.") }
                return@launch
            }

            val expenses = splitRepository.getExpenses(planId).getOrElse { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                return@launch
            }
            val settlements = splitRepository.getSettlements(planId).getOrElse { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                return@launch
            }

            val splits = buildSplits(myId, expenses, settlements)
            val names = loadNames(splits)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    splits = sortByName(splits, names),
                    names = names
                )
            }
        }
    }

    fun onPaidChange(planId: String, split: Split, paid: Boolean) {
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveErrorMessage = null) }

            val result = if (paid) {
                splitRepository.addSettlement(planId, split.personId, split.amount - split.paidSoFar)
            } else {
                splitRepository.deleteMySettlementsTo(planId, split.personId)
            }

            result
                .onSuccess {
                    val newPaidSoFar = if (paid) split.amount else 0.0
                    _uiState.update { state ->
                        state.copy(
                            isSaving = false,
                            splits = state.splits.map {
                                if (it.personId == split.personId) it.copy(paidSoFar = newPaidSoFar, paid = paid) else it
                            }
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, saveErrorMessage = error.message) }
                }
        }
    }

    private fun buildSplits(myId: String, expenses: List<Expense>, settlements: List<Settlement>): List<Split> {
        val netDebt = mutableMapOf<String, Double>()

        for (expense in expenses) {
            if (expense.splitAmongIds.isEmpty()) continue
            val share = expense.value / expense.splitAmongIds.size
            for (debtorId in expense.splitAmongIds) {
                if (debtorId == expense.paidById) continue
                if (debtorId == myId) {
                    netDebt[expense.paidById] = (netDebt[expense.paidById] ?: 0.0) + share
                } else if (expense.paidById == myId) {
                    netDebt[debtorId] = (netDebt[debtorId] ?: 0.0) - share
                }
            }
        }

        for (settlement in settlements) {
            if (settlement.toId == myId && settlement.fromId != myId) {
                netDebt[settlement.fromId] = (netDebt[settlement.fromId] ?: 0.0) + settlement.amount
            }
        }

        val splits = mutableListOf<Split>()
        for ((personId, amount) in netDebt) {
            if (personId.isBlank() || amount <= PAYMENT_TOLERANCE) continue
            val paidSoFar = settlements
                .filter { it.fromId == myId && it.toId == personId }
                .sumOf { it.amount }
            splits.add(Split(personId, amount, paidSoFar, paidSoFar >= amount - PAYMENT_TOLERANCE))
        }
        return splits
    }

    private suspend fun loadNames(splits: List<Split>): Map<String, String> {
        val personIds = splits.map { it.personId }.distinct()
        if (personIds.isEmpty()) return emptyMap()

        val users = crewRepository.getUsersByIds(personIds).getOrElse { return emptyMap() }
        return users.filter { it.name.isNotBlank() }.associate { it.id to it.name }
    }

    private fun sortByName(splits: List<Split>, names: Map<String, String>): List<Split> {
        val known = splits.filter { names[it.personId] != null }
            .sortedBy { names[it.personId].orEmpty().lowercase() }
        val unknown = splits.filter { names[it.personId] == null }
        return known + unknown
    }
}
