package com.example.plansync.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plansync.ui.theme.Coral
import com.example.plansync.viewmodel.ExpenseFormViewModel

@Composable
fun ExpenseFormScreen(
    planId: String,
    expenseId: String?,
    viewModel: ExpenseFormViewModel = viewModel(),
    onBack: () -> Unit = {},
    onDone: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(planId, expenseId) {
        viewModel.load(planId, expenseId)
    }

    LaunchedEffect(uiState.isDone) {
        if (uiState.isDone) onDone()
    }

    Scaffold(
        containerColor = Color(0xFFF8F8F8),
        topBar = {
            Column {
                BackTopBar(title = if (uiState.isEditing) "Edit Expense" else "New Expense", onBack = onBack)
                OfflineBanner(
                    isOnline = uiState.isOnline,
                    message = "You're offline. This expense will be saved on your phone and synced when you reconnect."
                )
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("Cancel", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Surface(
                    onClick = viewModel::save,
                    enabled = !uiState.isSaving && !uiState.isLoading,
                    shape = RoundedCornerShape(8.dp),
                    color = if (uiState.isSaving) Color.Gray else Coral
                ) {
                    Text(
                        text = when {
                            uiState.isSaving -> "Saving..."
                            uiState.isEditing -> "Save"
                            else -> "Create"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Coral)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            FormLabel("NAME")
            FormTextField(
                value = uiState.name,
                placeholder = "e.g Coffee",
                onValueChange = viewModel::onNameChange
            )

            FormLabel("VALUE")
            FormTextField(
                value = uiState.value,
                placeholder = "e.g 20",
                keyboardType = KeyboardType.Decimal,
                onValueChange = viewModel::onValueChange
            )

            FormLabel("WHO PAID")
            PaidByPicker(
                people = uiState.people,
                selectedId = uiState.paidById,
                onSelect = viewModel::onPaidByChange
            )

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            if (uiState.isEditing) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = viewModel::delete,
                    enabled = !uiState.isSaving,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935), contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete Expense", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun FormTextField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedBorderColor = Coral
        )
    )
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun PaidByPicker(
    people: List<ExpenseFormViewModel.Person>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = people.find { it.id == selectedId }?.name ?: "Choose who paid"

    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selectedName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color.White
        ) {
            people.forEach { person ->
                DropdownMenuItem(
                    text = {
                        Text(
                            person.name,
                            fontWeight = if (person.id == selectedId) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    trailingIcon = {
                        if (person.id == selectedId) Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF3B82F6))
                    },
                    onClick = {
                        onSelect(person.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
