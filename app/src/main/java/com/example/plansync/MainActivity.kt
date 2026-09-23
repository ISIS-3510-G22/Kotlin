package com.example.plansync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.plansync.ui.LoginScreen
<<<<<<< Updated upstream
import com.example.plansync.ui.MyCrewScreen
import com.example.plansync.ui.MyPlansScreen
import com.example.plansync.ui.PlanDetailScreen
=======
import com.example.plansync.ui.navigation.BottomNavBar
import com.example.plansync.ui.navigation.MainNavHost
>>>>>>> Stashed changes
import com.example.plansync.ui.theme.PlanSyncTheme
import com.example.plansync.ui.ExploreScreen
import com.example.plansync.ui.ProfileScreen
import com.example.plansync.ui.SignUpScreen

<<<<<<< Updated upstream
=======
/**
 * UI layer — single Activity, entry point of the application.
 *
 * Controls two phases:
 *  1. Pre-login: shows LoginScreen (no bottom nav, no NavHost)
 *  2. Post-login: shows MainNavHost inside a Scaffold with BottomNavBar
 *
 * Jetpack Navigation (NavHostController) manages all post-login routing.
 * The Activity holds no ViewModel — each screen owns its own via viewModel().
 */
>>>>>>> Stashed changes
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlanSyncTheme {
<<<<<<< Updated upstream
                
                var currentScreen by remember { mutableStateOf(Screen.LOGIN) }
                var selectedPlanId by remember {mutableStateOf("plan-001")}

                when (currentScreen) {
                    Screen.LOGIN -> LoginScreen(
                        onLoginSuccess = { currentScreen = Screen.EXPLORE },
                        onSignUpClick = {currentScreen = Screen.SIGN_UP}
                    )

                    Screen.SIGN_UP -> SignUpScreen(
                        onProfileCreated = {currentScreen = Screen.EXPLORE},
                        onCancel = {currentScreen = Screen.LOGIN}
                        
                    )

                    Screen.EXPLORE -> ExploreScreen(
                        onPlanSelected = {planId ->
                            selectedPlanId = planId
                            currentScreen = Screen.PLAN_DETAIL
                        },
                        onProfileSelected = {currentScreen = Screen.PROFILE},
                        onMyPlansSelected = {currentScreen = Screen.MY_PLANS},
                        onMyCrewSelected = {currentScreen = Screen.MY_CREW}
                    )
                    Screen.PLAN_DETAIL -> PlanDetailScreen(
                        planId = selectedPlanId,
                        onBack = { currentScreen = Screen.EXPLORE }
                    )

                    Screen.PROFILE -> ProfileScreen(
                        onLoggedOut = {currentScreen = Screen.LOGIN},
                        onExploreSelected = { currentScreen = Screen.EXPLORE},
                        onPlanSelected = { planId ->
                            selectedPlanId = planId
                            currentScreen = Screen.PLAN_DETAIL
                        },
                        onMyPlansSelected = {currentScreen = Screen.MY_PLANS},
                        onMyCrewSelected = {currentScreen = Screen.MY_CREW}
                    )

                    Screen.MY_PLANS -> MyPlansScreen(
                        onExploreSelected = {currentScreen = Screen.EXPLORE},
                        onProfileSelected = {currentScreen = Screen.PROFILE},
                        onMyCrewSelected = {currentScreen = Screen.MY_CREW}
                    )

                    Screen.MY_CREW -> MyCrewScreen(
                        onExploreSelected = {currentScreen = Screen.EXPLORE},
                        onMyPlansSelected = {currentScreen = Screen.MY_PLANS},
                        onProfileSelected = {currentScreen = Screen.PROFILE}
=======

                // Tracks whether the user has authenticated in this session
                var isLoggedIn by remember { mutableStateOf(false) }

                if (!isLoggedIn) {
                    // Phase 1 — Login (no bottom nav)
                    LoginScreen(
                        onLoginSuccess = { isLoggedIn = true }
>>>>>>> Stashed changes
                    )
                } else {
                    // Phase 2 — Main app with Jetpack Navigation + bottom nav
                    val navController = rememberNavController()
                    Scaffold(
                        bottomBar = { BottomNavBar(navController = navController) }
                    ) { innerPadding ->
                        MainNavHost(
                            navController = navController,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
<<<<<<< Updated upstream
}

enum class Screen { LOGIN, EXPLORE, PLAN_DETAIL, PROFILE, SIGN_UP, MY_PLANS, MY_CREW}
=======
}
>>>>>>> Stashed changes
