package com.example.plansync.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.service.OfflineSyncService
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SyncStatusViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val isOnline: Boolean = true,
        val queuedCount: Int = 0
    ) {
        val showQueuedBanner: Boolean get() = !isOnline && queuedCount > 0
    }

    val uiState: StateFlow<UiState> = combine(
        OfflineSyncService.isOnline,
        OfflineSyncService.queuedCount
    ) { online, count -> UiState(online, count) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    val queuedNotices: SharedFlow<String> = OfflineSyncService.queuedNotices
}
