package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.CrewRepository
import com.example.plansync.model.Friend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InviteToGroupViewModel(
    private val repository: CrewRepository = CrewRepository()
) : ViewModel() {

    data class UiState(
        val friends: List<Friend> = emptyList(),
        val invitedIds: Set<String> = emptySet(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val inviteError: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun load(groupId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val group = repository.getGroup(groupId).getOrElse { error ->
                _uiState.update { it.copy(errorMessage = error.message, isLoading = false) }
                return@launch
            }

            repository.getFriends()
                .onSuccess { friends ->
                    val invitable = friends.filter { it.id !in group.memberIds && it.id !in group.invitedIds }
                    _uiState.update { it.copy(friends = invitable, isLoading = false) }
                }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message, isLoading = false) } }

            loadPhotos()
        }
    }

    private suspend fun loadPhotos() {
        val friendIds = _uiState.value.friends.map { it.id }
        if (friendIds.isEmpty()) return

        repository.getPhotoUrls(friendIds)
            .onSuccess { photos ->
                _uiState.update { state ->
                    state.copy(friends = state.friends.map { it.copy(photoUrl = photos[it.id].orEmpty()) })
                }
            }
    }

    fun invite(groupId: String, friendId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(inviteError = null) }

            repository.inviteToGroup(groupId, friendId)
                .onSuccess { _uiState.update { it.copy(invitedIds = it.invitedIds + friendId) } }
                .onFailure { error -> _uiState.update { it.copy(inviteError = error.message) } }
        }
    }
}
