package com.example.plansync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.plansync.model.Split
import com.example.plansync.ui.theme.Coral
import com.example.plansync.ui.theme.CoralLight
import com.example.plansync.viewmodel.ManageSplitsViewModel
import java.util.Locale

@Composable
fun ManageSplitsScreen(
    planId: String,
    viewModel: ManageSplitsViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(planId) {
        viewModel.load(planId)
    }

    Scaffold(
        containerColor = Color(0xFFF5F5F5),
        topBar = {
            Column {
                BackTopBar(title = "Manage Splits", onBack = onBack)
                OfflineBanner(
                    isOnline = uiState.isOnline,
                    message = "You're offline. Showing splits saved on your phone, payments will sync when you reconnect."
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SplitTabRow(selectedTab = uiState.selectedTab, onTabSelected = viewModel::onTabSelected)

            val isOwedTab = uiState.selectedTab == ManageSplitsViewModel.Tab.OWED_TO_ME
            val splits = if (isOwedTab) uiState.owedToMe else uiState.splits

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Coral)
                        }
                    }
                    uiState.errorMessage != null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = uiState.errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    splits.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isOwedTab) "Nobody owes you anything for this plan." else "You don't owe anything for this plan.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF888888)
                            )
                        }
                    }
                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = if (isOwedTab) "They owe you the following" else "You owe the following",
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF888888)
                            )

                            if (!isOwedTab && uiState.saveErrorMessage != null) {
                                Text(
                                    text = uiState.saveErrorMessage!!,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(splits, key = { it.personId }) { split ->
                                    SplitCard(
                                        name = uiState.names[split.personId] ?: "Unknown",
                                        split = split,
                                        isOwedToMe = isOwedTab,
                                        daysOverdue = uiState.daysOverdue,
                                        enabled = !uiState.isSaving,
                                        onPaidChange = { paid -> viewModel.onPaidChange(planId, split, paid) },
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitTabRow(
    selectedTab: ManageSplitsViewModel.Tab,
    onTabSelected: (ManageSplitsViewModel.Tab) -> Unit
) {
    Column(modifier = Modifier.background(Color.White)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ManageSplitsViewModel.Tab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(tab) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (tab == ManageSplitsViewModel.Tab.I_OWE) "What I owe" else "Owed to you",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Coral else Color(0xFF888888)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (isSelected) Coral else Color.Transparent)
                    )
                }
            }
        }
        HorizontalDivider(color = Color(0xFFDDDDDD), thickness = 1.dp)
    }
}

@Composable
private fun SplitCard(
    name: String,
    split: Split,
    isOwedToMe: Boolean,
    daysOverdue: Int?,
    enabled: Boolean,
    onPaidChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val amount = if (isOwedToMe) split.amount - split.paidSoFar else split.amount
    val lateDays = daysOverdue?.takeIf { !split.paid }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isOwedToMe && split.paid) {
                    Text(
                        text = "Paid",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF888888)
                    )
                } else {
                    Text(
                        text = String.format(Locale.US, "$%.2f", amount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!isOwedToMe) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = split.paid,
                        onCheckedChange = onPaidChange,
                        enabled = enabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Coral,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFD0D3D9),
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }
            }
            if (lateDays != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .background(CoralLight, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = overdueLabel(isOwedToMe, lateDays),
                        style = MaterialTheme.typography.labelSmall,
                        color = Coral,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun overdueLabel(isOwedToMe: Boolean, days: Int): String {
    val dayText = if (days == 1) "1 day" else "$days days"
    return if (isOwedToMe) "Hasn't paid you · $dayText overdue" else "You haven't paid · $dayText overdue"
}
