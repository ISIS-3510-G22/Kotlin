package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.CrewRepository
import com.example.plansync.model.User
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AddFriendViewModel(
    private val repository: CrewRepository = CrewRepository()
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val results: List<User> = emptyList(),
        val requestedIds: Set<String> = emptySet(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val requestError: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
        searchJob?.cancel()

        val cleanQuery = value.replace(" ", "").replace("@", "")
        if (cleanQuery.isEmpty()) {
            _uiState.update { it.copy(results = emptyList(), isLoading = false, errorMessage = null) }
            return
        }

        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            repository.searchUsers(cleanQuery)
                .onSuccess { users -> _uiState.update { it.copy(results = users, isLoading = false) } }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message, isLoading = false) } }
        }
    }

    fun sendRequest(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(requestError = null) }

            repository.sendFriendRequest(userId)
                .onSuccess { _uiState.update { it.copy(requestedIds = it.requestedIds + userId) } }
                .onFailure { error -> _uiState.update { it.copy(requestError = error.message) } }
        }
    }
}
