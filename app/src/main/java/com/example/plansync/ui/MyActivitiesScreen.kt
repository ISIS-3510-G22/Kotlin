package com.example.plansync.ui

import android.text.format.DateUtils
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plansync.R
import com.example.plansync.model.Activity
import com.example.plansync.model.ActivityVisibility
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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

            OfflineBanner(
                isOnline = uiState.isOnline,
                message = offlineMessage(uiState.lastUpdated)
            )

            MyActivitiesTabRow(
                selectedTab = uiState.selectedTab,
                onTabSelected = viewModel::selectTab
            )

            if (uiState.selectedTab == ActivityTab.ALL && uiState.availableTags.isNotEmpty()) {
                TagFilterRow(
                    tags = uiState.availableTags,
                    selectedTags = uiState.selectedTags,
                    onTagToggled = viewModel::toggleTag,
                    onClear = viewModel::clearTags
                )
            }

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
                    EmptyActivitiesMessage(
                        tab = uiState.selectedTab,
                        nothingSaved = !uiState.isOnline && uiState.allActivities.isEmpty()
                    )
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
                                isPending = activity.id in uiState.pendingIds,
                                showVisibility = uiState.selectedTab == ActivityTab.CREATED,
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
            text = "Activities",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
    }
}


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
            ActivityTab.ALL to "All",
            ActivityTab.FOR_YOU to "For You",
            ActivityTab.LIKED to "Liked",
            ActivityTab.CREATED to "Created"
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


@Composable
private fun TagFilterRow(
    tags: List<String>,
    selectedTags: Set<String>,
    onTagToggled: (String) -> Unit,
    onClear: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            TagChip(label = "All tags", isSelected = selectedTags.isEmpty(), onClick = onClear)
        }
        items(tags) { tag ->
            TagChip(label = tag, isSelected = tag in selectedTags, onClick = { onTagToggled(tag) })
        }
    }
}

@Composable
private fun TagChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isSelected) Coral.copy(alpha = 0.15f) else Color.White)
            .border(1.dp, if (isSelected) Coral else Color(0xFFDDDDDD), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Coral else Color(0xFF666666)
        )
    }
}

private fun emptyStateMessage(tab: ActivityTab): String = when (tab) {
    ActivityTab.ALL -> "No activities match these tags."
    ActivityTab.FOR_YOU -> "No recommendations yet. Like a few activities and we'll suggest ones you'll enjoy."
    ActivityTab.LIKED -> "No liked activities yet."
    ActivityTab.CREATED -> "You haven't created any activities yet."
}

@Composable
private fun EmptyActivitiesMessage(tab: ActivityTab, nothingSaved: Boolean) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = if (nothingSaved) {
                "No activities saved on your phone yet. Connect to the internet to load them."
            } else {
                emptyStateMessage(tab)
            },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF888888)
        )
    }
}


@Composable
fun ActivityCard(
    activity: Activity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    distanceKm: Float? = null,
    isPending: Boolean = false,
    showVisibility: Boolean = false
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
                    CachedImage(
                        url = activity.photoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(R.drawable.activity_placeholder),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.activity_placeholder),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
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
                if (showVisibility) {
                    Spacer(modifier = Modifier.height(6.dp))
                    VisibilityLabel(activity.visibility)
                }
                distanceKm?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "%.1f km away".format(it),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Coral
                    )
                }
                if (isPending) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Waiting to upload",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF888888)
                    )
                }
            }
        }
    }
}


@Composable
private fun VisibilityLabel(visibility: ActivityVisibility) {
    val isPublic = visibility == ActivityVisibility.PUBLIC
    val color = if (isPublic) Coral else Color(0xFF666666)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isPublic) Icons.Filled.Public else Icons.Filled.Lock,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (isPublic) "Public" else "Private",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}


private data class MyActivitiesNavItem(
    val label: String,
    val icon: ImageVector
)

private val myActivitiesNavItems = listOf(
    MyActivitiesNavItem("Explore", Icons.Filled.Explore),
    MyActivitiesNavItem("My Plans", Icons.Filled.CalendarMonth),
    MyActivitiesNavItem("Activities", Icons.Filled.Checklist),
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

private fun offlineMessage(lastUpdated: Long?): String {
    val now = System.currentTimeMillis()
    val saved = when {
        lastUpdated == null -> "saved on your phone"
        now - lastUpdated < DateUtils.MINUTE_IN_MILLIS -> "saved on your phone just now"
        else -> "saved on your phone " +
            DateUtils.getRelativeTimeSpanString(lastUpdated, now, DateUtils.MINUTE_IN_MILLIS).toString().lowercase()
    }
    return "You're offline. Showing activities $saved, new ones will upload when you reconnect."
}
