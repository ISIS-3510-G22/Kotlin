package com.example.plansync

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.plansync.ui.ForgotPasswordScreen
import com.example.plansync.ui.LoginScreen
import com.example.plansync.ui.QueuedActionsBanner
import com.example.plansync.ui.SignUpScreen
import com.example.plansync.ui.navigation.MainNavHost
import com.example.plansync.ui.theme.PlanSyncTheme
import com.example.plansync.data.ActivityDatabase
import com.example.plansync.data.DraftDatabase
import com.example.plansync.data.FirebaseProvider
import com.example.plansync.data.LocalStorageProvider
import com.example.plansync.service.OfflineSyncService
import com.example.plansync.viewmodel.SyncStatusViewModel
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings


/**
 * UI layer — single Activity, entry point of the application.
 *
 * Controls three phases:
 *  1. Pre-login: shows LoginScreen (no bottom nav, no NavHost)
 *  2. Sign-up: shows SignUpScreen (accessible from LoginScreen)
 *  3. Post-login: shows MainNavHost inside a Scaffold with BottomNavBar
 *
 * Jetpack Navigation (NavHostController) manages all post-login routing.
 * The Activity owns a [SyncStatusViewModel] for global offline queue state.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable Firestore offline persistence — must run before any other Firestore use.
        FirebaseProvider.firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder()
                    .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build()
            )
            .build()

        LocalStorageProvider.init(this)
        ActivityDatabase.init(this)
        DraftDatabase.init(this)
        OfflineSyncService.start(this, lifecycleScope)

        enableEdgeToEdge()
        setContent {
            PlanSyncTheme {

                var isLoggedIn by remember { mutableStateOf(FirebaseProvider.auth.currentUser != null) }
                var isSigningUp by remember { mutableStateOf(false) }
                var isResettingPassword by remember { mutableStateOf(false) }

                when {
                    isSigningUp -> SignUpScreen(
                        onProfileCreated = { isLoggedIn = true; isSigningUp = false },
                        onCancel = { isSigningUp = false }
                    )
                    !isLoggedIn && isResettingPassword -> ForgotPasswordScreen(
                        onBack = { isResettingPassword = false }
                    )
                    !isLoggedIn -> LoginScreen(
                        onLoginSuccess = { isLoggedIn = true },
                        onSignUpClick = { isSigningUp = true },
                        onForgotPasswordClick = { isResettingPassword = true }
                    )
                    else -> {
                        val navController = rememberNavController()
                        val notificationPermission = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission()
                        ) {}
                        LaunchedEffect(Unit) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        val syncVm: SyncStatusViewModel = viewModel()
                        val syncState by syncVm.uiState.collectAsStateWithLifecycle()
                        val snackbarState = remember { SnackbarHostState() }
                        LaunchedEffect(Unit) {
                            syncVm.queuedNotices.collect { message ->
                                snackbarState.currentSnackbarData?.dismiss()
                                snackbarState.showSnackbar(message, duration = SnackbarDuration.Short)
                            }
                        }
                        Scaffold(
                            topBar = {
                                QueuedActionsBanner(
                                    visible = syncState.showQueuedBanner,
                                    queuedCount = syncState.queuedCount
                                )
                            },
                            snackbarHost = {
                                SnackbarHost(snackbarState, Modifier.padding(bottom = 72.dp))
                            }
                        ) { innerPadding ->
                            MainNavHost(
                                navController = navController,
                                onLoggedOut = { isLoggedIn = false },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}
