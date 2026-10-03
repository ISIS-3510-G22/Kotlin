package com.example.plansync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.example.plansync.model.Group
import com.example.plansync.model.User
import com.example.plansync.ui.theme.Coral
import com.example.plansync.viewmodel.GroupDetailViewModel

@Composable
fun GroupDetailScreen(
    groupId: String,
    viewModel: GroupDetailViewModel = viewModel(),
    onBack: () -> Unit = {},
    onInvite: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(groupId) {
        viewModel.load(groupId)
    }

    Scaffold(
        containerColor = Color(0xFFF5F5F5),
        topBar = { CrewTopBar(title = "Your Groups", onBack = onBack) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(color = Coral, modifier = Modifier.align(Alignment.Center))
                }
                uiState.errorMessage != null -> {
                    Text(
                        text = uiState.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.group != null -> {
                    GroupDetailContent(
                        group = uiState.group!!,
                        members = uiState.members,
                        onInvite = { onInvite(groupId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupDetailContent(group: Group, members: Map<String, User>, onInvite: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 32.dp)) {
        Text(
            text = group.name,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${group.memberIds.size} members",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF888888)
        )
        if (group.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = group.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF888888)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            itemsIndexed(group.memberIds) { index, memberId ->
                MemberTile(member = members[memberId], colorIndex = index)
            }
            item {
                InviteTile(onClick = onInvite)
            }
        }
    }
}

@Composable
private fun MemberTile(member: User?, colorIndex: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CrewAvatar(
            name = member?.name.orEmpty(),
            lastName = member?.lastName.orEmpty(),
            colorIndex = colorIndex,
            size = 48.dp,
            shape = RoundedCornerShape(12.dp),
            photoUrl = member?.photoUrl.orEmpty()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = member?.name.orEmpty().substringBefore(" "),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun InviteTile(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFECEEF2)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = null,
                tint = Color(0xFF666666),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Invite",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF888888)
        )
    }
}
