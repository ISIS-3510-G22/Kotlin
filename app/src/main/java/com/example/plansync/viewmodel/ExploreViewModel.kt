package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.ExploreRepository
import com.example.plansync.model.Plan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExploreViewModel(
    private val repository: ExploreRepository = ExploreRepository()
) : ViewModel() {

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
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun loadPlans() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            repository.getPlans()
                .onSuccess { plans ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            plans = plans,
                            categoryOptions = listOf("All") + plans.flatMap { plan -> plan.tags }.distinct().sorted(),
                            filteredPlans = filterPlans(plans, it)
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
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
