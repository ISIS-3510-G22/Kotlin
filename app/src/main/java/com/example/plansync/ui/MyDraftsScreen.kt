package com.example.plansync.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plansync.ui.theme.Coral
import com.example.plansync.ui.theme.CoralLight
import com.example.plansync.viewmodel.MyDraftsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MyDraftsScreen(
    onBack: () -> Unit = {},
    onResume: (draftId: String) -> Unit = {},
    viewModel: MyDraftsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Coral, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text("My Drafts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        OfflineBanner(isOnline = uiState.isOnline, message = "You're offline — showing cached drafts")

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Coral)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

                    uiState.topAbandonmentStep?.let { step ->
                        item { AbandonmentInsightCard(step) }
                    }

                    if (uiState.drafts.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No drafts yet.", color = Color(0xFF888888), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        items(uiState.drafts, key = { it.id }) { draft ->
                            DraftCard(
                                draft = draft,
                                onResume = { onResume(draft.id) },
                                onDiscard = { viewModel.discard(draft.id) }
                            )
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AbandonmentInsightCard(step: Int) {
    val stepLabel = when (step) {
        1 -> "entering a plan name"
        2 -> "selecting a date"
        3 -> "adding activities"
        else -> "step $step"
    }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Coral, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("INSIGHT", style = MaterialTheme.typography.labelSmall, color = Coral, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(2.dp))
                Text("Most plan flows are abandoned after $stepLabel", style = MaterialTheme.typography.bodySmall, color = Color(0xFF555555))
            }
        }
    }
}

@Composable
private fun DraftCard(
    draft: MyDraftsViewModel.DraftItem,
    onResume: () -> Unit,
    onDiscard: () -> Unit
) {
    val stepLabel = when (draft.lastStep) {
        1 -> "Named plan"
        2 -> "Date selected"
        3 -> "Activities added"
        else -> "Just started"
    }
    val dateStr = remember(draft.updatedAt) {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(draft.updatedAt))
    }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = draft.planName.ifBlank { "Untitled draft" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(CoralLight, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(stepLabel, style = MaterialTheme.typography.labelSmall, color = Coral, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                Text(dateStr, style = MaterialTheme.typography.bodySmall, color = Color(0xFF888888))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onResume,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Resume", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onDiscard,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFCCCCCC)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF888888)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Discard")
                }
            }
        }
    }
}
