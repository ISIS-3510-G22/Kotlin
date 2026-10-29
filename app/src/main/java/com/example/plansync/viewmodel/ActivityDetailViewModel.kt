package com.example.plansync.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.ActivityRepository
import com.example.plansync.data.StorageRepository
import com.example.plansync.model.Activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class ActivityDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ActivityRepository()
    private val storageRepository = StorageRepository()

    data class UiState(
        val activity: Activity? = null,
        val isOwner: Boolean = false,
        val isLiked: Boolean = false,
        val isLoading: Boolean = false,
        val isUploadingPhoto: Boolean = false,
        val isSharing: Boolean = false,
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

    fun shareActivity() {
        val activity = _uiState.value.activity ?: return
        _uiState.update { it.copy(isSharing = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val imageUri = try {
                downloadPhoto(activity)
            } catch (e: IOException) {
                null
            }

            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isSharing = false) }
                getApplication<Application>().startActivity(shareIntent(activity, imageUri))
            }
        }
    }

    private fun downloadPhoto(activity: Activity): Uri? {
        if (activity.photoUrl.isBlank()) return null
        val app = getApplication<Application>()
        val file = File(app.cacheDir, "shared_images/${activity.id}.jpg").apply { parentFile?.mkdirs() }
        storageRepository.downloadToFile(activity.photoUrl, file)
        return FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
    }

    private fun shareIntent(activity: Activity, imageUri: Uri?): Intent {
        val text = "Let's go to ${activity.name}!\n${activity.address}\n\nShared from PlanSync"
        val send = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            if (imageUri != null) {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                clipData = ClipData.newRawUri(null, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
        }
        return Intent.createChooser(send, "Share activity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
