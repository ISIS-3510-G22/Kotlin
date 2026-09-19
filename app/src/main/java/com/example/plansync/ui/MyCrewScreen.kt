package com.example.plansync.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plansync.model.Friend
import com.example.plansync.model.Group
import com.example.plansync.ui.theme.Coral
import com.example.plansync.viewmodel.CrewTab
import com.example.plansync.viewmodel.MyCrewViewModel

@Composable
fun MyCrewScreen(
    viewModel: MyCrewViewModel = viewModel(),
    onExploreSelected: () -> Unit = {},
    onMyPlansSelected: () -> Unit = {},
    onProfileSelected: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadCrew()
    }

    Scaffold(
        bottomBar = {
            MyCrewBottomBar(
                onExploreSelected = onExploreSelected,
                onMyPlansSelected = onMyPlansSelected,
                onProfileSelected = onProfileSelected
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(innerPadding)
        ) {
            MyCrewTopBar()

            MyCrewTabRow(
                selectedTab = uiState.selectedTab,
                onTabSelected = viewModel::selectTab
            )

            Box(modifier = Modifier.weight(1f)) {
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
                    uiState.selectedTab == CrewTab.GROUPS -> {
                        if (uiState.groups.isEmpty()) {
                            EmptyCrewMessage(message = "No groups yet.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(uiState.groups) { group ->
                                    GroupCard(
                                        group = group,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        if (uiState.friends.isEmpty()) {
                            EmptyCrewMessage(message = "No friends added.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(uiState.friends) { friend ->
                                    FriendCard(
                                        friend = friend,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            CrewActionButton(
                label = if (uiState.selectedTab == CrewTab.GROUPS) "New Invitations (1)" else "Add Friend",
                onClick = { }
            )
        }
    }
}

// Top

@Composable
private fun MyCrewTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "My Crew",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

// Tabs

@Composable
private fun MyCrewTabRow(selectedTab: CrewTab, onTabSelected: (CrewTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CrewTabPill(
            label = "My Groups",
            isSelected = selectedTab == CrewTab.GROUPS,
            onClick = { onTabSelected(CrewTab.GROUPS) },
            modifier = Modifier.weight(1f)
        )
        CrewTabPill(
            label = "My Friends",
            isSelected = selectedTab == CrewTab.FRIENDS,
            onClick = { onTabSelected(CrewTab.FRIENDS) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CrewTabPill(
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

@Composable
private fun EmptyCrewMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF888888)
        )
    }
}

// Action button

@Composable
private fun CrewActionButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White)
    ) {
        Text(text = label, fontWeight = FontWeight.Bold)
    }
}

// Groups

@Composable
private fun GroupCard(group: Group, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            GroupAvatarCluster(memberCount = group.memberCount)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${group.memberCount} Members",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF888888)
            )
        }
    }
}

// Friends

@Composable
private fun FriendCard(friend: Friend, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FriendAvatar(friend = friend)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = friend.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = friend.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF888888)
                )
            }
        }
    }
}

@Composable
private fun FriendAvatar(friend: Friend) {
    val backgroundColor = crewAvatarColors.getOrElse(friend.avatarColorIndex) { Coral }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = friend.initials,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF444444)
        )
    }
}

// Avatars

private val crewAvatarColors = listOf(
    Color(0xFFF3B9A0),
    Color(0xFFCBB8E8),
    Color(0xFFAEB9D9),
    Color(0xFFF6DCC0),
    Color(0xFFA8DCC9)
)

private const val MAX_AVATARS_SHOWN = 3

@Composable
private fun GroupAvatarCluster(memberCount: Int) {
    val shownCount = minOf(memberCount, MAX_AVATARS_SHOWN)
    val overflowCount = memberCount - shownCount

    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        repeat(shownCount) { index ->
            GroupMemberAvatar(colorIndex = index)
        }
        if (overflowCount > 0) {
            OverflowCircle(count = overflowCount)
        }
    }
}

@Composable
private fun GroupMemberAvatar(colorIndex: Int) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(crewAvatarColors.getOrElse(colorIndex) { Coral })
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = Color(0xFF555555),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun OverflowCircle(count: Int) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(0xFFE0E0E0))
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+$count",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF666666)
        )
    }
}

// NavBar

private data class MyCrewNavItem(
    val label: String,
    val icon: ImageVector
)

private val myCrewNavItems = listOf(
    MyCrewNavItem("Explore", Icons.Filled.Explore),
    MyCrewNavItem("My Plans", Icons.Filled.CalendarMonth),
    MyCrewNavItem("My Activities", Icons.Filled.Checklist),
    MyCrewNavItem("My Crew", Icons.Filled.Groups),
    MyCrewNavItem("My Profile", Icons.Filled.Person)
)

@Composable
private fun MyCrewBottomBar(
    onExploreSelected: () -> Unit,
    onMyPlansSelected: () -> Unit,
    onProfileSelected: () -> Unit
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        myCrewNavItems.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = index == 3,
                onClick = {
                    when (index) {
                        0 -> onExploreSelected()
                        1 -> onMyPlansSelected()
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
