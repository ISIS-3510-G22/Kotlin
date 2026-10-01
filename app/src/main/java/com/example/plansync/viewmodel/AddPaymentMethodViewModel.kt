package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AddPaymentMethodViewModel(
    private val profileRepository: ProfileRepository = ProfileRepository()
) : ViewModel() {

    data class UiState(
        val accountType: String = "",
        val account: String = "",
        val isSaving: Boolean = false,
        val errorMessage: String? = null,
        val didSave: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onAccountTypeChange(value: String) = _uiState.update { it.copy(accountType = value) }

    fun onAccountChange(value: String) = _uiState.update { it.copy(account = value) }

    fun saveAccount() {
        val current = _uiState.value
        if (current.accountType.isBlank() || current.account.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please fill in both fields.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            profileRepository.addPaymentMethod(
                accountType = current.accountType.trim(),
                account = current.account.trim()
            )
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, didSave = true) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = error.message) }
                }
        }
    }
}
