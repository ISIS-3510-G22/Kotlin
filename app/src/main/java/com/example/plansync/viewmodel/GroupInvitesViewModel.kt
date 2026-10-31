package com.example.plansync.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.CrewRepository
import com.example.plansync.model.Group
import com.example.plansync.model.User
import com.example.plansync.service.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GroupInvitesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CrewRepository()
    private val networkMonitor = NetworkMonitor.get(application)

    data class UiState(
        val invites: List<Group> = emptyList(),
        val groupMembers: Map<String, User> = emptyMap(),
        val memberIdsByGroup: Map<String, List<String>> = emptyMap(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val isSaving: Boolean = false,
        val actionError: String? = null,
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
            if (!online) {
                _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                return@launch
            }

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
        if (!networkMonitor.isCurrentlyOnline()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, actionError = null) }

            repository.joinGroup(groupId)
                .onSuccess { removeInvite(groupId) }
                .onFailure { error -> _uiState.update { it.copy(actionError = error.message) } }

            _uiState.update { it.copy(isSaving = false) }
        }
    }

    fun deny(groupId: String) {
        if (!networkMonitor.isCurrentlyOnline()) return
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
