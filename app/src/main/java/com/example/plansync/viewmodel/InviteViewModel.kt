package com.example.plansync.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.AnalyticsRepository
import com.example.plansync.data.InviteRepository
import com.example.plansync.model.PendingAction
import com.example.plansync.service.NetworkMonitor
import com.example.plansync.service.NotificationFactory
import com.example.plansync.service.NotificationService
import com.example.plansync.service.OfflineSyncService
import com.example.plansync.model.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel layer — MVVM architecture.
 *
 * Owns UI state for the Invite Friends or Groups screen.
 * Observer pattern: exposes [uiState] as StateFlow, collected by
 * InviteScreen via collectAsStateWithLifecycle().
 *
 * The Composable never calls InviteRepository directly.
 */
class InviteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = InviteRepository()
    private val analyticsRepository = AnalyticsRepository()
    private val notificationService = NotificationService(application)
    private val networkMonitor = NetworkMonitor.get(application)

    private var currentPlanId: String? = null

    data class UiState(
        val contacts: List<Contact> = emptyList(),
        val filteredContacts: List<Contact> = emptyList(),
        val searchQuery: String = "",
        val isLoading: Boolean = false,
        val pendingInviteContact: Contact? = null,
        val pendingSync: Boolean = false,
        val isOnline: Boolean = true,      // tracked here so the Screen never touches NetworkMonitor
        val errorMessage: String? = null,  // surfaces permanent sync failures from OfflineSyncService
        // True when offline and no cached contacts exist yet — prevents silent empty list
        val neverCached: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Track connectivity in UiState — Screen reads uiState.isOnline, never NetworkMonitor directly
        networkMonitor.isOnline
            .onEach { online -> _uiState.update { it.copy(isOnline = online) } }
            .launchIn(viewModelScope)
        // Reload contacts on reconnect so the list is never stale after coming back online
        viewModelScope.launch {
            networkMonitor.isOnline
                .distinctUntilChanged()
                .filter { it }
                .collect { currentPlanId?.let { id -> loadContacts(id) } }
        }
        // Surface permanent sync failures (e.g. queued invite rejected by server)
        viewModelScope.launch {
            OfflineSyncService.syncErrors.collect { msg ->
                _uiState.update { it.copy(errorMessage = msg) }
            }
        }
    }

    /** Load suggested contacts for [planId], marking those already invited. */
    fun loadContacts(planId: String) {
        currentPlanId = planId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val contacts = repository.getSuggestedContacts(planId)
            // neverCached = true when the result is empty because we're offline with no file cache
            val neverCached = contacts.isEmpty() && !networkMonitor.isCurrentlyOnline()
            _uiState.update {
                it.copy(isLoading = false, contacts = contacts, filteredContacts = contacts, neverCached = neverCached)
            }
        }
    }

    /** Filter the contact list as the user types in the search field. */
    fun onSearchQueryChange(query: String) {
        val filtered = _uiState.value.contacts.filter { contact ->
            contact.name.contains(query, ignoreCase = true) ||
                    contact.handle?.contains(query, ignoreCase = true) == true
        }
        _uiState.update { it.copy(searchQuery = query, filteredContacts = filtered) }
    }

    /**
     * Called when the user taps "Invite" on a contact row.
     * Sets [UiState.pendingInviteContact] to trigger the confirmation dialog.
     */
    fun onInviteTapped(contact: Contact) {
        _uiState.update { it.copy(pendingInviteContact = contact) }
    }

    /** Clears the pending contact, dismissing the confirmation dialog. */
    fun onDialogDismissed() {
        _uiState.update { it.copy(pendingInviteContact = null) }
    }

    /**
     * Confirms the invite for [planId] and the current [pendingInviteContact].
     * Marks the contact as invited in the list on success.
     */
    fun onInviteConfirmed(planId: String) {
        val contact = _uiState.value.pendingInviteContact ?: return
        viewModelScope.launch {
            val isOnline = networkMonitor.isCurrentlyOnline()

            // Mark contact as invited in the UI regardless of connectivity.
            _uiState.update { state ->
                val updated = state.contacts.map {
                    if (it.id == contact.id) it.copy(isInvited = true) else it
                }
                state.copy(
                    contacts = updated,
                    filteredContacts = updated.filter { c ->
                        c.name.contains(state.searchQuery, ignoreCase = true) ||
                                c.handle?.contains(state.searchQuery, ignoreCase = true) == true
                    },
                    pendingInviteContact = null,
                    pendingSync = !isOnline
                )
            }

            if (!isOnline) {
                // Queue for sync when back online instead of silently dropping
                OfflineSyncService.enqueue(PendingAction.SendInvite(planId, contact.id))
                return@launch
            }

            repository.inviteContact(planId, contact.id)
                .onSuccess {
                    analyticsRepository.logEvent(AnalyticsRepository.INVITE_SENT, planId, inviteeId = contact.id)
                    NotificationFactory.create(AnalyticsRepository.INVITE_SENT, personName = contact.name)
                        ?.let(notificationService::show)
                    _uiState.update { it.copy(pendingSync = false) }
                }
                .onFailure {
                    // Revert the optimistic update on hard failure.
                    _uiState.update { state ->
                        val reverted = state.contacts.map {
                            if (it.id == contact.id) it.copy(isInvited = false) else it
                        }
                        state.copy(contacts = reverted, filteredContacts = reverted)
                    }
                }
        }
    }
}
