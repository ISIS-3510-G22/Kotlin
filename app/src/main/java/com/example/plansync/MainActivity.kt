package com.example.plansync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.plansync.ui.LoginScreen
import com.example.plansync.ui.SignUpScreen
import com.example.plansync.ui.navigation.BottomNavBar
import com.example.plansync.ui.navigation.MainNavHost
import com.example.plansync.ui.navigation.Routes
import com.example.plansync.ui.theme.Coral
import com.example.plansync.ui.theme.PlanSyncTheme

/**
 * UI layer — single Activity, entry point of the application.
 *
 * Controls three phases:
 *  1. Pre-login: shows LoginScreen (no bottom nav, no NavHost)
 *  2. Sign-up: shows SignUpScreen (accessible from LoginScreen)
 *  3. Post-login: shows MainNavHost inside a Scaffold with BottomNavBar
 *
 * Jetpack Navigation (NavHostController) manages all post-login routing.
 * The Activity holds no ViewModel — each screen owns its own via viewModel().
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlanSyncTheme {

                var isLoggedIn by remember { mutableStateOf(false) }
                var isSigningUp by remember { mutableStateOf(false) }

                when {
                    isSigningUp -> SignUpScreen(
                        onProfileCreated = { isLoggedIn = true; isSigningUp = false },
                        onCancel = { isSigningUp = false }
                    )
                    !isLoggedIn -> LoginScreen(
                        onLoginSuccess = { isLoggedIn = true },
                        onSignUpClick = { isSigningUp = true }
                    )
                    else -> {
                        // Phase 3 — Main app with Jetpack Navigation + bottom nav
                        val navController = rememberNavController()
                        val currentRoute = navController
                            .currentBackStackEntryAsState().value
                            ?.destination?.route

                        Scaffold(
                            bottomBar = { BottomNavBar(navController = navController) },
                            floatingActionButton = {
                                if (currentRoute == Routes.MY_PLANS) {
                                    FloatingActionButton(
                                        onClick = { navController.navigate(Routes.CREATE_PLAN) },
                                        containerColor = Coral
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "Create Plan",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
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
