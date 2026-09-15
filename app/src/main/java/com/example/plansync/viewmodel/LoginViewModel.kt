package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.AuthRepository
import com.example.plansync.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel layer — MVVM architecture.
 *
 * Owns and manages the UI state for the Login screen. Survives configuration
 * changes (e.g. screen rotation) because it sits above the Composable lifecycle.
 *
 * Observer pattern: exposes [uiState] as a [StateFlow] so the UI layer can
 * observe changes reactively without polling. The Composable collects this
 * flow with collectAsStateWithLifecycle(), which is lifecycle-safe.
 *
 * The Composable calls functions on this ViewModel only — it never touches
 * [AuthRepository] directly. This keeps the UI layer thin and testable.
 */
class LoginViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    /**
     * Immutable snapshot of everything the Login screen needs to render itself.
     * A new copy is emitted every time any field changes.
     */
    data class UiState(
        val email: String = "",
        val password: String = "",
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val loggedInUser: User? = null
    )

    // Mutable backing field — private so only this ViewModel can write to it
    private val _uiState = MutableStateFlow(UiState())

    // Read-only StateFlow exposed to the UI layer
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Called by the UI whenever the email field changes. */
    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, errorMessage = null) }
    }

    /** Called by the UI whenever the password field changes. */
    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    /**
     * Initiates a login attempt using the current email and password.
     *
     * Runs inside [viewModelScope] so it is automatically cancelled if the
     * ViewModel is cleared (e.g. user navigates away), preventing leaks.
     *
     * Sets [UiState.isLoading] to true while the repository call is in flight,
     * then either populates [UiState.loggedInUser] on success or
     * [UiState.errorMessage] on failure.
     */
    fun login() {
        val current = _uiState.value
        if (current.email.isBlank() || current.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your email and password.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = repository.login(current.email, current.password)

            result
                .onSuccess { user ->
                    _uiState.update { it.copy(isLoading = false, loggedInUser = user) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message)
                    }
                }
        }
    }
}
