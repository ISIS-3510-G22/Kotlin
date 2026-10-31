package com.example.plansync.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.ExploreRepository
import com.example.plansync.model.Plan
import com.example.plansync.service.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExploreViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ExploreRepository()
    private val networkMonitor = NetworkMonitor.get(application)

    data class UiState(
        val searchQuery: String = "",
        val categoryOptions: List<String> = listOf("All"),
        val planTypeOptions: List<String> = listOf("All", "Solo", "Couple", "Group", "Family"),
        val priceOptions: List<String> = listOf("All", "Free", "$", "$$", "$$$"),
        val selectedCategory: String = "All",
        val selectedPlanType: String = "All",
        val selectedPrice: String = "All",
        val plans: List<Plan> = emptyList(),
        val filteredPlans: List<Plan> = emptyList(),
        val isLoading: Boolean = false,
        val isOnline: Boolean = true,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState(isOnline = networkMonitor.isCurrentlyOnline()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                val reconnected = online && !_uiState.value.isOnline
                _uiState.update { it.copy(isOnline = online) }
                if (reconnected) loadPlans()
            }
        }
    }

    fun loadPlans() {
        viewModelScope.launch {
            val cached = repository.getCachedPlans()
            _uiState.update { it.copy(isLoading = cached.isEmpty(), errorMessage = null) }
            if (cached.isNotEmpty()) showPlans(cached)

            if (_uiState.value.isOnline) {
                repository.getPlans()
                    .onSuccess { plans ->
                        repository.cachePlans(plans)
                        showPlans(plans)
                    }
                    .onFailure { error ->
                        if (cached.isEmpty()) _uiState.update { it.copy(errorMessage = error.message) }
                    }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun showPlans(plans: List<Plan>) {
        _uiState.update {
            it.copy(
                plans = plans,
                categoryOptions = listOf("All") + plans.flatMap { plan -> plan.tags }.filter { tag -> tag.isNotBlank() }.distinct().sorted(),
                filteredPlans = filterPlans(plans, it)
            )
        }
    }

    fun onSearchQueryChange(query: String) = applyFilters { it.copy(searchQuery = query) }

    fun onCategorySelect(category: String) = applyFilters { it.copy(selectedCategory = category) }

    fun onPlanTypeSelect(planType: String) = applyFilters { it.copy(selectedPlanType = planType) }

    fun onPriceSelect(price: String) = applyFilters { it.copy(selectedPrice = price) }


    private fun applyFilters(update: (UiState) -> UiState) {
        _uiState.update { current ->
            val next = update(current)
            next.copy(filteredPlans = filterPlans(next.plans, next))
        }
    }

    private fun filterPlans(plans: List<Plan>, state: UiState): List<Plan> =
        plans.filter { plan ->
            (state.searchQuery.isBlank() || plan.title.contains(state.searchQuery, ignoreCase = true)) &&
                (state.selectedCategory == "All" || state.selectedCategory in plan.tags) &&
                (state.selectedPlanType == "All" || plan.planType == state.selectedPlanType) &&
                (state.selectedPrice == "All" || plan.priceTier == state.selectedPrice)
        }
}
