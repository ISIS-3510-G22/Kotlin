package com.example.plansync.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plansync.model.FriendRequest
import com.example.plansync.ui.theme.Coral
import com.example.plansync.viewmodel.FriendRequestsViewModel

@Composable
fun FriendRequestsScreen(
    viewModel: FriendRequestsViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Scaffold(
        containerColor = Color(0xFFF5F5F5),
        topBar = { CrewTopBar(title = "Friend Invites", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.actionError != null) {
                Text(
                    text = uiState.actionError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(color = Coral, modifier = Modifier.align(Alignment.Center))
                    }
                    uiState.errorMessage != null -> CrewErrorMessage(message = uiState.errorMessage!!)
                    uiState.requests.isEmpty() -> EmptyCrewMessage(message = "No friend requests.")
                    else -> {
                        LazyColumn(contentPadding = PaddingValues(vertical = 24.dp)) {
                            itemsIndexed(uiState.requests) { index, request ->
                                FriendRequestCard(
                                    request = request,
                                    colorIndex = index,
                                    isSaving = uiState.isSaving,
                                    onAccept = { viewModel.accept(request) },
                                    onDeny = { viewModel.deny(request) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendRequestCard(
    request: FriendRequest,
    colorIndex: Int,
    isSaving: Boolean,
    onAccept: () -> Unit,
    onDeny: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CrewAvatar(
                name = request.name,
                lastName = request.lastName,
                colorIndex = colorIndex,
                size = 32.dp,
                shape = RoundedCornerShape(8.dp),
                photoUrl = request.photoUrl
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${request.name} ${request.lastName}".trim(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "@${request.username}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF888888)
            )
            Spacer(modifier = Modifier.height(12.dp))
            CrewDecisionButtons(
                acceptLabel = "Accept",
                enabled = !isSaving,
                onAccept = onAccept,
                onDeny = onDeny
            )
        }
    }
}
