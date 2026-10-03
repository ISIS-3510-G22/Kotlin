package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.PlanRepository
import com.example.plansync.data.ReviewRepository
import com.example.plansync.model.PlanStatus
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LeaveReviewViewModel(
    private val planRepository: PlanRepository = PlanRepository(),
    private val reviewRepository: ReviewRepository = ReviewRepository()
) : ViewModel() {

    data class UiState(
        val planTitle: String = "",
        val planDate: Date? = null,
        val rating: Int = 0,
        val comment: String = "",
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        val didSave: Boolean = false,
        val errorMessage: String? = null,
        val saveErrorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun load(planId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            planRepository.getMyPlans()
                .onSuccess { plans ->
                    val plan = plans.find { it.id == planId }
                    if (plan == null || plan.status != PlanStatus.PAST) {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "This plan is not available for review.") }
                        return@launch
                    }

                    reviewRepository.getMyReview(planId)
                        .onSuccess { review ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    planTitle = plan.title,
                                    planDate = plan.dateTime,
                                    rating = review?.rating ?: 0,
                                    comment = review?.comment.orEmpty()
                                )
                            }
                        }
                        .onFailure { error ->
                            _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                        }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    fun onRatingChange(value: Int) = _uiState.update { it.copy(rating = value) }

    fun onCommentChange(value: String) = _uiState.update { it.copy(comment = value) }

    fun submitReview(planId: String) {
        val current = _uiState.value
        if (current.rating == 0) {
            _uiState.update { it.copy(saveErrorMessage = "Please select a rating.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveErrorMessage = null) }
            reviewRepository.saveReview(
                planId = planId,
                rating = current.rating,
                comment = current.comment.trim()
            )
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, didSave = true) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, saveErrorMessage = error.message) }
                }
        }
    }
}
