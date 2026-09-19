package com.example.plansync.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Star
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
import com.example.plansync.model.Participant
import com.example.plansync.model.Plan
import com.example.plansync.model.PlanStatus
import com.example.plansync.ui.theme.Coral
import com.example.plansync.viewmodel.MyPlansViewModel

@Composable
fun MyPlansScreen(
    viewModel: MyPlansViewModel = viewModel(),
    onExploreSelected: () -> Unit = {},
    onProfileSelected: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadPlans()
    }

    Scaffold(
        bottomBar = {
            MyPlansBottomBar(
                onExploreSelected = onExploreSelected,
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
            MyPlansTopBar()

            MyPlansTabRow(
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
                uiState.filteredPlans.isEmpty() -> {
                    EmptyPlansMessage(tab = uiState.selectedTab)
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(uiState.filteredPlans) { plan ->
                            PlanCard(
                                plan = plan,
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
private fun MyPlansTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "My Plans",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = { }) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Create plan",
                tint = Color(0xFF222222)
            )
        }
    }
}

// Mid

private fun tabLabel(status: PlanStatus): String = when (status) {
    PlanStatus.CONFIRMED -> "Upcoming"
    PlanStatus.PENDING_INVITE -> "Pending Invites"
    PlanStatus.PAST -> "Past"
}

private val allTabs = listOf(PlanStatus.CONFIRMED, PlanStatus.PENDING_INVITE, PlanStatus.PAST)

@Composable
private fun MyPlansTabRow(selectedTab: PlanStatus, onTabSelected: (PlanStatus) -> Unit) {
    Column(modifier = Modifier.background(Color.White)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            allTabs.forEach { tab ->
                MyPlansTab(
                    label = tabLabel(tab),
                    isSelected = tab == selectedTab,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        HorizontalDivider(color = Color(0xFFDDDDDD), thickness = 1.dp)
    }
}

@Composable
private fun MyPlansTab(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = if (isSelected) Coral else Color(0xFF888888)
    val underlineColor = if (isSelected) Coral else Color.Transparent

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(underlineColor)
        )
    }
}

// Empty state

private fun emptyStateMessage(tab: PlanStatus): String = when (tab) {
    PlanStatus.CONFIRMED -> "No upcoming plans."
    PlanStatus.PENDING_INVITE -> "No pending invites."
    PlanStatus.PAST -> "No past plans."
}

@Composable
private fun EmptyPlansMessage(tab: PlanStatus) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = emptyStateMessage(tab),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF888888)
        )
    }
}

// Planes

@Composable
private fun PlanCard(plan: Plan, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            if (plan.status == PlanStatus.CONFIRMED) {
                Text(
                    text = "Confirmed",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Coral
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${plan.date} • ${participantCountLabel(plan.participants)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF888888)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                ParticipantAvatarRow(participants = plan.participants)
            }

            if (plan.status == PlanStatus.PAST) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(thickness = 1.dp, color = Color(0xFFDDDDDD))
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Coral,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Manage Split", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Leave a Review", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun participantCountLabel(participants: List<Participant>): String {
    return if (participants.isEmpty()) "Solo Trip" else "${participants.size} People"
}

// avatares

private val avatarColors = listOf(
    Coral,
    Color(0xFFE08B72),
    Color(0xFFEDB5A3),
    Color(0xFFF5D8CF)
)

private const val MAX_AVATARS_SHOWN = 3

@Composable
private fun ParticipantAvatarRow(participants: List<Participant>) {
    if (participants.isEmpty()) {
        SoloTripIcon()
        return
    }

    val visibleParticipants = participants.take(MAX_AVATARS_SHOWN)
    val overflowCount = participants.size - visibleParticipants.size

    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        visibleParticipants.forEach { participant ->
            ParticipantAvatar(participant = participant)
        }
        if (overflowCount > 0) {
            OverflowCircle(count = overflowCount)
        }
    }
}

@Composable
private fun ParticipantAvatar(participant: Participant) {
    val backgroundColor = avatarColors.getOrElse(participant.avatarColorIndex) { Coral }
    val textColor = if (participant.avatarColorIndex <= 1) Color.White else Color(0xFF444444)

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = participant.initials,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
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

@Composable
private fun SoloTripIcon() {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(0xFFF0F0F0)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = "Solo trip",
            tint = Color(0xFF999999),
            modifier = Modifier.size(18.dp)
        )
    }
}

// NavBar

private data class MyPlansNavItem(
    val label: String,
    val icon: ImageVector
)

private val myPlansNavItems = listOf(
    MyPlansNavItem("Explore", Icons.Filled.Explore),
    MyPlansNavItem("My Plans", Icons.Filled.CalendarMonth),
    MyPlansNavItem("My Activities", Icons.Filled.Checklist),
    MyPlansNavItem("My Crew", Icons.Filled.Groups),
    MyPlansNavItem("My Profile", Icons.Filled.Person)
)

@Composable
private fun MyPlansBottomBar(
    onExploreSelected: () -> Unit,
    onProfileSelected: () -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(1) }

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        myPlansNavItems.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = {
                    when (index) {
                        0 -> onExploreSelected()
                        4 -> onProfileSelected()
                        else -> selectedIndex = index
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
