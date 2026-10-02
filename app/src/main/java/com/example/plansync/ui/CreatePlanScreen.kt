package com.example.plansync.ui

import android.Manifest
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plansync.model.Activity
import com.example.plansync.ui.theme.Coral
import com.example.plansync.viewmodel.CreatePlanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * UI layer — Composable screen for creating a new Plan.
 *
 * Strictly follows MVVM: reads state only from [CreatePlanViewModel] via
 * collectAsStateWithLifecycle() and calls only ViewModel functions.
 *
 * Features:
 *  - Sensor: GPS location detection fills the meetup address field.
 *  - Smart: live cost-per-person derived from selected activities + participant count.
 *  - Validation: name and date required before saving.
 *  - Saves to Firestore via PlanRepository.createPlan().
 *
 * @param preselectedActivityId Optional activity id passed from "Add to Plan" flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlanScreen(
    viewModel: CreatePlanViewModel = viewModel(),
    preselectedActivityId: String? = null,
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Pre-select activity if navigated from "Add to Plan"
    LaunchedEffect(preselectedActivityId) {
        if (preselectedActivityId != null) viewModel.preSelectActivity(preselectedActivityId)
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onSaved()
    }

    // ── Sensor: GPS location permission + resolution ──────────────────────────
    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch(Dispatchers.IO) {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                @Suppress("MissingPermission")
                val loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val address = if (loc != null) {
                    @Suppress("DEPRECATION")
                    Geocoder(context, Locale.getDefault())
                        .getFromLocation(loc.latitude, loc.longitude, 1)
                        ?.firstOrNull()?.getAddressLine(0)
                        ?: "${String.format("%.4f", loc.latitude)}, ${String.format("%.4f", loc.longitude)}"
                } else "Unable to detect location"
                withContext(Dispatchers.Main) { viewModel.onLocationDetected(address) }
            }
        }
    }

    // ── Date picker dialog ────────────────────────────────────────────────────
    if (uiState.showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.selectedDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { viewModel.onHideDatePicker() },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.onDateSelected(it) }
                        ?: viewModel.onHideDatePicker()
                }) { Text("OK", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onHideDatePicker() }) {
                    Text("Cancel", color = Color(0xFF888888))
                }
            }
        ) { DatePicker(state = datePickerState) }
    }

    // ── Time picker dialog ────────────────────────────────────────────────────
    if (uiState.showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = uiState.selectedHour,
            initialMinute = uiState.selectedMinute
        )
        AlertDialog(
            onDismissRequest = { viewModel.onHideTimePicker() },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onTimeSelected(timePickerState.hour, timePickerState.minute)
                }) { Text("OK", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onHideTimePicker() }) {
                    Text("Cancel", color = Color(0xFF888888))
                }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }

    // ── Activity picker dialog ────────────────────────────────────────────────
    if (uiState.showActivityPicker) {
        ActivityPickerDialog(
            available = uiState.availableActivities,
            selected = uiState.selectedActivities,
            onToggle = viewModel::onActivityToggled,
            onDismiss = viewModel::onHideActivityPicker
        )
    }

    // ── Error dialog ──────────────────────────────────────────────────────────
    if (uiState.errorMessage != null) {
        AlertDialog(
            onDismissRequest = viewModel::onErrorDismissed,
            confirmButton = {
                TextButton(onClick = viewModel::onErrorDismissed) { Text("OK", color = Coral) }
            },
            title = { Text("Oops") },
            text  = { Text(uiState.errorMessage!!) }
        )
    }

    Scaffold(
        containerColor = Color(0xFFF5F5F5),
        topBar = { CreatePlanTopBar(onBack = onBack) },
        bottomBar = {
            CreatePlanBottomBar(
                isLoading = uiState.isLoading,
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

            // ── Plan name ────────────────────────────────────────────────────
            item {
                FormSectionLabel("PLAN NAME")
                Spacer(Modifier.height(6.dp))
                FormTextField(
                    value = uiState.planName,
                    onValueChange = viewModel::onPlanNameChange,
                    placeholder = "e.g. Saturday in Brooklyn"
                )
            }

            // ── Date + time ──────────────────────────────────────────────────
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        FormSectionLabel("DATE")
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { viewModel.onShowDatePicker() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, Color.Transparent
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White,
                                contentColor = if (uiState.formattedDate.isNotEmpty())
                                    Color(0xFF222222) else Color(0xFFAAAAAA)
                            )
                        ) {
                            Text(
                                text = uiState.formattedDate.ifEmpty { "MM/DD/YYYY" },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        FormSectionLabel("MEETUP TIME")
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { viewModel.onShowTimePicker() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, Color.Transparent
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF222222)
                            )
                        ) {
                            Text(
                                text = uiState.formattedTime,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // ── Meetup location (GPS sensor) ─────────────────────────────────
            item {
                FormSectionLabel("MEETUP LOCATION")
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.locationText,
                        onValueChange = viewModel::onLocationDetected,
                        placeholder = { Text("e.g. 55 Water St, Brooklyn", color = Color(0xFFAAAAAA)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Coral.copy(alpha = 0.5f),
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White
                        )
                    )
                    Button(
                        onClick = { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Coral,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Detect", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            // ── Participants (smart: cost per person) ────────────────────────
            item {
                FormSectionLabel("PARTICIPANTS")
                Spacer(Modifier.height(6.dp))
                FormTextField(
                    value = uiState.participantCount.toString(),
                    onValueChange = { viewModel.onParticipantCountChange(it.filter(Char::isDigit).toIntOrNull() ?: 1) },
                    placeholder = "Number of people",
                    keyboardType = KeyboardType.Number
                )
            }

            // ── Visibility toggle ────────────────────────────────────────────
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Public Plan", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (uiState.isPublic) "Visible to everyone" else "Only visible to participants",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF888888)
                            )
                        }
                        Switch(
                            checked = uiState.isPublic,
                            onCheckedChange = viewModel::onIsPublicChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Coral,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFDDDDDD)
                            )
                        )
                    }
                }
            }

            // ── Activities header ────────────────────────────────────────────
            item {
                FormSectionLabel("ACTIVITIES")
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = Color(0xFFEEEEEE))
            }

            // ── Selected activities list ─────────────────────────────────────
            if (uiState.selectedActivities.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No activities added yet.", color = Color(0xFF888888), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                itemsIndexed(uiState.selectedActivities) { index, activity ->
                    SelectedActivityRow(activity = activity)
                    if (index < uiState.selectedActivities.lastIndex)
                        HorizontalDivider(color = Color(0xFFF0F0F0))
                }
            }

            // ── Add activity button ──────────────────────────────────────────
            item {
                OutlinedButton(
                    onClick = { viewModel.onShowActivityPicker() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Coral),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral)
                ) {
                    Text("+ Add Activity", fontWeight = FontWeight.SemiBold)
                }
            }

            // ── Cost card ────────────────────────────────────────────────────
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text("Estimated Cost", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$${String.format("%.2f", uiState.totalEstimatedCost)} total",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        // Smart feature: updates live as activities or participant count changes
                        Text(
                            text = "$${String.format("%.2f", uiState.costPerPerson)} per person · " +
                                    "${uiState.participantCount} ${if (uiState.participantCount == 1) "person" else "people"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF888888)
                        )
                    }
                }
            }
        }
    }
}

// ── Dialogs ────────────────────────────────────────────────────────────────────

@Composable
private fun ActivityPickerDialog(
    available: List<Activity>,
    selected: List<Activity>,
    onToggle: (Activity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = Coral) }
        },
        title = { Text("Add Activities", fontWeight = FontWeight.Bold) },
        text = {
            if (available.isEmpty()) {
                Text("No activities found. Create some in My Activities first.", color = Color(0xFF888888))
            } else {
                Column {
                    available.forEach { activity ->
                        val isSelected = selected.any { it.id == activity.id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(activity) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggle(activity) },
                                colors = CheckboxDefaults.colors(checkedColor = Coral)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(activity.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${activity.category} · $${activity.price}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF888888)
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Coral, modifier = Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider(color = Color(0xFFF5F5F5))
                    }
                }
            }
        }
    )
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
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text("Create Plan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

// ── Bottom bar ─────────────────────────────────────────────────────────────────

@Composable
private fun CreatePlanBottomBar(isLoading: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
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
            enabled = !isLoading,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White),
            modifier = Modifier.height(48.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Save Plan", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
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
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color(0xFFAAAAAA)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = Coral.copy(alpha = 0.5f),
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White
        )
    )
}

// ── Selected activity row ──────────────────────────────────────────────────────

@Composable
private fun SelectedActivityRow(activity: Activity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).background(Coral, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(activity.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(activity.address, style = MaterialTheme.typography.bodySmall, color = Color(0xFF888888))
        }
        Text(
            text = "$${activity.price}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Coral
        )
    }
}

