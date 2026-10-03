package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.CrewRepository
import com.example.plansync.model.Friend
import com.example.plansync.model.Group
import com.example.plansync.model.User
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
        val groupMembers: Map<String, User> = emptyMap(),
        val selectedTab: CrewTab = CrewTab.GROUPS,
        val isLoading: Boolean = false,
        val groupsError: String? = null,
        val friendsError: String? = null,
        val friendRequestsCount: Int = 0,
        val friendRequestsCountError: String? = null,
        val groupInvitesCount: Int = 0,
        val groupInvitesCountError: String? = null,
        val maxGridColumns: Int = 2
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun loadCrew() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, groupsError = null, friendsError = null) }

            repository.getGroups()
                .onSuccess { groups -> _uiState.update { it.copy(groups = groups) } }
                .onFailure { error -> _uiState.update { it.copy(groupsError = error.message) } }

            repository.getFriends()
                .onSuccess { friends -> _uiState.update { it.copy(friends = friends) } }
                .onFailure { error -> _uiState.update { it.copy(friendsError = error.message) } }

            _uiState.update { it.copy(isLoading = false) }

            loadGroupMembers()
            loadFriendPhotos()
            loadFriendRequestsCount()
            loadGroupInvitesCount()
        }
    }

    private suspend fun loadGroupInvitesCount() {
        _uiState.update { it.copy(groupInvitesCountError = null) }

        repository.getGroupInvites()
            .onSuccess { invites -> _uiState.update { it.copy(groupInvitesCount = invites.size) } }
            .onFailure { error -> _uiState.update { it.copy(groupInvitesCount = 0, groupInvitesCountError = error.message) } }
    }

    private suspend fun loadFriendRequestsCount() {
        _uiState.update { it.copy(friendRequestsCountError = null) }

        repository.getFriendRequests()
            .onSuccess { requests -> _uiState.update { it.copy(friendRequestsCount = requests.size) } }
            .onFailure { error -> _uiState.update { it.copy(friendRequestsCount = 0, friendRequestsCountError = error.message) } }
    }

    private suspend fun loadGroupMembers() {
        val memberIds = _uiState.value.groups.flatMap { it.memberIds.take(3) }.distinct()
        if (memberIds.isEmpty()) return

        repository.getUsersByIds(memberIds)
            .onSuccess { users -> _uiState.update { it.copy(groupMembers = users.associateBy { user -> user.id }) } }
    }

    private suspend fun loadFriendPhotos() {
        val friendIds = _uiState.value.friends.map { it.id }
        if (friendIds.isEmpty()) return

        repository.getPhotoUrls(friendIds)
            .onSuccess { photos ->
                _uiState.update { state ->
                    state.copy(friends = state.friends.map { it.copy(photoUrl = photos[it.id].orEmpty()) })
                }
            }
    }

    fun selectTab(tab: CrewTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }
}
