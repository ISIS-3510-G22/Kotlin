package com.example.plansync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plansync.model.PaymentMethod
import com.example.plansync.model.User
import com.example.plansync.ui.theme.Coral
import com.example.plansync.viewmodel.UserDetailViewModel

@Composable
fun UserDetailScreen(
    userId: String,
    viewModel: UserDetailViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(userId) {
        viewModel.load(userId)
    }

    Scaffold(
        containerColor = Color(0xFFF5F5F5),
        topBar = { CrewTopBar(title = "User Detail", onBack = onBack) }
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
                uiState.errorMessage != null -> CrewErrorMessage(message = uiState.errorMessage!!)
                uiState.user != null -> UserDetailContent(user = uiState.user!!)
            }
        }
    }
}

@Composable
private fun UserDetailContent(user: User) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp)
    ) {
        CrewAvatar(
            name = user.name,
            lastName = user.lastName,
            colorIndex = 0,
            size = 120.dp,
            shape = RoundedCornerShape(24.dp),
            photoUrl = user.photoUrl,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "${user.name} ${user.lastName}".trim(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = user.email,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF888888),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(32.dp))
        CrewSectionLabel("REIMBURSEMENT METHODS")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Send payments to ${user.name} using any of these methods.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF888888)
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (user.reimbursementMethods.isEmpty()) {
            Text(
                text = "No reimbursement methods yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF888888)
            )
        }
        user.reimbursementMethods.forEach { method ->
            ReimbursementMethodCard(method = method)
        }
    }
}

@Composable
private fun ReimbursementMethodCard(method: PaymentMethod) {
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Coral.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = methodIcon(method.label),
                    contentDescription = null,
                    tint = Coral,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = method.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = method.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF888888)
                )
            }
            Button(
                onClick = { clipboardManager.setText(AnnotatedString(method.detail)) },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Coral.copy(alpha = 0.12f), contentColor = Coral)
            ) {
                Text(text = "Copy", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun methodIcon(type: String): ImageVector {
    val lowerType = type.lowercase()
    return when {
        lowerType.contains("nequi") -> Icons.Default.Phone
        lowerType.contains("daviplata") -> Icons.Default.AccountBalanceWallet
        else -> Icons.Default.CreditCard
    }
}
