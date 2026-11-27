package com.example.plansync.viewmodel

import android.app.Application
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.PasswordResetPreferencesRepository
import com.example.plansync.data.PasswordResetQueueRepository
import com.example.plansync.data.PasswordResetRepository
import com.example.plansync.service.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ForgotPasswordViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PasswordResetRepository()

    private val queueRepository = PasswordResetQueueRepository()

    private val preferencesRepository = PasswordResetPreferencesRepository(application)

    private val networkMonitor = NetworkMonitor.get(application)

    private var cooldownJob: Job? = null

    data class UiState(
        val email: String = "",
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val successMessage: String? = null,
        val isOffline: Boolean = false,
        val pendingEmail: String? = null,
        val infoMessage: String? = null,
        val cooldownSeconds: Int = 0
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        networkMonitor.isOnline
            .onEach { online ->
                _uiState.update { it.copy(isOffline = !online) }
                if (online && _uiState.value.pendingEmail != null) flushPending()
            }
            .launchIn(viewModelScope)
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, errorMessage = null, successMessage = null) }
    }

    fun onScreenOpened() {
        _uiState.update {
            it.copy(
                email = "",
                isLoading = false,
                errorMessage = null,
                successMessage = null,
                infoMessage = null,
                pendingEmail = null
            )
        }
        viewModelScope.launch {
            queueRepository.getPending().getOrNull()?.let { saved ->
                _uiState.update { it.copy(email = saved, pendingEmail = saved, infoMessage = OFFLINE_INFO) }
                if (networkMonitor.isCurrentlyOnline()) flushPending()
            }
        }
        viewModelScope.launch {
            preferencesRepository.getLastSentAt().getOrNull()?.let { startCooldown(it) }
        }
    }

    private fun startCooldown(lastSentAt: Long) {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                val elapsed = (System.currentTimeMillis() - lastSentAt) / 1000
                if (elapsed < 0 || elapsed >= COOLDOWN_SECONDS) {
                    _uiState.update { it.copy(cooldownSeconds = 0) }
                    break
                }
                _uiState.update { it.copy(cooldownSeconds = (COOLDOWN_SECONDS - elapsed).toInt()) }
                delay(1000)
            }
        }
    }

    private fun registerSend() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            preferencesRepository.saveLastSentAt(now)
            startCooldown(now)
        }
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
        val secondsLeft = _uiState.value.cooldownSeconds
        if (secondsLeft > 0) {
            _uiState.update {
                it.copy(
                    errorMessage = "Please wait $secondsLeft seconds before asking for another link.",
                    successMessage = null
                )
            }
            return
        }
        if (!networkMonitor.isCurrentlyOnline()) {
            viewModelScope.launch {
                queueRepository.save(email)
                    .onSuccess {
                        _uiState.update {
                            it.copy(
                                pendingEmail = email,
                                infoMessage = OFFLINE_INFO,
                                errorMessage = null,
                                successMessage = null
                            )
                        }
                    }
                    .onFailure {
                        _uiState.update {
                            it.copy(errorMessage = "Could not save your request. Please try again.")
                        }
                    }
            }
            return
        }

        viewModelScope.launch {
            if (_uiState.value.pendingEmail != null) {
                queueRepository.clear()
                _uiState.update { it.copy(pendingEmail = null, infoMessage = null) }
            }
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }

            repository.sendResetEmail(email)
                .onSuccess {
                    registerSend()
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

    private fun flushPending() {
        val state = _uiState.value
        val email = state.pendingEmail ?: return
        if (state.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }

            repository.sendResetEmail(email)
                .onSuccess {
                    queueRepository.clear()
                    registerSend()
                    _uiState.update {
                        it.copy(
                            pendingEmail = null,
                            infoMessage = null,
                            isLoading = false,
                            successMessage = "The reset link was sent. If an account exists for this email, check your inbox to reset your password."
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    private companion object {
        const val COOLDOWN_SECONDS = 60
        const val OFFLINE_INFO =
            "You are offline. We saved your request and will send the link when you are back online. Keep the app open."
    }
}
