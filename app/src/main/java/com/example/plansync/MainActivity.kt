package com.example.plansync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.plansync.ui.LoginScreen
import com.example.plansync.ui.theme.PlanSyncTheme

/**
 * UI layer — single Activity, entry point of the application.
 *
 * Hosts the Compose content tree inside PlanSyncTheme. Navigation between
 * screens will be added in milestone v0.2 (Jetpack Navigation component);
 * for now LoginScreen is the only screen.
 *
 * Following MVVM, this Activity does not create or hold any ViewModel directly —
 * that is handled inside each screen Composable via the viewModel() factory.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlanSyncTheme {
                LoginScreen()
            }
        }
    }
}