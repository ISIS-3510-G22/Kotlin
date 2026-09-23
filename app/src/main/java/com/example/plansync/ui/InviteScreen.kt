package com.example.plansync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plansync.model.Contact
import com.example.plansync.viewmodel.InviteViewModel

/**
 * UI layer — Composable screen for inviting friends or groups to a Plan.
 *
 * Strictly follows MVVM: reads state only from [InviteViewModel] via
 * collectAsStateWithLifecycle() and calls only ViewModel functions.
 * Never touches InviteRepository directly.
 *
 * When the user taps "Invite" on a contact, the ViewModel sets
 * [InviteViewModel.UiState.pendingInviteContact], which triggers the
 * confirmation dialog (rendered in Issue #12 — InviteDialogs.kt).
 *
 * @param planId    The plan these invites are associated with.
 * @param viewModel Injected via viewModel() factory.
 * @param onBack    Callback for the back arrow.
 */
@Composable
fun InviteScreen(
    planId: String,
    viewModel: InviteViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Load contacts once when screen enters composition
    LaunchedEffect(Unit) {
        viewModel.loadContacts()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {

        // ── Top bar ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Invite Friends or Groups",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider(color = Color(0xFFEEEEEE))

        // ── Search bar ─────────────────────────────────────────────────────
        Box(modifier = Modifier.background(Color.White).padding(12.dp)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = { Text("Search friends or groups...", color = Color(0xFF888888)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF888888))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0xFFDDDDDD),
                    focusedBorderColor = Color(0xFF888888),
                    unfocusedContainerColor = Color(0xFFF5F5F5),
                    focusedContainerColor = Color(0xFFF5F5F5)
                )
            )
        }

        HorizontalDivider(color = Color(0xFFEEEEEE))

        // ── Contact list ───────────────────────────────────────────────────
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize().background(Color.White)) {

                    // Section header
                    item {
                        Text(
                            text = "SUGGESTED FRIENDS",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF888888),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }

                    items(uiState.filteredContacts) { contact ->
                        ContactRow(
                            contact = contact,
                            onInviteTapped = { viewModel.onInviteTapped(contact) }
                        )
                        HorizontalDivider(
                            color = Color(0xFFF0F0F0),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }

    // ── Invite confirmation dialog ─────────────────────────────────────────
    // Shown when ViewModel sets a pendingInviteContact.
    // The actual dialog composables live in InviteDialogs.kt (Issue #12).
    // Placeholder: auto-confirm for now so the flow is testable end-to-end.
    uiState.pendingInviteContact?.let {
        viewModel.onInviteConfirmed(planId)
    }
}

// ── Contact row ────────────────────────────────────────────────────────────────

@Composable
private fun ContactRow(
    contact: Contact,
    onInviteTapped: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with initials
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(10.dp))
                .background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.initials,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name and handle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (contact.handle != null) {
                Text(
                    text = contact.handle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF888888)
                )
            }
        }

        // Invite / Invited button
        if (contact.isInvited) {
            OutlinedButton(
                onClick = {},
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCCCCCC)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF888888))
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Invited", style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Button(
                onClick = onInviteTapped,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                )
            ) {
                Text("Invite", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
