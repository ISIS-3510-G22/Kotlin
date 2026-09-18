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
        val categoryOptions: List<String> = listOf("All", "Weekend Getaways", "Food & Drink", "Outdoors", "Culture"),
        val planTypeOptions: List<String> = listOf("Solo", "Couple", "Group", "Family"),
        val priceOptions: List<String> = listOf("Free", "$", "$$", "$$$"),
        val ratingOptions: List<String> = listOf("4+", "3+", "All"),
        val selectedCategory: String = "All",
        val selectedPlanType: String = "Group",
        val selectedPrice: String = "$$",
        val selectedRating: String = "All",
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
                    _uiState.update { it.copy(isLoading = false, plans = plans, filteredPlans = filterPlans(plans, it)) }
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

    fun onRatingSelect(rating: String) = applyFilters { it.copy(selectedRating = rating) }

    private fun applyFilters(update: (UiState) -> UiState) {
        _uiState.update { current ->
            val next = update(current)
            next.copy(filteredPlans = filterPlans(next.plans, next))
        }
    }

    private fun filterPlans(plans: List<Plan>, state: UiState): List<Plan> {
        val minRating = when (state.selectedRating) {
            "4+" -> 4.0
            "3+" -> 3.0
            else -> 0.0
        }

        return plans.filter { plan ->
            (state.searchQuery.isBlank() || plan.title.contains(state.searchQuery, ignoreCase = true)) &&
                (state.selectedCategory == "All" || plan.category == state.selectedCategory) &&
                plan.planType == state.selectedPlanType &&
                plan.priceTier == state.selectedPrice &&
                plan.rating >= minRating
        }
    }
}
