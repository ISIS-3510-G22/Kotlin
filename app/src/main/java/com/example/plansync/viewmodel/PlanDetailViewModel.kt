package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.PlanRepository
import com.example.plansync.model.Plan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel layer — MVVM architecture.
 *
 * Owns and manages the UI state for the Plan Detail screen. Survives
 * configuration changes (e.g. screen rotation).
 *
 * Observer pattern: exposes [uiState] as a [StateFlow] so the UI layer
 * observes changes reactively via collectAsStateWithLifecycle().
 *
 * The Composable calls [loadPlan] once on first composition and never
 * touches [PlanRepository] directly.
 */
class PlanDetailViewModel(
    private val repository: PlanRepository = PlanRepository()
) : ViewModel() {

    /**
     * Immutable snapshot of everything PlanDetailScreen needs to render itself.
     */
    data class UiState(
        val plan: Plan? = null,
        val isLoading: Boolean = false,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Fetches plan data from the repository for the given [planId].
     * Sets [UiState.isLoading] while the call is in-flight, then populates
     * [UiState.plan] on success or [UiState.errorMessage] on failure.
     *
     * Safe to call multiple times — subsequent calls replace previous state.
     * Coroutine is scoped to [viewModelScope] to prevent leaks.
     */
    fun loadPlan(planId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            repository.getPlan(planId)
                .onSuccess { plan ->
                    _uiState.update { it.copy(isLoading = false, plan = plan) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }
}
