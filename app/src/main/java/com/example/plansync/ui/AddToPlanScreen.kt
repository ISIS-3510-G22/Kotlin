package com.example.plansync.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plansync.model.Plan
import com.example.plansync.ui.theme.Coral
import com.example.plansync.viewmodel.AddToPlanViewModel

@Composable
fun AddToPlanScreen(
    activityId: String,
    viewModel: AddToPlanViewModel = viewModel(),
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {},
    onCreatePlan: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(activityId) {
        viewModel.load(activityId)
    }

    LaunchedEffect(uiState.didSave) {
        if (uiState.didSave) onSaved()
    }

    Scaffold(
        containerColor = Color(0xFFF5F5F5),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add to Plan",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        },
        bottomBar = {
            Button(
                onClick = viewModel::addToPlan,
                enabled = uiState.selectedPlanId != null && !uiState.isSaving,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(52.dp)
            ) {
                Text("Add to Plan", fontWeight = FontWeight.SemiBold)
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Coral)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            uiState.activity?.let { activity ->
                item { ActivityCard(activity = activity, onClick = {}) }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Choose a plan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            items(uiState.plans) { plan ->
                PlanOptionCard(
                    plan = plan,
                    isAdded = activityId in plan.activityIds,
                    isSelected = plan.id == uiState.selectedPlanId,
                    onClick = { viewModel.selectPlan(plan.id) }
                )
            }

            item { CreatePlanOption(onClick = onCreatePlan) }

            uiState.errorMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanOptionCard(plan: Plan, isAdded: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = !isAdded,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
            disabledContainerColor = Color.White
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Coral else Color(0xFFE5E5E5)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plan.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${plan.date} • ${plan.participants.size} People",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF888888)
                )
            }
            if (isAdded) {
                Text(
                    text = "Added",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF888888)
                )
            }
        }
    }
}

@Composable
private fun CreatePlanOption(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFF444444)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Coral,
                modifier = Modifier.size(48.dp)
            ) {}
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Create a new plan", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Start a plan for this activity",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF666666)
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
