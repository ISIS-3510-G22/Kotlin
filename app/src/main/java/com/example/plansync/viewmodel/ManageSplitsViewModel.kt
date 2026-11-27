package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.CrewRepository
import com.example.plansync.data.PlanRepository
import com.example.plansync.data.SplitRepository
import com.example.plansync.model.PendingAction
import com.example.plansync.model.Split
import com.example.plansync.service.OfflineSyncService
import com.example.plansync.service.SplitCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ManageSplitsViewModel(
    private val splitRepository: SplitRepository = SplitRepository(),
    private val crewRepository: CrewRepository = CrewRepository(),
    private val planRepository: PlanRepository = PlanRepository()
) : ViewModel() {

    enum class Tab { I_OWE, OWED_TO_ME }

    data class UiState(
        val splits: List<Split> = emptyList(),
        val owedToMe: List<Split> = emptyList(),
        val selectedTab: Tab = Tab.I_OWE,
        val daysOverdue: Int? = null,
        val names: Map<String, String> = emptyMap(),
        val isLoading: Boolean = false,
        val isOnline: Boolean = true,
        val errorMessage: String? = null,
        val isSaving: Boolean = false,
        val saveErrorMessage: String? = null
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

            val myId = splitRepository.currentUserId
            if (myId == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "No active session.") }
                return@launch
            }

            val expenses = OfflineSyncService.loadExpenses(planId).getOrElse { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = loadErrorMessage(error)) }
                return@launch
            }
            val settlements = OfflineSyncService.loadSettlements(planId).getOrElse { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = loadErrorMessage(error)) }
                return@launch
            }

            val splits = SplitCalculator.buildSplits(myId, expenses, settlements)
            val owedToMe = SplitCalculator.buildOwedToMe(myId, expenses, settlements)
            val names = loadNames(splits + owedToMe)
            val daysOverdue = SplitCalculator.daysOverdue(planRepository.getPlanDate(planId).getOrNull())
            _uiState.update {
                it.copy(
                    isLoading = false,
                    splits = sortByName(splits, names),
                    owedToMe = sortByName(owedToMe, names),
                    names = names,
                    daysOverdue = daysOverdue
                )
            }
        }
    }

    fun onTabSelected(tab: Tab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onPaidChange(planId: String, split: Split, paid: Boolean) {
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveErrorMessage = null) }

            val myId = splitRepository.currentUserId.orEmpty()
            val result = if (!OfflineSyncService.isOnlineNow()) {
                val action = if (paid) {
                    PendingAction.AddSettlement(planId, myId, split.personId, split.amount - split.paidSoFar)
                } else {
                    PendingAction.DeleteMySettlementsTo(planId, myId, split.personId)
                }
                OfflineSyncService.enqueue(action)
                Result.success(Unit)
            } else if (paid) {
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

    private fun loadErrorMessage(error: Throwable): String? =
        if (error.message == SplitRepository.CONNECT_TO_LOAD) "Connect to the internet to load this plan's splits." else error.message

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
