package com.example.plansync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plansync.data.InviteRepository
import com.example.plansync.model.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
class InviteViewModel(
    private val repository: InviteRepository = InviteRepository()
) : ViewModel() {

    data class UiState(
        val contacts: List<Contact> = emptyList(),
        val filteredContacts: List<Contact> = emptyList(),
        val searchQuery: String = "",
        val isLoading: Boolean = false,
        val pendingInviteContact: Contact? = null  // contact awaiting confirmation dialog
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Load suggested contacts when the screen first opens. */
    fun loadContacts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val contacts = repository.getSuggestedContacts()
            _uiState.update {
                it.copy(isLoading = false, contacts = contacts, filteredContacts = contacts)
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
            repository.inviteContact(planId, contact.id)
                .onSuccess {
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
                            pendingInviteContact = null
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(pendingInviteContact = null) }
                }
        }
    }
}
