package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.CrewRepository
import com.example.plansync.data.SplitRepository
import com.example.plansync.model.Split
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

            splitRepository.getMySplits(planId)
                .onSuccess { splits ->
                    val names = loadNames(splits)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            splits = sortByName(splits, names),
                            names = names
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    fun onPaidChange(planId: String, split: Split, paid: Boolean) {
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveErrorMessage = null) }

            splitRepository.setSplitPaid(planId, split.id, paid)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            isSaving = false,
                            splits = state.splits.map { if (it.id == split.id) it.copy(paid = paid) else it }
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, saveErrorMessage = error.message) }
                }
        }
    }

    private suspend fun loadNames(splits: List<Split>): Map<String, String> {
        val creditorIds = splits.map { it.creditorId }.filter { it.isNotBlank() }.distinct()
        if (creditorIds.isEmpty()) return emptyMap()

        val users = crewRepository.getUsersByIds(creditorIds).getOrElse { return emptyMap() }
        return users.filter { it.name.isNotBlank() }.associate { it.id to it.name }
    }

    private fun sortByName(splits: List<Split>, names: Map<String, String>): List<Split> {
        val known = splits.filter { names[it.creditorId] != null }
            .sortedBy { names[it.creditorId].orEmpty().lowercase() }
        val unknown = splits.filter { names[it.creditorId] == null }
        return known + unknown
    }
}
