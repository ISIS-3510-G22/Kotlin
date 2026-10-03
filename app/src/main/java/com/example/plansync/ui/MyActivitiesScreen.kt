package com.example.plansync.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plansync.model.Activity
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.plansync.ui.theme.Coral
import com.example.plansync.viewmodel.ActivityTab
import com.example.plansync.viewmodel.MyActivitiesViewModel

@Composable
fun MyActivitiesScreen(
    viewModel: MyActivitiesViewModel = viewModel(),
    onExploreSelected: () -> Unit = {},
    onMyPlansSelected: () -> Unit = {},
    onMyCrewSelected: () -> Unit = {},
    onProfileSelected: () -> Unit = {},
    onCreateActivity: () -> Unit = {},
    onActivitySelected: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.onLocationPermissionResult() }

    LaunchedEffect(Unit) {
        viewModel.loadActivities()
        locationLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    Scaffold(
        bottomBar = {
            MyActivitiesBottomBar(
                onExploreSelected = onExploreSelected,
                onMyPlansSelected = onMyPlansSelected,
                onMyCrewSelected = onMyCrewSelected,
                onProfileSelected = onProfileSelected
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateActivity,
                containerColor = Coral,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create activity")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(innerPadding)
        ) {
            MyActivitiesTopBar()

            MyActivitiesTabRow(
                selectedTab = uiState.selectedTab,
                onTabSelected = viewModel::selectTab
            )

            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Coral)
                    }
                }
                uiState.errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                uiState.filteredActivities.isEmpty() -> {
                    EmptyActivitiesMessage(tab = uiState.selectedTab)
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(uiState.filteredActivities) { activity ->
                            ActivityCard(
                                activity = activity,
                                distanceKm = uiState.distancesKm[activity.id],
                                onClick = { onActivitySelected(activity.id) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Top

@Composable
private fun MyActivitiesTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "My Activities",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

// Tabs

@Composable
private fun MyActivitiesTabRow(selectedTab: ActivityTab, onTabSelected: (ActivityTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            ActivityTab.FOR_YOU to "For You",
            ActivityTab.LIKED to "Liked",
            ActivityTab.PRIVATE to "Private"
        ).forEach { (tab, label) ->
            ActivityTabPill(
                label = label,
                isSelected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ActivityTabPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) Coral else Color.White
    val textColor = if (isSelected) Color.White else Color(0xFF888888)
    val borderColor = if (isSelected) Coral else Color(0xFFDDDDDD)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

// Empty state

private fun emptyStateMessage(tab: ActivityTab): String = when (tab) {
    ActivityTab.FOR_YOU -> "No public activities yet."
    ActivityTab.LIKED -> "No liked activities yet."
    ActivityTab.PRIVATE -> "No private activities yet."
}

@Composable
private fun EmptyActivitiesMessage(tab: ActivityTab) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = emptyStateMessage(tab),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF888888)
        )
    }
}

// Activity card

@Composable
fun ActivityCard(
    activity: Activity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    distanceKm: Float? = null
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (activity.photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = activity.photoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Sell,
                        contentDescription = null,
                        tint = Coral,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = activity.tags.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Coral
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = activity.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = activity.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF888888)
                )
                distanceKm?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "%.1f km away".format(it),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Coral
                    )
                }
            }
        }
    }
}

// NavBar

private data class MyActivitiesNavItem(
    val label: String,
    val icon: ImageVector
)

private val myActivitiesNavItems = listOf(
    MyActivitiesNavItem("Explore", Icons.Filled.Explore),
    MyActivitiesNavItem("My Plans", Icons.Filled.CalendarMonth),
    MyActivitiesNavItem("My Activities", Icons.Filled.Checklist),
    MyActivitiesNavItem("My Crew", Icons.Filled.Groups),
    MyActivitiesNavItem("My Profile", Icons.Filled.Person)
)

@Composable
private fun MyActivitiesBottomBar(
    onExploreSelected: () -> Unit,
    onMyPlansSelected: () -> Unit,
    onMyCrewSelected: () -> Unit,
    onProfileSelected: () -> Unit
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        myActivitiesNavItems.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = index == 2,
                onClick = {
                    when (index) {
                        0 -> onExploreSelected()
                        1 -> onMyPlansSelected()
                        3 -> onMyCrewSelected()
                        4 -> onProfileSelected()
                        else -> {}
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.secondary
                )
            )
        }
    }
}
