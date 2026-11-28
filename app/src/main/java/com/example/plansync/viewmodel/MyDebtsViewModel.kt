package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.CrewRepository
import com.example.plansync.data.PlanRepository
import com.example.plansync.data.PlanSnapshotRepository
import com.example.plansync.data.SplitRepository
import com.example.plansync.model.OverdueDebt
import com.example.plansync.model.PendingAction
import com.example.plansync.service.OfflineSyncService
import com.example.plansync.service.SplitCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyDebtsViewModel(
    private val planRepository: PlanRepository = PlanRepository(),
    private val splitRepository: SplitRepository = SplitRepository(),
    private val crewRepository: CrewRepository = CrewRepository(),
    private val planSnapshotRepository: PlanSnapshotRepository = PlanSnapshotRepository()
) : ViewModel() {

    data class UiState(
        val debts: List<OverdueDebt> = emptyList(),
        val isLoading: Boolean = false,
        val payingId: String? = null,
        val isOnline: Boolean = true,
        val errorMessage: String? = null
    ) {
        val totalOwed: Double get() = debts.sumOf { it.amount }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            OfflineSyncService.isOnline.collect { online ->
                val cameBackOnline = online && !_uiState.value.isOnline
                _uiState.update { it.copy(isOnline = online) }
                if (cameBackOnline) {
                    OfflineSyncService.syncNow()
                    load()
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            val myId = splitRepository.currentUserId ?: return@launch
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val fromServer = if (OfflineSyncService.isOnlineNow()) planRepository.getMyPlans() else null
            val plans = fromServer?.getOrNull()
                ?: planSnapshotRepository.getPlans().getOrNull()
                ?: run {
                    val message = fromServer?.exceptionOrNull()?.message ?: "Connect to the internet to load your debts."
                    _uiState.update { it.copy(isLoading = false, errorMessage = message) }
                    return@launch
                }

            val planSplits = withContext(Dispatchers.IO) {
                plans.filter { SplitCalculator.daysOverdue(it.dateTime) != null }.map { plan ->
                    async {
                        val expenses = async { OfflineSyncService.loadExpenses(plan.id).getOrNull() }
                        val settlements = async { OfflineSyncService.loadSettlements(plan.id).getOrNull() }
                        val e = expenses.await() ?: return@async null
                        val s = settlements.await() ?: return@async null
                        plan to SplitCalculator.buildSplits(myId, e, s)
                    }
                }.awaitAll().filterNotNull()
            }

            val creditorIds = planSplits.flatMap { (_, splits) -> splits.map { it.personId } }
            val names = crewRepository.getUsersByIds(creditorIds, OfflineSyncService.readSource())
                .getOrDefault(emptyList())
                .associate { it.id to it.fullName }

            val now = System.currentTimeMillis()
            val debts = withContext(Dispatchers.Default) {
                planSplits.flatMap { (plan, splits) ->
                    val days = SplitCalculator.daysOverdue(plan.dateTime, now) ?: return@flatMap emptyList()
                    splits.filter { !it.paid }.map { split ->
                        OverdueDebt(
                            id = "${plan.id}_${split.personId}",
                            planId = plan.id,
                            planName = plan.title,
                            amount = split.amount - split.paidSoFar,
                            daysOverdue = days,
                            fromId = myId,
                            toId = split.personId,
                            toName = names[split.personId].orEmpty(),
                            updatedAt = now
                        )
                    }
                }.sortedByDescending { it.daysOverdue }
            }

            _uiState.update { it.copy(isLoading = false, debts = debts) }
        }
    }

    fun markAsPaid(debt: OverdueDebt) {
        if (_uiState.value.payingId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(payingId = debt.id, errorMessage = null) }
            val result = if (OfflineSyncService.isOnlineNow()) {
                splitRepository.addSettlement(debt.planId, debt.toId, debt.amount)
            } else {
                OfflineSyncService.enqueue(PendingAction.AddSettlement(debt.planId, debt.fromId, debt.toId, debt.amount))
                Result.success(Unit)
            }
            result
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(payingId = null, debts = state.debts.filter { it.id != debt.id })
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(payingId = null, errorMessage = error.message) } }
        }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

}
