package com.example.plansync.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.AuthRepository
import com.example.plansync.data.ProfileRepository
import com.example.plansync.data.WriteResult
import com.example.plansync.model.PaymentMethod
import com.example.plansync.service.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository()
    private val profileRepository = ProfileRepository()
    private val networkMonitor = NetworkMonitor.get(application)

    data class UiState(
        val fullName: String = "",
        val phone: String = "",
        val photoUrl: String = "",
        val paymentMethods: List<PaymentMethod> = emptyList(),
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        val isUploadingPhoto: Boolean = false,
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

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val user = authRepository.getCurrentUser()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    fullName = listOfNotNull(user?.name, user?.lastName)
                        .filter { part -> part.isNotBlank() }
                        .joinToString(" "),
                    phone = user?.phone.orEmpty(),
                    photoUrl = user?.photoUrl.orEmpty(),
                    paymentMethods = user?.reimbursementMethods.orEmpty()
                )
            }
        }
    }

    fun onFullNameChange(value: String) = _uiState.update { it.copy(fullName = value) }

    fun onPhoneChange(value: String) = _uiState.update { it.copy(phone = value) }

    fun onPhotoPicked(uri: Uri) {
        if (!_uiState.value.isOnline) {
            _uiState.update { it.copy(errorMessage = "You need a connection to change your photo.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingPhoto = true, errorMessage = null) }
            profileRepository.uploadProfilePhoto(uri)
                .onSuccess { photoUrl ->
                    _uiState.update { it.copy(isUploadingPhoto = false, photoUrl = photoUrl) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isUploadingPhoto = false, errorMessage = error.message) }
                }
        }
    }

    fun deletePaymentMethod(method: PaymentMethod) {
        viewModelScope.launch {
            when (val result = profileRepository.deletePaymentMethod(method, _uiState.value.isOnline)) {
                is WriteResult.Failed -> _uiState.update { it.copy(errorMessage = result.error.message) }
                else -> _uiState.update { state ->
                    state.copy(
                        paymentMethods = state.paymentMethods
                            .filter { it.id != method.id }
                            .mapIndexed { i, m -> m.copy(isPrimary = i == 0) }
                    )
                }
            }
        }
    }

    fun saveChanges() {
        val current = _uiState.value
        val parts = current.fullName.trim().split(" ", limit = 2)
        val name = parts.getOrElse(0) { "" }
        val lastName = parts.getOrElse(1) { "" }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = profileRepository.updateProfile(
                name = name,
                lastName = lastName,
                phone = current.phone,
                isOnline = current.isOnline
            )
            _uiState.update {
                when (result) {
                    WriteResult.Committed -> it.copy(isSaving = false, didSave = true)
                    WriteResult.Queued -> it.copy(isSaving = false, didSave = true, savedOffline = true)
                    is WriteResult.Failed -> it.copy(isSaving = false, errorMessage = result.error.message)
                }
            }
        }
    }

    fun onSaveHandled() = _uiState.update { it.copy(didSave = false, savedOffline = false) }

    fun onErrorShown() = _uiState.update { it.copy(errorMessage = null) }
}
