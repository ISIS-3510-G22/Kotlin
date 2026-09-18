package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import com.example.plansync.data.AuthRepository
import com.example.plansync.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ProfileViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    data class UiState(
        val user: User? = null,
        val isLoggedOut: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun loadUser() {
        _uiState.update { it.copy(user = repository.getCurrentUser()) }
    }

    fun logOut() {
        repository.signOut()
        _uiState.update { it.copy(user = null, isLoggedOut = true) }
    }
}
