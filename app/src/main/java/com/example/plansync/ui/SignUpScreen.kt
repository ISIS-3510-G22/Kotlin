package com.example.plansync.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plansync.ui.theme.Coral
import com.example.plansync.ui.theme.CoralLight
import com.example.plansync.viewmodel.SignUpViewModel

@Composable
fun SignUpScreen(
    viewModel: SignUpViewModel = viewModel(),
    onProfileCreated: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.createdUser) {
        if (uiState.createdUser != null) {
            onProfileCreated()
            viewModel.onProfileCreatedHandled()
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onCancel,
                    shape = CircleShape,
                    color = CoralLight,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Cancel",
                            tint = Coral
                        )
                    }
                }
                Text(
                    text = "Create Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 40.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                TextButton(onClick = onCancel) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    onClick = viewModel::createProfile,
                    shape = RoundedCornerShape(50),
                    color = Coral
                ) {
                    Text(
                        text = "Create",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            SignUpField(
                label = "NAME",
                placeholder = "e.g., Tomas",
                value = uiState.name,
                onValueChange = viewModel::onNameChange
            )
            SignUpField(
                label = "LAST NAME",
                placeholder = "e.g., Sierra",
                value = uiState.lastName,
                onValueChange = viewModel::onLastNameChange
            )
            SignUpField(
                label = "USERNAME",
                placeholder = "e.g., Tom1281",
                value = uiState.username,
                onValueChange = viewModel::onUsernameChange
            )
            SignUpField(
                label = "PHONE",
                placeholder = "e.g., 310xxxyyyy",
                value = uiState.phone,
                onValueChange = viewModel::onPhoneChange,
                keyboardType = KeyboardType.Phone
            )
            SignUpField(
                label = "EMAIL ADDRESS",
                placeholder = "@gmail.com, @yahoo.com, etc",
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                keyboardType = KeyboardType.Email
            )
            SignUpField(
                label = "PASSWORD",
                placeholder = "",
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                visualTransformation = PasswordVisualTransformation()
            )

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SignUpField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
    )
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary
        )
    )
}
