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
import com.example.plansync.service.NetworkMonitor
import com.example.plansync.service.OfflineSyncService
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
    private val networkMonitor = NetworkMonitor.get(application)
    private var activityId: String? = null

    data class UiState(
        val activity: Activity? = null,
        val isOwner: Boolean = false,
        val isLiked: Boolean = false,
        val isLoading: Boolean = false,
        val isUploadingPhoto: Boolean = false,
        val isSharing: Boolean = false,
        val isDeleting: Boolean = false,
        val didDelete: Boolean = false,
        val deletedOffline: Boolean = false,
        val isPending: Boolean = false,
        val isOnline: Boolean = true,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState(isOnline = networkMonitor.isCurrentlyOnline()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                val reconnected = online && !_uiState.value.isOnline
                _uiState.update { it.copy(isOnline = online) }
                if (reconnected) {
                    OfflineSyncService.syncNow()
                    activityId?.let(::load)
                }
            }
        }
    }

    fun load(activityId: String) {
        this.activityId = activityId
        viewModelScope.launch {
            val cached = repository.getCachedActivity(activityId)
            _uiState.update { it.copy(isLoading = cached == null, errorMessage = null) }
            if (cached != null) show(cached)

            val isOnline = _uiState.value.isOnline
            val fromNetwork = if (isOnline) repository.getActivity(activityId) else null
            val activity = fromNetwork?.getOrNull()
                ?.let { OfflineSyncService.applyPending(listOf(it)).first() }
                ?.also { repository.cacheActivity(it) }
                ?: cached

            if (activity != null) {
                show(activity)
            } else {
                val message = if (isOnline) {
                    fromNetwork?.exceptionOrNull()?.message
                } else {
                    "This activity isn't saved on your phone. Connect to the internet to open it."
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = message) }
            }
        }
    }

    fun toggleLike() {
        val activity = _uiState.value.activity ?: return
        val uid = repository.currentUserId ?: return
        val liked = !_uiState.value.isLiked
        viewModelScope.launch {
            if (!_uiState.value.isOnline || _uiState.value.isPending) {
                show(OfflineSyncService.setLikedOffline(activity, liked))
                return@launch
            }
            OfflineSyncService.syncNow()
            val updated = activity.copy(likedBy = if (liked) activity.likedBy + uid else activity.likedBy - uid)
            repository.setLiked(activity.id, liked)
                .onSuccess {
                    repository.cacheActivity(updated)
                    show(updated)
                }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }
        }
    }

    fun onPhotoPicked(uri: Uri) {
        val activity = _uiState.value.activity ?: return
        if (!_uiState.value.isOnline || _uiState.value.isPending) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingPhoto = true, errorMessage = null) }
            repository.updatePhoto(activity.id, uri)
                .onSuccess { photoUrl ->
                    val updated = activity.copy(photoUrl = photoUrl)
                    repository.cacheActivity(updated)
                    _uiState.update { it.copy(isUploadingPhoto = false, activity = updated) }
                }
                .onFailure { error -> _uiState.update { it.copy(isUploadingPhoto = false, errorMessage = error.message) } }
        }
    }

    fun deleteActivity() {
        val current = _uiState.value
        val activity = current.activity ?: return
        if (!current.isOwner || current.isDeleting || current.didDelete) return
        _uiState.update { it.copy(isDeleting = true, errorMessage = null) }
        viewModelScope.launch {
            if (!current.isOnline || current.isPending) {
                OfflineSyncService.deleteActivityOffline(activity)
                _uiState.update { it.copy(isDeleting = false, didDelete = true, deletedOffline = !current.isOnline) }
                return@launch
            }
            OfflineSyncService.syncNow()
            repository.deleteActivity(activity.id, activity.photoUrl)
                .onSuccess { _uiState.update { it.copy(isDeleting = false, didDelete = true) } }
                .onFailure { error -> _uiState.update { it.copy(isDeleting = false, errorMessage = error.message) } }
        }
    }

    fun shareActivity() {
        val activity = _uiState.value.activity ?: return
        val isOnline = _uiState.value.isOnline
        _uiState.update { it.copy(isSharing = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val imageUri = try {
                if (isOnline) downloadPhoto(activity) else null
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

    private suspend fun show(activity: Activity) {
        val uid = repository.currentUserId
        val isPending = activity.id in OfflineSyncService.pendingActivityIds()
        _uiState.update {
            it.copy(
                activity = activity,
                isOwner = activity.ownerId == uid,
                isLiked = uid in activity.likedBy,
                isPending = isPending,
                isLoading = false
            )
        }
    }
}
