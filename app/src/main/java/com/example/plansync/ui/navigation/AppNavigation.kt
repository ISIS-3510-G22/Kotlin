package com.example.plansync.ui.navigation

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
import com.example.plansync.ui.ActivityDetailScreen
import com.example.plansync.ui.AddFriendScreen
import com.example.plansync.ui.AddPaymentMethodScreen
import com.example.plansync.ui.AddToPlanScreen
import com.example.plansync.ui.CreateActivityScreen
import com.example.plansync.ui.CreatePlanScreen
import com.example.plansync.ui.CreateGroupScreen
import com.example.plansync.ui.EditProfileScreen
import com.example.plansync.ui.ExploreScreen
import com.example.plansync.ui.FriendRequestsScreen
import com.example.plansync.ui.GroupDetailScreen
import com.example.plansync.ui.GroupInvitesScreen
import com.example.plansync.ui.InviteToGroupScreen
import com.example.plansync.ui.InviteScreen
import com.example.plansync.ui.MyActivitiesScreen
import com.example.plansync.ui.MyCrewScreen
import com.example.plansync.ui.MyPlansScreen
import com.example.plansync.ui.PlanDetailScreen
import com.example.plansync.ui.ProfileScreen
import com.example.plansync.ui.UserDetailScreen



object Routes {
    const val EXPLORE     = "explore"
    const val MY_PLANS    = "my_plans"
    const val PLAN_DETAIL = "plan_detail/{planId}"
    const val INVITE      = "invite/{planId}"
    const val CREATE_PLAN = "create_plan?activityId={activityId}"
    fun createPlan(activityId: String? = null) =
        if (activityId != null) "create_plan?activityId=$activityId" else "create_plan"
    const val ACTIVITIES  = "activities"
    const val GROUPS      = "groups"
    const val PROFILE     = "profile"
    const val EDIT_PROFILE = "edit_profile"
    const val ADD_PAYMENT_METHOD = "add_payment_method"
    const val CREATE_ACTIVITY = "create_activity"
    const val ACTIVITY_DETAIL = "activity_detail/{activityId}"
    const val EDIT_ACTIVITY = "edit_activity/{activityId}"
    const val ADD_TO_PLAN = "add_to_plan/{activityId}"
    const val CREATE_GROUP = "create_group"
    const val GROUP_DETAIL = "group_detail/{groupId}"
    const val ADD_FRIEND = "add_friend"
    const val FRIEND_REQUESTS = "friend_requests"
    const val GROUP_INVITES = "group_invites"
    const val INVITE_TO_GROUP = "invite_to_group/{groupId}"
    const val USER_DETAIL = "user_detail/{userId}"

    fun planDetail(planId: String) = "plan_detail/$planId"
    fun invite(planId: String)     = "invite/$planId"
    fun activityDetail(activityId: String) = "activity_detail/$activityId"
    fun editActivity(activityId: String) = "edit_activity/$activityId"
    fun addToPlan(activityId: String) = "add_to_plan/$activityId"
    fun groupDetail(groupId: String) = "group_detail/$groupId"
    fun inviteToGroup(groupId: String) = "invite_to_group/$groupId"
    fun userDetail(userId: String) = "user_detail/$userId"
}



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

        composable(Routes.EXPLORE) {
            ExploreScreen(
                onPlanSelected = { planId -> navController.navigate(Routes.planDetail(planId)) },
                onProfileSelected = { navController.navigate(Routes.PROFILE) },
                onMyPlansSelected = { navController.navigate(Routes.MY_PLANS) },
                onMyActivitiesSelected = { navController.navigate(Routes.ACTIVITIES) },
                onMyCrewSelected = { navController.navigate(Routes.GROUPS) }
            )
        }

        composable(Routes.MY_PLANS) {
            MyPlansScreen(
                onExploreSelected = { navController.navigate(Routes.EXPLORE) },
                onProfileSelected = { navController.navigate(Routes.PROFILE) },
                onMyActivitiesSelected = { navController.navigate(Routes.ACTIVITIES) },
                onMyCrewSelected = { navController.navigate(Routes.GROUPS) },
                onPlanSelected = { planId -> navController.navigate(Routes.planDetail(planId)) },
                onCreatePlan = { navController.navigate(Routes.CREATE_PLAN) }
            )
        }

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

        composable(
            route = Routes.CREATE_PLAN,
            arguments = listOf(navArgument("activityId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val activityId = backStackEntry.arguments?.getString("activityId")
            CreatePlanScreen(
                preselectedActivityId = activityId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(Routes.ACTIVITIES) {
            MyActivitiesScreen(
                onExploreSelected = { navController.navigate(Routes.EXPLORE) },
                onMyPlansSelected = { navController.navigate(Routes.MY_PLANS) },
                onMyCrewSelected = { navController.navigate(Routes.GROUPS) },
                onProfileSelected = { navController.navigate(Routes.PROFILE) },
                onCreateActivity = { navController.navigate(Routes.CREATE_ACTIVITY) },
                onActivitySelected = { id -> navController.navigate(Routes.activityDetail(id)) }
            )
        }

        composable(
            route = Routes.ACTIVITY_DETAIL,
            arguments = listOf(navArgument("activityId") { type = NavType.StringType })
        ) { backStackEntry ->
            ActivityDetailScreen(
                activityId = backStackEntry.arguments?.getString("activityId").orEmpty(),
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Routes.editActivity(id)) },
                onAddToPlan = { id -> navController.navigate(Routes.addToPlan(id)) }
            )
        }

        composable(
            route = Routes.ADD_TO_PLAN,
            arguments = listOf(navArgument("activityId") { type = NavType.StringType })
        ) { backStackEntry ->
            AddToPlanScreen(
                activityId = backStackEntry.arguments?.getString("activityId").orEmpty(),
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onCreatePlan = { navController.navigate(Routes.CREATE_PLAN) }
            )
        }

        composable(
            route = Routes.EDIT_ACTIVITY,
            arguments = listOf(navArgument("activityId") { type = NavType.StringType })
        ) { backStackEntry ->
            CreateActivityScreen(
                activityId = backStackEntry.arguments?.getString("activityId"),
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(Routes.CREATE_ACTIVITY) {
            CreateActivityScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(Routes.GROUPS) {
            MyCrewScreen(
                onExploreSelected = { navController.navigate(Routes.EXPLORE) },
                onMyPlansSelected = { navController.navigate(Routes.MY_PLANS) },
                onMyActivitiesSelected = { navController.navigate(Routes.ACTIVITIES) },
                onProfileSelected = { navController.navigate(Routes.PROFILE) },
                onCreateGroup = { navController.navigate(Routes.CREATE_GROUP) },
                onGroupSelected = { id -> navController.navigate(Routes.groupDetail(id)) },
                onAddFriend = { navController.navigate(Routes.ADD_FRIEND) },
                onFriendRequests = { navController.navigate(Routes.FRIEND_REQUESTS) },
                onGroupInvites = { navController.navigate(Routes.GROUP_INVITES) },
                onFriendSelected = { id -> navController.navigate(Routes.userDetail(id)) }
            )
        }

        composable(Routes.ADD_FRIEND) {
            AddFriendScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.FRIEND_REQUESTS) {
            FriendRequestsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CREATE_GROUP) {
            CreateGroupScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.GROUP_DETAIL,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            GroupDetailScreen(
                groupId = backStackEntry.arguments?.getString("groupId").orEmpty(),
                onBack = { navController.popBackStack() },
                onInvite = { id -> navController.navigate(Routes.inviteToGroup(id)) }
            )
        }

        composable(
            route = Routes.INVITE_TO_GROUP,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            InviteToGroupScreen(
                groupId = backStackEntry.arguments?.getString("groupId").orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.GROUP_INVITES) {
            GroupInvitesScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.USER_DETAIL,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            UserDetailScreen(
                userId = backStackEntry.arguments?.getString("userId").orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                onLoggedOut = onLoggedOut,
                onExploreSelected = { navController.navigate(Routes.EXPLORE) },
                onPlanSelected = { planId -> navController.navigate(Routes.planDetail(planId)) },
                onMyPlansSelected = { navController.navigate(Routes.MY_PLANS) },
                onMyActivitiesSelected = { navController.navigate(Routes.ACTIVITIES) },
                onMyCrewSelected = { navController.navigate(Routes.GROUPS) },
                onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) }
            )
        }

        composable(Routes.EDIT_PROFILE) {
            EditProfileScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onAddMethod = { navController.navigate(Routes.ADD_PAYMENT_METHOD) }
            )
        }

        composable(Routes.ADD_PAYMENT_METHOD) {
            AddPaymentMethodScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
    }
}
