package com.example.plansync.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.CrewRepository
import com.example.plansync.data.PlanRepository
import com.example.plansync.model.Friend
import com.example.plansync.model.Plan
import com.example.plansync.model.RsvpStatus
import com.example.plansync.service.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InviteToGroupViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CrewRepository()
    private val planRepository = PlanRepository()
    private val networkMonitor = NetworkMonitor.get(application)

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

            val memberIds = repository.getMemberIds(listOf(groupId)).getOrElse { error ->
                _uiState.update { it.copy(errorMessage = error.message, isLoading = false) }
                return@launch
            }[groupId].orEmpty()

            val pendingIds = repository.getPendingInviteeIds(groupId).getOrElse { error ->
                _uiState.update { it.copy(errorMessage = error.message, isLoading = false) }
                return@launch
            }

            val plans = planRepository.getMyPlans().getOrNull()

            repository.getFriends()
                .onSuccess { friends ->
                    val invitable = friends.filter { it.id !in memberIds && it.id !in pendingIds }
                    val sorted = if (plans == null) invitable else sortBySharedPlans(invitable, plans)
                    _uiState.update { it.copy(friends = sorted, isLoading = false) }
                }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message, isLoading = false) } }

            loadPhotos()
        }
    }

    private fun sortBySharedPlans(friends: List<Friend>, plans: List<Plan>): List<Friend> {
        val myId = repository.currentUserId ?: return friends.sortedBy { it.name.lowercase() }
        return friends.sortedBy { it.name.lowercase() }
            .sortedByDescending { friend -> plans.count { plan -> isGoing(plan, myId) && isGoing(plan, friend.id) } }
    }

    private fun isGoing(plan: Plan, userId: String): Boolean =
        plan.invitations.any { it.userId == userId && it.rsvp == RsvpStatus.GOING }

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
        if (!networkMonitor.isCurrentlyOnline()) {
            _uiState.update { it.copy(inviteError = "Group functionalities are not available while offline.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(inviteError = null) }

            repository.inviteToGroup(groupId, friendId)
                .onSuccess { _uiState.update { it.copy(invitedIds = it.invitedIds + friendId) } }
                .onFailure { error -> _uiState.update { it.copy(inviteError = error.message) } }
        }
    }
}
