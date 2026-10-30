package com.example.plansync.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.AuthRepository
import com.example.plansync.data.ProfileRepository
import com.example.plansync.model.User
import com.example.plansync.service.NetworkMonitor
import com.example.plansync.service.OfflineSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository()
    private val profileRepository = ProfileRepository()
    private val networkMonitor = NetworkMonitor.get(application)

    data class UiState(
        val user: User? = null,
        val isOnline: Boolean = true,
        val showUnsyncedWarning: Boolean = false,
        val isLoggedOut: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState(isOnline = networkMonitor.isCurrentlyOnline()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                val reconnected = online && !_uiState.value.isOnline
                _uiState.update { it.copy(isOnline = online) }
                if (reconnected) loadUser()
            }
        }
    }

    fun loadUser() {
        viewModelScope.launch {
            val user = if (_uiState.value.isOnline) {
                repository.getCurrentUser()?.also { profileRepository.cacheUser(it) }
            } else {
                profileRepository.getCachedUser() ?: repository.getCurrentUser()
            }
            _uiState.update { it.copy(user = user?.let { OfflineSyncService.applyPending(it) }) }
        }
    }

    fun onLogOutClick() {
        viewModelScope.launch {
            if (_uiState.value.isOnline) OfflineSyncService.syncNow()
            if (OfflineSyncService.hasPendingActions()) {
                _uiState.update { it.copy(showUnsyncedWarning = true) }
            } else {
                logOut()
            }
        }
    }

    fun onUnsyncedWarningDismissed() = _uiState.update { it.copy(showUnsyncedWarning = false) }

    fun logOut() {
        repository.signOut()
        _uiState.update { it.copy(user = null, showUnsyncedWarning = false, isLoggedOut = true) }
    }
}
