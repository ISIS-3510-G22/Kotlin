package com.example.plansync.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plansync.ui.theme.Coral
import com.example.plansync.viewmodel.InviteToGroupViewModel

@Composable
fun InviteToGroupScreen(
    groupId: String,
    viewModel: InviteToGroupViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(groupId) {
        viewModel.load(groupId)
    }

    Scaffold(
        containerColor = Color(0xFFF5F5F5),
        topBar = { CrewTopBar(title = "Invite to Group", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            CrewSectionLabel("YOUR FRIENDS")

            if (uiState.inviteError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.inviteError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(color = Coral, modifier = Modifier.align(Alignment.Center))
                    }
                    uiState.errorMessage != null -> CrewErrorMessage(message = uiState.errorMessage!!)
                    uiState.friends.isEmpty() -> EmptyCrewMessage(message = "No friends to invite.")
                    else -> {
                        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                            itemsIndexed(uiState.friends) { index, friend ->
                                val isInvited = friend.id in uiState.invitedIds
                                CrewPersonCard(
                                    name = friend.name,
                                    lastName = "",
                                    subtitle = friend.email,
                                    photoUrl = friend.photoUrl,
                                    colorIndex = index,
                                    buttonLabel = if (isInvited) "Invited" else "Invite",
                                    buttonEnabled = !isInvited,
                                    onButtonClick = { viewModel.invite(groupId, friend.id) },
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
