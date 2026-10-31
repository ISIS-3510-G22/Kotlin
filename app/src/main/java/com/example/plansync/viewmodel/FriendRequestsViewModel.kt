package com.example.plansync.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.CrewRepository
import com.example.plansync.data.FriendRequestQueueRepository
import com.example.plansync.data.FriendRequestSnapshotRepository
import com.example.plansync.model.FriendRequest
import com.example.plansync.model.PendingFriendAnswer
import com.example.plansync.service.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class FriendRequestsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CrewRepository()
    private val snapshotRepository = FriendRequestSnapshotRepository()
    private val queueRepository = FriendRequestQueueRepository()
    private val networkMonitor = NetworkMonitor.get(application)

    data class UiState(
        val requests: List<FriendRequest> = emptyList(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val isSaving: Boolean = false,
        val actionError: String? = null,
        val queueError: String? = null,
        val savedError: String? = null,
        val isOnline: Boolean = true
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            var wasOnline: Boolean? = null
            networkMonitor.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
                if (wasOnline == false && online) {
                    load()
                }
                wasOnline = online
            }
        }
    }

    fun load() {
        val online = networkMonitor.isCurrentlyOnline()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, queueError = null, savedError = null) }

            if (!online) {
                snapshotRepository.getRequests()
                    .onSuccess { requests -> _uiState.update { it.copy(requests = requests, isLoading = false) } }
                    .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message, isLoading = false) } }
                return@launch
            }

            withTimeoutOrNull(8_000) { queueRepository.flush(repository) }
                ?.onFailure { error -> _uiState.update { it.copy(queueError = error.message) } }
            val queuedIds = queueRepository.getAll().getOrNull().orEmpty().map { it.requestId }

            repository.getFriendRequests()
                .onSuccess { requests ->
                    val shown = requests.filter { it.id !in queuedIds }
                    _uiState.update { it.copy(requests = shown, isLoading = false) }
                    saveRequests(shown)
                }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message, isLoading = false) } }
        }
    }

    fun accept(request: FriendRequest) = answer(request, "accepted")

    fun deny(request: FriendRequest) = answer(request, "denied")

    private fun answer(request: FriendRequest, status: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, actionError = null) }

            if (!networkMonitor.isCurrentlyOnline()) {
                queueRepository.add(PendingFriendAnswer(request.id, status))
                    .onSuccess { removeRequest(request.id) }
                    .onFailure { error -> _uiState.update { it.copy(actionError = error.message) } }
            } else {
                val result = withTimeoutOrNull(8_000) {
                    if (status == "accepted") {
                        repository.acceptFriendRequest(request.id)
                    } else {
                        repository.denyFriendRequest(request.id)
                    }
                }
                if (result == null) {
                    queueRepository.add(PendingFriendAnswer(request.id, status))
                        .onSuccess { removeRequest(request.id) }
                        .onFailure { error -> _uiState.update { it.copy(actionError = error.message) } }
                } else {
                    result
                        .onSuccess {
                            queueRepository.remove(request.id)
                            removeRequest(request.id)
                        }
                        .onFailure { error -> _uiState.update { it.copy(actionError = error.message) } }
                }
            }

            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private suspend fun removeRequest(requestId: String) {
        _uiState.update { state -> state.copy(requests = state.requests.filter { it.id != requestId }) }
        saveRequests(_uiState.value.requests)
    }

    private suspend fun saveRequests(requests: List<FriendRequest>) {
        snapshotRepository.saveRequests(requests)
            .onFailure { error -> _uiState.update { it.copy(savedError = error.message) } }
    }
}
