package com.example.plansync.viewmodel

import android.app.Application
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.PasswordResetRepository
import com.example.plansync.service.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ForgotPasswordViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PasswordResetRepository()

    private val networkMonitor = NetworkMonitor.get(application)

    data class UiState(
        val email: String = "",
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val successMessage: String? = null,
        val isOffline: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        networkMonitor.isOnline
            .onEach { online -> _uiState.update { it.copy(isOffline = !online) } }
            .launchIn(viewModelScope)
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, errorMessage = null, successMessage = null) }
    }

    fun onScreenOpened() {
        _uiState.update { it.copy(email = "", isLoading = false, errorMessage = null, successMessage = null) }
    }

    fun sendReset() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your email.", successMessage = null) }
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(errorMessage = "Enter a valid email address.", successMessage = null) }
            return
        }
        if (!networkMonitor.isCurrentlyOnline()) {
            _uiState.update {
                it.copy(
                    errorMessage = "No internet connection. Please try again when online.",
                    successMessage = null
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }

            repository.sendResetEmail(email)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "If an account exists for this email, we sent a link to reset your password."
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }
}
