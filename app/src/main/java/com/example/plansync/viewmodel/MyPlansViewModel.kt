package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.PlanRepository
import com.example.plansync.data.ReviewRepository
import com.example.plansync.data.WeatherRepository
import com.example.plansync.model.Plan
import com.example.plansync.model.PlanStatus
import com.example.plansync.model.RsvpStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyPlansViewModel(
    private val repository: PlanRepository = PlanRepository(),
    private val weatherRepository: WeatherRepository = WeatherRepository(),
    private val reviewRepository: ReviewRepository = ReviewRepository()
) : ViewModel() {

    data class UiState(
        val allPlans: List<Plan> = emptyList(),
        val selectedTab: PlanStatus = PlanStatus.CONFIRMED,
        val filteredPlans: List<Plan> = emptyList(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val invitesError: String? = null,
        val respondError: String? = null,
        val selectedInvite: Plan? = null,
        val rainForecast: Map<String, Int> = emptyMap(),
        val bestTag: String? = null,
        val bestAverage: Double = 0.0,
        val bestReviewCount: Int = 0
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var initialTabChosen = false

    fun loadPlans() {
        loadRainForecast()
        loadBestRatedTag()
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null, invitesError = null, respondError = null) }
            }

            val result = repository.getMyPlans()
            withContext(Dispatchers.Main) {
                result
                    .onSuccess { plans -> _uiState.update { it.copy(allPlans = plans) } }
                    .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }
            }

            if (!initialTabChosen) {
                initialTabChosen = true
                if (_uiState.value.allPlans.any { it.status == PlanStatus.PENDING_INVITE }) {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(selectedTab = PlanStatus.PENDING_INVITE) }
                    }
                }
            }

            val current = _uiState.value
            val filtered = withContext(Dispatchers.Default) {
                plansForTab(current.allPlans, current.selectedTab)
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isLoading = false, filteredPlans = filtered) }
            }
        }
    }

    private fun loadRainForecast() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = weatherRepository.getRainForecast()
            withContext(Dispatchers.Main) {
                result.onSuccess { forecast -> _uiState.update { it.copy(rainForecast = forecast) } }
            }
        }
    }

    private fun loadBestRatedTag() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = reviewRepository.getRatingsByTag()
            val ratingsByTag = result.getOrNull() ?: return@launch

            var bestTag: String? = null
            var bestAverage = 0.0
            var bestCount = 0
            withContext(Dispatchers.Default) {
                for ((tag, ratings) in ratingsByTag) {
                    val average = ratings.average()
                    if (bestTag == null || average > bestAverage || (average == bestAverage && ratings.size > bestCount)) {
                        bestTag = tag
                        bestAverage = average
                        bestCount = ratings.size
                    }
                }
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(bestTag = bestTag, bestAverage = bestAverage, bestReviewCount = bestCount) }
            }
        }
    }

    fun selectTab(tab: PlanStatus) {
        initialTabChosen = true
        _uiState.update { current ->
            current.copy(
                selectedTab = tab,
                respondError = null,
                filteredPlans = plansForTab(current.allPlans, tab)
            )
        }
    }

    fun onInviteTapped(plan: Plan) {
        _uiState.update { it.copy(selectedInvite = plan) }
    }

    fun onInviteDismissed() {
        _uiState.update { it.copy(selectedInvite = null) }
    }

    fun onInviteGoing() {
        respondToInvite(RsvpStatus.GOING)
    }

    fun onInviteCantMake() {
        respondToInvite(RsvpStatus.NOT_GOING)
    }

    private fun respondToInvite(status: RsvpStatus) {
        val invite = _uiState.value.selectedInvite
        if (invite == null) {
            return
        }
        _uiState.update { it.copy(selectedInvite = null) }

        viewModelScope.launch {
            repository.setRsvp(invite.id, status)
                .onSuccess { loadPlans() }
                .onFailure { error -> _uiState.update { it.copy(respondError = error.message) } }
        }
    }

    private fun plansForTab(plans: List<Plan>, tab: PlanStatus): List<Plan> {
        val result = mutableListOf<Plan>()
        for (plan in plans) {
            if (plan.status == tab) {
                result.add(plan)
            }
        }

        val withDate = result.filter { it.dateTime != null }
        val withoutDate = result.filter { it.dateTime == null }
        val sorted = if (tab == PlanStatus.PAST) {
            withDate.sortedByDescending { it.dateTime }
        } else {
            withDate.sortedBy { it.dateTime }
        }
        return sorted + withoutDate
    }
}
