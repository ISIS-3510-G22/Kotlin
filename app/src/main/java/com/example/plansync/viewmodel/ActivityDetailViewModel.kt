package com.example.plansync.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.ActivityRepository
import com.example.plansync.model.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ActivityDetailViewModel(
    private val repository: ActivityRepository = ActivityRepository()
) : ViewModel() {

    data class UiState(
        val activity: Activity? = null,
        val isOwner: Boolean = false,
        val isLiked: Boolean = false,
        val isLoading: Boolean = false,
        val isUploadingPhoto: Boolean = false,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun load(activityId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.getActivity(activityId)
                .onSuccess { activity -> show(activity) }
                .onFailure { error -> _uiState.update { it.copy(isLoading = false, errorMessage = error.message) } }
        }
    }

    fun toggleLike() {
        val activity = _uiState.value.activity ?: return
        val uid = repository.currentUserId ?: return
        val liked = !_uiState.value.isLiked
        viewModelScope.launch {
            repository.setLiked(activity.id, liked)
                .onSuccess {
                    show(activity.copy(likedBy = if (liked) activity.likedBy + uid else activity.likedBy - uid))
                }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }
        }
    }

    fun onPhotoPicked(uri: Uri) {
        val activity = _uiState.value.activity ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingPhoto = true, errorMessage = null) }
            repository.updatePhoto(activity.id, uri)
                .onSuccess { photoUrl ->
                    _uiState.update { it.copy(isUploadingPhoto = false, activity = activity.copy(photoUrl = photoUrl)) }
                }
                .onFailure { error -> _uiState.update { it.copy(isUploadingPhoto = false, errorMessage = error.message) } }
        }
    }

    private fun show(activity: Activity) {
        val uid = repository.currentUserId
        _uiState.update {
            it.copy(
                activity = activity,
                isOwner = activity.ownerId == uid,
                isLiked = uid in activity.likedBy,
                isLoading = false
            )
        }
    }
}
