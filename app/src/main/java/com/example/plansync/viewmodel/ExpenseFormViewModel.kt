package com.example.plansync.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.AnalyticsRepository
import com.example.plansync.data.CrewRepository
import com.example.plansync.data.PlanDatabase
import com.example.plansync.data.PlanRepository
import com.example.plansync.data.SplitRepository
import com.example.plansync.model.PendingAction
import com.example.plansync.service.OfflineSyncService
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExpenseFormViewModel(application: Application) : AndroidViewModel(application) {

    private val splitRepository = SplitRepository()
    private val planRepository = PlanRepository(planDao = PlanDatabase.get(application).planDao())
    private val crewRepository = CrewRepository()
    private val analyticsRepository = AnalyticsRepository()

    data class Person(val id: String, val name: String)

    data class UiState(
        val name: String = "",
        val value: String = "",
        val paidById: String? = null,
        val people: List<Person> = emptyList(),
        val isEditing: Boolean = false,
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        val isDone: Boolean = false,
        val isOnline: Boolean = true,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var planId = ""
    private var expenseId: String? = null
    private var splitAmongIds: List<String> = emptyList()
    private var loaded = false

    init {
        viewModelScope.launch {
            OfflineSyncService.isOnline.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
    }

    fun load(planId: String, expenseId: String?) {
        if (loaded) return
        loaded = true
        this.planId = planId
        this.expenseId = expenseId

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isEditing = expenseId != null, errorMessage = null) }

            val plan = planRepository.getPlan(planId).getOrElse {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Connect to the internet to load this plan.") }
                return@launch
            }
            val expense = expenseId?.let { id ->
                OfflineSyncService.loadExpenses(planId).getOrNull()?.find { it.id == id }
                    ?: run {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "This expense no longer exists.") }
                        return@launch
                    }
            }

            val participantIds = plan.participants.map { it.id }
            splitAmongIds = expense?.splitAmongIds ?: participantIds
            val ids = (participantIds + splitAmongIds + listOfNotNull(expense?.paidById)).distinct()
            val people = crewRepository.getUsersByIds(ids)
                .getOrDefault(emptyList())
                .map { Person(it.id, it.fullName.ifBlank { it.email }) }
                .sortedBy { it.name.lowercase() }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    people = people,
                    name = expense?.name.orEmpty(),
                    value = expense?.value?.let(::formatValue).orEmpty(),
                    paidById = expense?.paidById ?: splitRepository.currentUserId?.takeIf { id -> id in ids }
                )
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, errorMessage = null) }
    }

    fun onValueChange(value: String) {
        _uiState.update { it.copy(value = value, errorMessage = null) }
    }

    fun onPaidByChange(personId: String) {
        _uiState.update { it.copy(paidById = personId, errorMessage = null) }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        val name = state.name.trim()
        val value = state.value.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 }
        val paidById = state.paidById
        if (name.isEmpty() || value == null || paidById == null) {
            _uiState.update { it.copy(errorMessage = "Fill in the name, a value greater than 0 and who paid.") }
            return
        }
        if (splitAmongIds.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "This plan has no participants to split with.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val id = expenseId

            if (!OfflineSyncService.isOnlineNow()) {
                val action = if (id == null) {
                    PendingAction.AddExpense(planId, UUID.randomUUID().toString(), name, value, paidById, splitAmongIds, System.currentTimeMillis())
                } else {
                    PendingAction.UpdateExpense(planId, id, name, value, paidById)
                }
                OfflineSyncService.enqueue(action)
                _uiState.update { it.copy(isSaving = false, isDone = true) }
                return@launch
            }

            val result = if (id == null) {
                splitRepository.addExpense(planId, UUID.randomUUID().toString(), name, value, paidById, splitAmongIds)
            } else {
                splitRepository.updateExpense(planId, id, name, value, paidById)
            }
            result
                .onSuccess {
                    if (id == null) analyticsRepository.logEvent(AnalyticsRepository.EXPENSE_ADDED, planId)
                    _uiState.update { it.copy(isSaving = false, isDone = true) }
                }
                .onFailure { error -> _uiState.update { it.copy(isSaving = false, errorMessage = error.message) } }
        }
    }

    fun delete() {
        val id = expenseId ?: return
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }

            if (!OfflineSyncService.isOnlineNow()) {
                OfflineSyncService.deleteExpenseOffline(planId, id)
                _uiState.update { it.copy(isSaving = false, isDone = true) }
                return@launch
            }

            splitRepository.deleteExpense(planId, id)
                .onSuccess { _uiState.update { it.copy(isSaving = false, isDone = true) } }
                .onFailure { error -> _uiState.update { it.copy(isSaving = false, errorMessage = error.message) } }
        }
    }

    private fun formatValue(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
