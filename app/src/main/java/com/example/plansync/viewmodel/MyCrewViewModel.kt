package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.CrewRepository
import com.example.plansync.model.Friend
import com.example.plansync.model.Group
import com.example.plansync.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CrewTab { GROUPS, FRIENDS }

class MyCrewViewModel(
    private val repository: CrewRepository = CrewRepository()
) : ViewModel() {

    data class UiState(
        val groups: List<Group> = emptyList(),
        val friends: List<Friend> = emptyList(),
        val groupMembers: Map<String, User> = emptyMap(),
        val memberIdsByGroup: Map<String, List<String>> = emptyMap(),
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
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isLoading = true, groupsError = null, friendsError = null) }
            }

            coroutineScope {
                val groupsJob = async(Dispatchers.IO) { repository.getGroups() }
                val friendsJob = async(Dispatchers.IO) { repository.getFriends() }
                val groupsResult = groupsJob.await()
                val friendsResult = friendsJob.await()

                withContext(Dispatchers.Main) {
                    groupsResult
                        .onSuccess { groups -> _uiState.update { it.copy(groups = groups) } }
                        .onFailure { error -> _uiState.update { it.copy(groupsError = error.message) } }
                    friendsResult
                        .onSuccess { friends -> _uiState.update { it.copy(friends = friends) } }
                        .onFailure { error -> _uiState.update { it.copy(friendsError = error.message) } }
                    _uiState.update { it.copy(isLoading = false) }
                }
            }

            launch { loadGroupMembers() }
            launch { loadFriendPhotos() }
            launch { loadFriendRequestsCount() }
            launch { loadGroupInvitesCount() }
        }
    }

    private suspend fun loadGroupInvitesCount() {
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(groupInvitesCountError = null) }
        }

        val result = repository.getGroupInvites()
        withContext(Dispatchers.Main) {
            result
                .onSuccess { invites -> _uiState.update { it.copy(groupInvitesCount = invites.size) } }
                .onFailure { error -> _uiState.update { it.copy(groupInvitesCount = 0, groupInvitesCountError = error.message) } }
        }
    }

    private suspend fun loadFriendRequestsCount() {
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(friendRequestsCountError = null) }
        }

        val result = repository.getFriendRequests()
        withContext(Dispatchers.Main) {
            result
                .onSuccess { requests -> _uiState.update { it.copy(friendRequestsCount = requests.size) } }
                .onFailure { error -> _uiState.update { it.copy(friendRequestsCount = 0, friendRequestsCountError = error.message) } }
        }
    }

    private suspend fun loadGroupMembers() {
        val groupIds = _uiState.value.groups.map { it.id }
        if (groupIds.isEmpty()) return

        val memberIdsByGroup = repository.getMemberIds(groupIds).getOrElse { return }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(memberIdsByGroup = memberIdsByGroup) }
        }

        val memberIds = withContext(Dispatchers.Default) {
            memberIdsByGroup.values.flatMap { it.take(3) }.distinct()
        }
        if (memberIds.isEmpty()) return

        val users = repository.getUsersByIds(memberIds).getOrElse { return }
        val usersById = withContext(Dispatchers.Default) { users.associateBy { it.id } }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(groupMembers = usersById) }
        }
    }

    private suspend fun loadFriendPhotos() {
        val friendIds = _uiState.value.friends.map { it.id }
        if (friendIds.isEmpty()) return

        val photos = repository.getPhotoUrls(friendIds).getOrElse { return }
        withContext(Dispatchers.Main) {
            _uiState.update { state ->
                state.copy(friends = state.friends.map { it.copy(photoUrl = photos[it.id].orEmpty()) })
            }
        }
    }

    fun selectTab(tab: CrewTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }
}
