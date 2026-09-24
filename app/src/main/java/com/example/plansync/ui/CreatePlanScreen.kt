package com.example.plansync.ui

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plansync.ui.theme.Coral
import com.example.plansync.viewmodel.CreatePlanViewModel

/**
 * UI layer — Composable screen for creating a new Plan.
 *
 * Strictly follows MVVM: reads state only from [CreatePlanViewModel] via
 * collectAsStateWithLifecycle() and calls only ViewModel functions.
 *
 * @param viewModel Injected via viewModel() factory.
 * @param onBack    Callback for the back arrow and Cancel button.
 * @param onSaved   Callback triggered once the plan is saved.
 */
@Composable
fun CreatePlanScreen(
    viewModel: CreatePlanViewModel = viewModel(),
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onSaved()
    }

    Scaffold(
        containerColor = Color(0xFFF5F5F5),
        topBar = { CreatePlanTopBar(onBack = onBack) },
        bottomBar = {
            CreatePlanBottomBar(
                onCancel = onBack,
                onSave = { viewModel.onSavePlan() }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Plan name ───────────────────────────────────────────────────
            item {
                FormSectionLabel("PLAN NAME")
                Spacer(Modifier.height(6.dp))
                FormTextField(
                    value = uiState.planName,
                    onValueChange = viewModel::onPlanNameChange,
                    placeholder = "e.g. Saturday in Brooklyn"
                )
            }

            // ── Date + meetup time ──────────────────────────────────────────
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        FormSectionLabel("DATE")
                        Spacer(Modifier.height(6.dp))
                        FormTextField(
                            value = uiState.date,
                            onValueChange = viewModel::onDateChange,
                            placeholder = "MM/DD/YYYY"
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        FormSectionLabel("MEETUP TIME")
                        Spacer(Modifier.height(6.dp))
                        FormTextField(
                            value = uiState.meetupTime,
                            onValueChange = viewModel::onMeetupTimeChange,
                            placeholder = "10:30 AM"
                        )
                    }
                }
            }

            // ── Activities header ───────────────────────────────────────────
            item {
                FormSectionLabel("ACTIVITIES")
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = Color(0xFFEEEEEE))
            }

            // ── Activity rows ───────────────────────────────────────────────
            items(uiState.activities) { activity ->
                DraftActivityRow(activity = activity)
                HorizontalDivider(color = Color(0xFFF0F0F0))
            }

            // ── Add activity button ─────────────────────────────────────────
            item {
                OutlinedButton(
                    onClick = { /* TODO: navigate to add activity */ },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Coral),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral)
                ) {
                    Text("+ Add Activity", fontWeight = FontWeight.SemiBold)
                }
            }

            // ── Total estimated cost ────────────────────────────────────────
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text(
                            text = "Total Estimated Cost",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$${String.format("%.2f", uiState.totalEstimatedCost)}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ── Top bar ────────────────────────────────────────────────────────────────────

@Composable
private fun CreatePlanTopBar(onBack: () -> Unit) {
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
                Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Create Plan",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Bottom bar ─────────────────────────────────────────────────────────────────

@Composable
private fun CreatePlanBottomBar(onCancel: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onCancel) {
            Text("Cancel", color = Color(0xFF888888), fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = onSave,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = Coral,
                contentColor = Color.White
            ),
            modifier = Modifier.height(48.dp)
        ) {
            Text("Save Plan", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

// ── Form helpers ───────────────────────────────────────────────────────────────

@Composable
private fun FormSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF888888),
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color(0xFFAAAAAA)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = Coral.copy(alpha = 0.5f),
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White
        )
    )
}

// ── Draft activity row ─────────────────────────────────────────────────────────

@Composable
private fun DraftActivityRow(activity: CreatePlanViewModel.DraftActivity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(Coral, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activity.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = activity.address,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF888888)
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color(0xFFCCCCCC),
            modifier = Modifier.size(20.dp)
        )
    }
}
