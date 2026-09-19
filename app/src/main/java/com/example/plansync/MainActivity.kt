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
import com.example.plansync.ui.MyPlansScreen
import com.example.plansync.ui.PlanDetailScreen
import com.example.plansync.ui.theme.PlanSyncTheme
import com.example.plansync.ui.ExploreScreen
import com.example.plansync.ui.ProfileScreen
import com.example.plansync.ui.SignUpScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlanSyncTheme {
                
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
                        onMyPlansSelected = {currentScreen = Screen.MY_PLANS}
                    )
                    Screen.PLAN_DETAIL -> PlanDetailScreen(
                        planId = selectedPlanId,
                        onBack = { currentScreen = Screen.LOGIN }
                    )

                    Screen.PROFILE -> ProfileScreen(
                        onLoggedOut = {currentScreen = Screen.LOGIN},
                        onExploreSelected = { currentScreen = Screen.EXPLORE},
                        onPlanSelected = { planId ->
                            selectedPlanId = planId
                            currentScreen = Screen.PLAN_DETAIL
                        },
                        onMyPlansSelected = {currentScreen = Screen.MY_PLANS}
                    )

                    Screen.MY_PLANS -> MyPlansScreen(
                        onExploreSelected = {currentScreen = Screen.EXPLORE},
                        onProfileSelected = {currentScreen = Screen.PROFILE}
                    )
                }
            }
        }
    }
}

enum class Screen { LOGIN, EXPLORE, PLAN_DETAIL, PROFILE, SIGN_UP, MY_PLANS}