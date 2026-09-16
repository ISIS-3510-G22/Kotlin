package com.example.plansync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.plansync.ui.LoginScreen
import com.example.plansync.ui.PlanDetailScreen
import com.example.plansync.ui.theme.PlanSyncTheme

/**
 * UI layer — single Activity, entry point of the application.
 *
 * Manages which screen is currently visible using a simple in-memory
 * state variable. This is intentionally lightweight for milestone v0.2;
 * Jetpack Navigation (NavHost + NavController) will replace this in v0.3,
 * enabling deep links, back-stack management, and animated transitions.
 *
 * The Activity holds no ViewModel — each screen Composable owns its own
 * ViewModel via the viewModel() factory, keeping this class thin.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlanSyncTheme {

                // Tracks which screen is shown; starts on Login
                var currentScreen by remember { mutableStateOf(Screen.LOGIN) }

                when (currentScreen) {
                    Screen.LOGIN -> LoginScreen(
                        onLoginSuccess = { currentScreen = Screen.PLAN_DETAIL }
                    )
                    Screen.PLAN_DETAIL -> PlanDetailScreen(
                        planId = "plan-001",
                        onBack = { currentScreen = Screen.LOGIN }
                    )
                }
            }
        }
    }
}

/**
 * UI layer — represents the set of top-level screens in the app.
 * Will be replaced by a NavGraph destination in milestone v0.3.
 */
enum class Screen { LOGIN, PLAN_DETAIL }