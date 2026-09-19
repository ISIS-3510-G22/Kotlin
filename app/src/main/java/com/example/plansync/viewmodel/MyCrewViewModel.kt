package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.CrewRepository
import com.example.plansync.model.Friend
import com.example.plansync.model.Group
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CrewTab { GROUPS, FRIENDS }

class MyCrewViewModel(
    private val repository: CrewRepository = CrewRepository()
) : ViewModel() {

    data class UiState(
        val groups: List<Group> = emptyList(),
        val friends: List<Friend> = emptyList(),
        val selectedTab: CrewTab = CrewTab.GROUPS,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val maxGridColumns: Int = 2
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun loadCrew() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            repository.getGroups()
                .onSuccess { groups -> _uiState.update { it.copy(groups = groups) } }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }

            repository.getFriends()
                .onSuccess { friends -> _uiState.update { it.copy(friends = friends) } }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun selectTab(tab: CrewTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }
}
