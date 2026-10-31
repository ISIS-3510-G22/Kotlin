package com.example.plansync.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.CrewDatabase
import com.example.plansync.data.CrewPreferencesRepository
import com.example.plansync.data.CrewRepository
import com.example.plansync.data.CrewSnapshotRepository
import com.example.plansync.data.FriendRequestQueueRepository
import com.example.plansync.model.CrewSnapshot
import com.example.plansync.model.Friend
import com.example.plansync.model.Group
import com.example.plansync.model.User
import com.example.plansync.service.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class CrewTab { GROUPS, FRIENDS }

private const val COUNT_TIMEOUT_MS = 2_000L

class MyCrewViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CrewRepository()
    private val snapshotRepository = CrewSnapshotRepository(CrewDatabase.get(application).crewDao())
    private val friendQueueRepository = FriendRequestQueueRepository()
    private val preferences = CrewPreferencesRepository(application)
    private val networkMonitor = NetworkMonitor.get(application)
    @Volatile private var tabChosenByUser = false

    data class UiState(
        val groups: List<Group> = emptyList(),
        val friends: List<Friend> = emptyList(),
        val groupMembers: Map<String, User> = emptyMap(),
        val memberIdsByGroup: Map<String, List<String>> = emptyMap(),
        val selectedTab: CrewTab = CrewTab.GROUPS,
        val isLoading: Boolean = false,
        val groupsError: String? = null,
        val friendsError: String? = null,
        val friendRequestsCount: Int? = null,
        val friendRequestsCountError: String? = null,
        val groupInvitesCount: Int? = null,
        val groupInvitesCountError: String? = null,
        val maxGridColumns: Int = 2,
        val isOnline: Boolean = true,
        val showingSavedCrew: Boolean = false,
        val savedCrewError: String? = null,
        val friendAnswersError: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { loadSavedTab() }
        viewModelScope.launch {
            var wasOnline: Boolean? = null
            networkMonitor.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
                if (wasOnline == false && online) {
                    sendQueuedFriendAnswers()
                    loadCrew()
                }
                wasOnline = online
            }
        }
    }

    private suspend fun loadSavedTab() {
        val result = withContext(Dispatchers.IO) { preferences.getTab() }
        result
            .onSuccess { saved ->
                val tab = when (saved) {
                    "groups" -> CrewTab.GROUPS
                    "friends" -> CrewTab.FRIENDS
                    else -> null
                }
                if (tab != null && !tabChosenByUser) {
                    _uiState.update { it.copy(selectedTab = tab) }
                }
            }
            .onFailure { error -> _uiState.update { it.copy(savedCrewError = error.message) } }
    }

    private suspend fun sendQueuedFriendAnswers() {
        _uiState.update { it.copy(friendAnswersError = null) }
        val result = withContext(Dispatchers.IO) {
            withTimeoutOrNull(8_000) { friendQueueRepository.flush(repository) }
        }
        result?.onFailure { error -> _uiState.update { it.copy(friendAnswersError = error.message) } }
    }

    fun loadCrew() {
        val online = networkMonitor.isCurrentlyOnline()
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        groupsError = null,
                        friendsError = null,
                        savedCrewError = null,
                        friendRequestsCountError = null,
                        groupInvitesCountError = null,
                        showingSavedCrew = false
                    )
                }
            }

            useSavedFriendRequestsCount(overwrite = !online)
            useSavedGroupInvitesCount()

            if (online) {
                loadOnline()
            } else {
                loadSaved()
            }
        }
    }

    private suspend fun loadSaved() {
        val snapshot = snapshotRepository.getSnapshot()
        withContext(Dispatchers.Main) {
            snapshot
                .onSuccess { saved ->
                    _uiState.update {
                        it.copy(
                            groups = saved.groups,
                            friends = saved.friends,
                            memberIdsByGroup = saved.memberIdsByGroup,
                            groupMembers = saved.groupMembers
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(groupsError = error.message, friendsError = error.message) } }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun useSavedFriendRequestsCount(overwrite: Boolean) {
        val saved = preferences.getFriendRequestsCount()
        val queued = friendQueueRepository.getAll()
        withContext(Dispatchers.Main) {
            saved
                .onSuccess { count ->
                    val adjusted = count?.let { (it - queued.getOrNull().orEmpty().size).coerceAtLeast(0) }
                    _uiState.update {
                        if (overwrite) {
                            it.copy(friendRequestsCount = adjusted ?: it.friendRequestsCount)
                        } else {
                            it.copy(friendRequestsCount = it.friendRequestsCount ?: adjusted)
                        }
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(friendRequestsCountError = error.message) } }
            queued.onFailure { error -> _uiState.update { it.copy(friendAnswersError = error.message) } }
        }
    }

    private suspend fun useSavedGroupInvitesCount() {
        val saved = preferences.getGroupInvitesCount()
        withContext(Dispatchers.Main) {
            saved
                .onSuccess { count -> _uiState.update { it.copy(groupInvitesCount = it.groupInvitesCount ?: count) } }
                .onFailure { error -> _uiState.update { it.copy(groupInvitesCountError = error.message) } }
        }
    }

    private suspend fun loadOnline() = coroutineScope {
        launch { loadFriendRequestsCount() }
        launch { loadGroupInvitesCount() }
        val groupsJob = async(Dispatchers.IO) {
            withTimeoutOrNull(8_000) { repository.getGroups() }
                ?: Result.failure(Exception("Loading groups timed out."))
        }
        val friendsJob = async(Dispatchers.IO) {
            withTimeoutOrNull(8_000) { repository.getFriends() }
                ?: Result.failure(Exception("Loading friends timed out."))
        }
        val groupsResult = groupsJob.await()
        val friendsResult = friendsJob.await()

        var saved: CrewSnapshot? = null
        if (groupsResult.isFailure || friendsResult.isFailure) {
            val found = snapshotRepository.getSnapshot().getOrNull()
            if (found != null && (found.groups.isNotEmpty() || found.friends.isNotEmpty())) {
                saved = found
            }
        }

        withContext(Dispatchers.Main) {
            if (saved != null) {
                _uiState.update {
                    it.copy(
                        groups = saved.groups,
                        friends = saved.friends,
                        memberIdsByGroup = saved.memberIdsByGroup,
                        groupMembers = saved.groupMembers,
                        showingSavedCrew = true
                    )
                }
            } else {
                groupsResult
                    .onSuccess { groups -> _uiState.update { it.copy(groups = groups) } }
                    .onFailure { error -> _uiState.update { it.copy(groupsError = error.message) } }
                friendsResult
                    .onSuccess { friends -> _uiState.update { it.copy(friends = friends) } }
                    .onFailure { error -> _uiState.update { it.copy(friendsError = error.message) } }
            }
            _uiState.update { it.copy(isLoading = false) }
        }

        if (saved == null) {
            groupsResult.onSuccess { groups -> saveGroups(groups) }
            friendsResult.onSuccess { friends -> saveFriends(friends) }
            launch { loadGroupMembers() }
            launch { loadFriendPhotos() }
        }
    }

    private suspend fun saveGroups(groups: List<Group>) {
        snapshotRepository.saveGroups(groups).onFailure { showSaveError(it) }
    }

    private suspend fun saveFriends(friends: List<Friend>) {
        snapshotRepository.saveFriends(friends).onFailure { showSaveError(it) }
    }

    private suspend fun showSaveError(error: Throwable) {
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(savedCrewError = error.message) }
        }
    }

    private suspend fun loadGroupInvitesCount() {
        val result = withTimeoutOrNull(COUNT_TIMEOUT_MS) { repository.getGroupInvites() }
        result?.onSuccess { invites ->
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(groupInvitesCount = invites.size) }
            }
            preferences.saveGroupInvitesCount(invites.size).onFailure { showSaveError(it) }
        }
    }

    private suspend fun loadFriendRequestsCount() {
        val result = withTimeoutOrNull(COUNT_TIMEOUT_MS) { repository.getFriendRequests() }
        result?.onSuccess { requests ->
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(friendRequestsCount = requests.size) }
            }
            preferences.saveFriendRequestsCount(requests.size).onFailure { showSaveError(it) }
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
        snapshotRepository.saveMembers(memberIdsByGroup, usersById).onFailure { showSaveError(it) }
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
        saveFriends(_uiState.value.friends)
    }

    fun selectTab(tab: CrewTab) {
        tabChosenByUser = true
        _uiState.update { it.copy(selectedTab = tab) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = preferences.saveTab(tab.name.lowercase())
            result.onFailure { showSaveError(it) }
        }
    }
}
