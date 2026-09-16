package com.example.plansync.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plansync.ui.theme.Coral
import com.example.plansync.ui.theme.CoralLight
import com.example.plansync.viewmodel.LoginViewModel

/**
 * UI layer — Composable screen for user login.
 *
 * Strictly follows MVVM: this Composable only reads state from [LoginViewModel]
 * and calls ViewModel functions in response to user actions. It never interacts
 * with the Repository or any data source directly.
 *
 * Observer pattern: [LoginViewModel.uiState] is collected with
 * [collectAsStateWithLifecycle], which is lifecycle-aware and automatically
 * cancels collection when the Composable leaves the active state.
 *
 * @param viewModel Injected via [viewModel] factory by default; can be overridden
 *                  in tests to pass a fake ViewModel.
 * @param onLoginSuccess Callback invoked when [LoginViewModel.UiState.loggedInUser]
 *                       is set — wired to navigation in a later milestone.
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = viewModel(),
    onLoginSuccess: () -> Unit = {}
) {
    // Collect the StateFlow as Compose state, scoped to the Composable's lifecycle
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Navigate away as soon as the ViewModel signals a successful login
    if (uiState.loggedInUser != null) {
        onLoginSuccess()
    }

    // Local UI-only state: whether the password characters are visible
    // This does not belong in the ViewModel because it has no business logic value
    var passwordVisible by remember { mutableStateOf(false) }

    // Soft coral background fills the whole screen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CoralLight),
        contentAlignment = Alignment.Center
    ) {
        // White card containing the entire login form
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ── Logo icon ──────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFEEEEEE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = "PlanSync logo",
                        modifier = Modifier.size(36.dp),
                        tint = Color(0xFF444444)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── App name ───────────────────────────────────────────────
                Text(
                    text = "PlanSync",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                // ── Subtitle ───────────────────────────────────────────────
                Text(
                    text = "Log in to continue organizing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF757575)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // ── Email field ────────────────────────────────────────────
                TextField(
                    value = uiState.email,
                    onValueChange = viewModel::onEmailChange,
                    label = { Text("Email address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Coral,
                        unfocusedIndicatorColor = Color(0xFFBBBBBB),
                        focusedLabelColor = Coral,
                        unfocusedLabelColor = Color(0xFF757575)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ── Password field ─────────────────────────────────────────
                TextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible)
                                    Icons.Default.Visibility
                                else
                                    Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible)
                                    "Hide password"
                                else
                                    "Show password",
                                tint = Color(0xFF757575)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Coral,
                        unfocusedIndicatorColor = Color(0xFFBBBBBB),
                        focusedLabelColor = Coral,
                        unfocusedLabelColor = Color(0xFF757575)
                    )
                )

                // ── Forgot password ────────────────────────────────────────
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = { /* TODO: forgot password flow */ }) {
                        Text(
                            text = "FORGOT PASSWORD?",
                            style = MaterialTheme.typography.labelSmall,
                            color = Coral,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Error message ──────────────────────────────────────────
                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                // ── Log In button ──────────────────────────────────────────
                Button(
                    onClick = viewModel::login,
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Coral,
                        contentColor = Color.White,
                        disabledContainerColor = Coral.copy(alpha = 0.6f),
                        disabledContentColor = Color.White
                    )
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Log In →",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Sign up row ────────────────────────────────────────────
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Don't have an account?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF444444)
                    )
                    TextButton(onClick = { /* TODO: navigate to sign-up screen */ }) {
                        Text(
                            text = "Sign Up",
                            color = Coral,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
