package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.CrewRepository
import com.example.plansync.model.Group
import com.example.plansync.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GroupInvitesViewModel(
    private val repository: CrewRepository = CrewRepository()
) : ViewModel() {

    data class UiState(
        val invites: List<Group> = emptyList(),
        val groupMembers: Map<String, User> = emptyMap(),
        val memberIdsByGroup: Map<String, List<String>> = emptyMap(),
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

            repository.getGroupInvites()
                .onSuccess { invites -> _uiState.update { it.copy(invites = invites, isLoading = false) } }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message, isLoading = false) } }

            loadGroupMembers()
        }
    }

    private suspend fun loadGroupMembers() {
        val groupIds = _uiState.value.invites.map { it.id }
        if (groupIds.isEmpty()) return

        val memberIdsByGroup = repository.getMemberIds(groupIds).getOrElse { return }
        _uiState.update { it.copy(memberIdsByGroup = memberIdsByGroup) }

        val memberIds = memberIdsByGroup.values.flatMap { it.take(3) }.distinct()
        if (memberIds.isEmpty()) return

        repository.getUsersByIds(memberIds)
            .onSuccess { users -> _uiState.update { it.copy(groupMembers = users.associateBy { user -> user.id }) } }
    }

    fun join(groupId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, actionError = null) }

            repository.joinGroup(groupId)
                .onSuccess { removeInvite(groupId) }
                .onFailure { error -> _uiState.update { it.copy(actionError = error.message) } }

            _uiState.update { it.copy(isSaving = false) }
        }
    }

    fun deny(groupId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, actionError = null) }

            repository.denyGroupInvite(groupId)
                .onSuccess { removeInvite(groupId) }
                .onFailure { error -> _uiState.update { it.copy(actionError = error.message) } }

            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private fun removeInvite(groupId: String) {
        _uiState.update { state -> state.copy(invites = state.invites.filter { it.id != groupId }) }
    }
}
