package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.CrewRepository
import com.example.plansync.model.FriendRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FriendRequestsViewModel(
    private val repository: CrewRepository = CrewRepository()
) : ViewModel() {

    data class UiState(
        val requests: List<FriendRequest> = emptyList(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val isSaving: Boolean = false,
        val actionError: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            repository.getFriendRequests()
                .onSuccess { requests -> _uiState.update { it.copy(requests = requests, isLoading = false) } }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message, isLoading = false) } }

            loadPhotos()
        }
    }

    private suspend fun loadPhotos() {
        val requestIds = _uiState.value.requests.map { it.id }
        if (requestIds.isEmpty()) return

        repository.getPhotoUrls(requestIds)
            .onSuccess { photos ->
                _uiState.update { state ->
                    state.copy(requests = state.requests.map { it.copy(photoUrl = photos[it.id].orEmpty()) })
                }
            }
    }

    fun accept(request: FriendRequest) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, actionError = null) }

            repository.acceptFriendRequest(request)
                .onSuccess { removeRequest(request.id) }
                .onFailure { error -> _uiState.update { it.copy(actionError = error.message) } }

            _uiState.update { it.copy(isSaving = false) }
        }
    }

    fun deny(request: FriendRequest) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, actionError = null) }

            repository.denyFriendRequest(request.id)
                .onSuccess { removeRequest(request.id) }
                .onFailure { error -> _uiState.update { it.copy(actionError = error.message) } }

            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private fun removeRequest(requestId: String) {
        _uiState.update { state -> state.copy(requests = state.requests.filter { it.id != requestId }) }
    }
}
