package com.example.plansync.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.AuthRepository
import com.example.plansync.data.ProfileRepository
import com.example.plansync.model.PaymentMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditProfileViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val profileRepository: ProfileRepository = ProfileRepository()
) : ViewModel() {

    data class UiState(
        val fullName: String = "",
        val phone: String = "",
        val photoUrl: String = "",
        val paymentMethods: List<PaymentMethod> = emptyList(),
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        val isUploadingPhoto: Boolean = false,
        val errorMessage: String? = null,
        val didSave: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

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
            profileRepository.deletePaymentMethod(method)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            paymentMethods = state.paymentMethods
                                .filter { it.id != method.id }
                                .mapIndexed { i, m -> m.copy(isPrimary = i == 0) }
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
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
            profileRepository.updateProfile(name = name, lastName = lastName, phone = current.phone)
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, didSave = true) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = error.message) }
                }
        }
    }

    fun onSaveHandled() = _uiState.update { it.copy(didSave = false) }
}
