package com.example.plansync.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plansync.model.ActivityVisibility
import com.example.plansync.ui.theme.Coral
import com.example.plansync.viewmodel.ActivityCategories
import com.example.plansync.viewmodel.CreateActivityViewModel

@Composable
fun CreateActivityScreen(
    activityId: String? = null,
    viewModel: CreateActivityViewModel = viewModel(),
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(activityId) {
        activityId?.let(viewModel::load)
    }

    LaunchedEffect(uiState.didSave) {
        if (uiState.didSave) onSaved()
    }

    Scaffold(
        containerColor = Color(0xFFF5F5F5),
        topBar = {
            CreateActivityTopBar(
                title = if (activityId == null) "Create Activity" else "Edit Activity",
                onBack = onBack
            )
        },
        bottomBar = {
            CreateActivityBottomBar(
                onCancel = onBack,
                onSave = viewModel::saveActivity
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
            item {
                FormSectionLabel("PLACE NAME")
                Spacer(Modifier.height(6.dp))
                FormTextField(
                    value = uiState.placeName,
                    onValueChange = viewModel::onPlaceNameChange,
                    placeholder = "e.g., Dumbo House"
                )
            }

            item {
                FormSectionLabel("ADDRESS")
                Spacer(Modifier.height(6.dp))
                FormTextField(
                    value = uiState.address,
                    onValueChange = viewModel::onAddressChange,
                    placeholder = "Address or location",
                    leadingIcon = Icons.Filled.LocationOn
                )
            }

            item {
                FormSectionLabel("EXPECTED PRICE")
                Spacer(Modifier.height(6.dp))
                FormTextField(
                    value = uiState.expectedPrice,
                    onValueChange = viewModel::onExpectedPriceChange,
                    placeholder = "e.g., 120,000"
                )
            }

            item {
                FormSectionLabel("TAGS")
                Spacer(Modifier.height(6.dp))
                CategoryChipGrid(
                    options = (ActivityCategories + uiState.tags).distinct(),
                    selected = uiState.tags,
                    onToggle = viewModel::onTagToggle
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        FormTextField(
                            value = uiState.customTag,
                            onValueChange = viewModel::onCustomTagChange,
                            placeholder = "Add your own tag"
                        )
                    }
                    IconButton(onClick = viewModel::addCustomTag) {
                        Icon(Icons.Filled.Add, contentDescription = "Add tag", tint = Coral)
                    }
                }
            }

            item {
                FormSectionLabel("NOTES")
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = viewModel::onNotesChange,
                    placeholder = { Text("Add notes for your group...", color = Color(0xFFAAAAAA)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Coral.copy(alpha = 0.5f),
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White
                    )
                )
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FormSectionLabel("VISIBILITY")
                    Spacer(Modifier.width(16.dp))
                    VisibilityToggle(
                        selected = uiState.visibility,
                        onSelect = viewModel::onVisibilitySelect
                    )
                }
            }

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

// Top bar

@Composable
private fun CreateActivityTopBar(title: String, onBack: () -> Unit) {
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
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

// Bottom bar

@Composable
private fun CreateActivityBottomBar(onCancel: () -> Unit, onSave: () -> Unit) {
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
            Text("Save Activity", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

// Form helpers

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
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color(0xFFAAAAAA)) },
        leadingIcon = leadingIcon?.let { icon ->
            { Icon(icon, contentDescription = null, tint = Color(0xFFAAAAAA)) }
        },
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

// Category chips

@Composable
private fun CategoryChipGrid(options: List<String>, selected: List<String>, onToggle: (String) -> Unit) {
    Column {
        options.chunked(3).forEach { rowCategories ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowCategories.forEach { category ->
                    CategoryChip(
                        label = category.replaceFirstChar { it.uppercase() },
                        isSelected = category in selected,
                        onClick = { onToggle(category) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CategoryChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isSelected) Coral else Color.White
    val textColor = if (isSelected) Color.White else Color(0xFF666666)
    val borderColor = if (isSelected) Coral else Color(0xFFDDDDDD)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

// Visibility toggle

@Composable
private fun VisibilityToggle(selected: ActivityVisibility, onSelect: (ActivityVisibility) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VisibilityOption(
            label = "Private",
            isSelected = selected == ActivityVisibility.PRIVATE,
            onClick = { onSelect(ActivityVisibility.PRIVATE) }
        )
        VisibilityOption(
            label = "Public",
            isSelected = selected == ActivityVisibility.PUBLIC,
            onClick = { onSelect(ActivityVisibility.PUBLIC) }
        )
    }
}

@Composable
private fun VisibilityOption(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isSelected) Coral else Color.White
    val textColor = if (isSelected) Color.White else Color(0xFF666666)
    val borderColor = if (isSelected) Coral else Color(0xFFDDDDDD)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}
