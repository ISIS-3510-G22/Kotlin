package com.example.plansync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plansync.ui.theme.Coral
import com.example.plansync.viewmodel.CreateGroupViewModel

@Composable
fun CreateGroupScreen(
    viewModel: CreateGroupViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.didSave) {
        if (uiState.didSave) onBack()
    }

    Scaffold(
        containerColor = Color(0xFFF5F5F5),
        topBar = { CrewTopBar(title = "Create Group", onBack = onBack) },
        bottomBar = {
            CreateGroupBottomBar(
                isSaving = uiState.isSaving,
                onCancel = onBack,
                onCreate = viewModel::createGroup
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            GroupFormLabel("GROUP NAME")
            Spacer(modifier = Modifier.height(6.dp))
            GroupFormField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                placeholder = "e.g., The Foosies"
            )

            Spacer(modifier = Modifier.height(16.dp))

            GroupFormLabel("DESCRIPTION")
            Spacer(modifier = Modifier.height(6.dp))
            GroupFormField(
                value = uiState.description,
                onValueChange = viewModel::onDescriptionChange,
                placeholder = "e.g. Friends from college"
            )

            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun GroupFormLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF888888),
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun GroupFormField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color(0xFFAAAAAA)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color(0xFFE0E0E0),
            focusedBorderColor = Coral.copy(alpha = 0.5f),
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White
        )
    )
}

@Composable
private fun CreateGroupBottomBar(isSaving: Boolean, onCancel: () -> Unit, onCreate: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onCancel) {
            Text("Cancel", color = Color(0xFF444444), fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = onCreate,
            enabled = !isSaving,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White),
            modifier = Modifier.height(48.dp)
        ) {
            Text("Create Group", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}
