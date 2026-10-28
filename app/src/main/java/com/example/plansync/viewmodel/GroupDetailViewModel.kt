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

class GroupDetailViewModel(
    private val repository: CrewRepository = CrewRepository()
) : ViewModel() {

    data class UiState(
        val group: Group? = null,
        val memberIds: List<String> = emptyList(),
        val members: Map<String, User> = emptyMap(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null
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

            val memberIds = repository.getMemberIds(listOf(groupId)).getOrElse { error ->
                _uiState.update { it.copy(errorMessage = error.message, isLoading = false) }
                return@launch
            }[groupId].orEmpty()

            _uiState.update { it.copy(group = group, memberIds = memberIds, isLoading = false) }
            if (memberIds.isEmpty()) return@launch

            repository.getUsersByIds(memberIds)
                .onSuccess { users -> _uiState.update { it.copy(members = users.associateBy { user -> user.id }) } }
        }
    }
}
