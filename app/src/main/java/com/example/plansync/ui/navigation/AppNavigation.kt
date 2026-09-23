package com.example.plansync.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.plansync.ui.ExploreScreen
import com.example.plansync.ui.InviteScreen
import com.example.plansync.ui.MyCrewScreen
import com.example.plansync.ui.MyPlansScreen
import com.example.plansync.ui.PlanDetailScreen
import com.example.plansync.ui.ProfileScreen

/**
 * UI layer — Navigation.
 *
 * Defines all app routes and hosts the NavHost. Replaces the manual
 * Screen enum used in milestones v0.1–v0.2.
 *
 * Route structure:
 *  login            → handled in MainActivity before NavHost
 *  sign_up          → handled in MainActivity before NavHost
 *  explore          → ExploreScreen
 *  my_plans         → MyPlansScreen
 *  plan_detail/{id} → PlanDetailScreen with specific id
 *  invite/{planId}  → InviteScreen
 *  activities       → stub
 *  groups           → MyCrewScreen
 *  profile          → ProfileScreen
 */

// ── Route constants ────────────────────────────────────────────────────────────

object Routes {
    const val EXPLORE     = "explore"
    const val MY_PLANS    = "my_plans"
    const val PLAN_DETAIL = "plan_detail/{planId}"
    const val INVITE      = "invite/{planId}"
    const val ACTIVITIES  = "activities"
    const val GROUPS      = "groups"
    const val PROFILE     = "profile"

    fun planDetail(planId: String) = "plan_detail/$planId"
    fun invite(planId: String)     = "invite/$planId"
}

// ── Bottom nav items ───────────────────────────────────────────────────────────

/**
 * UI layer — defines each tab in the bottom navigation bar.
 * [route] maps to the NavHost destination for that tab.
 */
enum class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
) {
    EXPLORE(Routes.EXPLORE,       Icons.Default.Explore,       "Explore"),
    MY_PLANS(Routes.MY_PLANS,     Icons.Default.CalendarToday, "My Plans"),
    ACTIVITIES(Routes.ACTIVITIES, Icons.Default.List,          "Activities"),
    GROUPS(Routes.GROUPS,         Icons.Default.Group,         "Groups"),
    PROFILE(Routes.PROFILE,       Icons.Default.Person,        "Profile")
}

// ── Bottom navigation bar ──────────────────────────────────────────────────────

/**
 * UI layer — shared bottom navigation bar rendered inside the main Scaffold.
 * Highlights the item whose route matches the current back-stack destination.
 */
@Composable
fun BottomNavBar(navController: NavController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar(containerColor = Color.White) {
        BottomNavItem.entries.forEach { item ->
            val selected = currentRoute == item.route ||
                    (item == BottomNavItem.MY_PLANS &&
                            (currentRoute?.startsWith("plan_detail") == true ||
                                    currentRoute?.startsWith("invite") == true))

            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = {
                    Text(
                        text = item.label,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    selectedTextColor = Color.Black,
                    indicatorColor = Color(0xFFF0F0F0),
                    unselectedIconColor = Color(0xFF888888),
                    unselectedTextColor = Color(0xFF888888)
                )
            )
        }
    }
}

// ── Nav host ───────────────────────────────────────────────────────────────────

/**
 * UI layer — hosts the main app navigation graph (post-login).
 * Login and sign-up are handled separately in MainActivity before this NavHost.
 *
 * [onLoggedOut] is passed in from MainActivity so ProfileScreen can reset
 * the login state that lives outside the NavHost.
 */
@Composable
fun MainNavHost(
    navController: NavHostController,
    onLoggedOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.EXPLORE,
        modifier = modifier
    ) {
        // Explore tab
        composable(Routes.EXPLORE) {
            ExploreScreen(
                onPlanSelected = { planId -> navController.navigate(Routes.planDetail(planId)) },
                onProfileSelected = { navController.navigate(Routes.PROFILE) },
                onMyPlansSelected = { navController.navigate(Routes.MY_PLANS) },
                onMyCrewSelected = { navController.navigate(Routes.GROUPS) }
            )
        }

        // My Plans tab
        composable(Routes.MY_PLANS) {
            MyPlansScreen(
                onExploreSelected = { navController.navigate(Routes.EXPLORE) },
                onProfileSelected = { navController.navigate(Routes.PROFILE) },
                onMyCrewSelected = { navController.navigate(Routes.GROUPS) }
            )
        }

        // Plan detail with dynamic planId
        composable(
            route = Routes.PLAN_DETAIL,
            arguments = listOf(navArgument("planId") { type = NavType.StringType })
        ) { backStackEntry ->
            val planId = backStackEntry.arguments?.getString("planId") ?: "plan-001"
            PlanDetailScreen(
                planId = planId,
                onBack = { navController.popBackStack() },
                onInvite = { id -> navController.navigate(Routes.invite(id)) }
            )
        }

        // Invite screen
        composable(
            route = Routes.INVITE,
            arguments = listOf(navArgument("planId") { type = NavType.StringType })
        ) { backStackEntry ->
            val planId = backStackEntry.arguments?.getString("planId") ?: "plan-001"
            InviteScreen(
                planId = planId,
                onBack = { navController.popBackStack() }
            )
        }

        // Activities tab — stub (future milestone)
        composable(Routes.ACTIVITIES) { StubScreen("Activities") }

        // Groups tab — MyCrewScreen
        composable(Routes.GROUPS) {
            MyCrewScreen(
                onExploreSelected = { navController.navigate(Routes.EXPLORE) },
                onMyPlansSelected = { navController.navigate(Routes.MY_PLANS) },
                onProfileSelected = { navController.navigate(Routes.PROFILE) }
            )
        }

        // Profile tab
        composable(Routes.PROFILE) {
            ProfileScreen(
                onLoggedOut = onLoggedOut,
                onExploreSelected = { navController.navigate(Routes.EXPLORE) },
                onPlanSelected = { planId -> navController.navigate(Routes.planDetail(planId)) },
                onMyPlansSelected = { navController.navigate(Routes.MY_PLANS) },
                onMyCrewSelected = { navController.navigate(Routes.GROUPS) }
            )
        }
    }
}

// Temporary placeholder for unimplemented tabs
@Composable
private fun StubScreen(name: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "$name — coming soon", color = Color(0xFF888888))
    }
}
