package com.example.plansync.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.InsightsRepository
import com.example.plansync.data.PlanDraftRepository
import com.example.plansync.service.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyDraftsViewModel(application: Application) : AndroidViewModel(application) {

    private val draftRepository    = PlanDraftRepository()
    private val insightsRepository = InsightsRepository()
    private val networkMonitor     = NetworkMonitor.get(application)

    data class DraftItem(
        val id: String,
        val planName: String,
        val lastStep: Int,
        val updatedAt: Long
    )

    data class UiState(
        val drafts: List<DraftItem> = emptyList(),
        val topAbandonmentStep: Int? = null,
        val isLoading: Boolean = false,
        val isOnline: Boolean = true
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        networkMonitor.isOnline
            .onEach { online -> _uiState.update { it.copy(isOnline = online) } }
            .launchIn(viewModelScope)
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val draftsDeferred  = async(Dispatchers.IO) { draftRepository.getAll() }
            val insightDeferred = async(Dispatchers.IO) { insightsRepository.getTopAbandonmentStep() }
            val draftEntities = draftsDeferred.await()
            val insight       = insightDeferred.await()
            val items = withContext(Dispatchers.Default) {
                draftEntities.map { DraftItem(it.id, it.planName, it.lastStep, it.updatedAt) }
            }
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(drafts = items, topAbandonmentStep = insight?.step, isLoading = false)
                }
            }
        }
    }

    fun discard(draftId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { draftRepository.delete(draftId) }
            _uiState.update { state -> state.copy(drafts = state.drafts.filter { it.id != draftId }) }
        }
    }
}
