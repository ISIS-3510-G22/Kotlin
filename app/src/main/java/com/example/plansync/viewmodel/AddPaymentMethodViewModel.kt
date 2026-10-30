package com.example.plansync.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.ProfileRepository
import com.example.plansync.data.WriteResult
import com.example.plansync.model.PendingAction
import com.example.plansync.service.NetworkMonitor
import com.example.plansync.service.OfflineSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AddPaymentMethodViewModel(application: Application) : AndroidViewModel(application) {

    private val profileRepository = ProfileRepository()
    private val networkMonitor = NetworkMonitor.get(application)

    data class UiState(
        val accountType: String = "",
        val account: String = "",
        val isSaving: Boolean = false,
        val isOnline: Boolean = true,
        val errorMessage: String? = null,
        val didSave: Boolean = false,
        val savedOffline: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState(isOnline = networkMonitor.isCurrentlyOnline()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
    }

    fun onAccountTypeChange(value: String) = _uiState.update { it.copy(accountType = value) }

    fun onAccountChange(value: String) = _uiState.update { it.copy(account = value) }

    fun saveAccount() {
        val current = _uiState.value
        if (current.isSaving || current.didSave) return
        if (current.accountType.isBlank() || current.account.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please fill in both fields.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        val method = profileRepository.newPaymentMethod(current.accountType.trim(), current.account.trim())

        viewModelScope.launch {
            val result = if (current.isOnline) {
                OfflineSyncService.syncNow()
                profileRepository.addPaymentMethod(method)
            } else {
                OfflineSyncService.enqueue(PendingAction.AddPaymentMethod(method))
                WriteResult.Queued
            }
            _uiState.update {
                when (result) {
                    WriteResult.Committed -> it.copy(isSaving = false, didSave = true)
                    WriteResult.Queued -> it.copy(isSaving = false, didSave = true, savedOffline = true)
                    is WriteResult.Failed -> it.copy(isSaving = false, errorMessage = result.error.message)
                }
            }
        }
    }
}
