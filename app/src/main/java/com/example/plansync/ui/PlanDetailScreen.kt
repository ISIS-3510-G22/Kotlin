package com.example.plansync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plansync.model.Activity
import com.example.plansync.model.ActivityIcon
import com.example.plansync.model.Participant
import com.example.plansync.model.Plan
import com.example.plansync.ui.theme.Coral
import com.example.plansync.ui.theme.CoralLight
import com.example.plansync.viewmodel.PlanDetailViewModel

/**
 * UI layer — Composable screen for Plan Detail.
 *
 * Strictly follows MVVM: reads state only from [PlanDetailViewModel] via
 * collectAsStateWithLifecycle() (Observer pattern) and calls only ViewModel
 * functions. Never touches PlanRepository directly.
 *
 * @param planId    ID of the plan to load.
 * @param viewModel Injected via viewModel() factory; can be overridden in tests.
 * @param onBack    Callback for the back arrow.
 * @param onInvite  Callback for the add-participant (+) button — navigates to InviteScreen.
 */
@Composable
fun PlanDetailScreen(
    planId: String = "plan-001",
    viewModel: PlanDetailViewModel = viewModel(),
    onBack: () -> Unit = {},
    onInvite: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Load plan once when the screen first enters composition
    LaunchedEffect(planId) {
        viewModel.loadPlan(planId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // ── Top bar ────────────────────────────────────────────────────────
        PlanDetailTopBar(onBack = onBack)

        // ── Content: loading / error / data ───────────────────────────────
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
            uiState.plan != null -> {
                PlanDetailContent(plan = uiState.plan!!, onInvite = { onInvite(planId) })
            }
        }
    }

    // ── RSVP dialog ────────────────────────────────────────────────────────
    if (uiState.showRsvpDialog && uiState.plan != null) {
        RsvpDialog(
            planTitle = uiState.plan!!.title,
            planMeta  = "${uiState.plan!!.date} · ${uiState.plan!!.participants.size} people invited",
            onGoing      = { viewModel.onRsvpDismissed() },
            onCantMake   = { viewModel.onRsvpDismissed() },
            onMaybeLater = { viewModel.onRsvpDismissed() }
        )
    }
}

// ── Top bar ────────────────────────────────────────────────────────────────────

@Composable
private fun PlanDetailTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color(0xFF222222)
            )
        }
        Text(
            text = "My Plans",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        // Spacer to balance the back button and keep title centered
        Spacer(modifier = Modifier.size(48.dp))
    }
}

// ── Scrollable plan content ────────────────────────────────────────────────────

@Composable
private fun PlanDetailContent(plan: Plan, onInvite: () -> Unit = {}) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {

        // Header: title, date, cost, activity count, action buttons
        item { PlanHeader(plan = plan) }

        item { HorizontalDivider(color = Color(0xFFDDDDDD), thickness = 1.dp) }

        // Participants row
        item { ParticipantsSection(participants = plan.participants, onInvite = onInvite) }

        // Activity timeline items
        itemsIndexed(plan.activities) { index, activity ->
            ActivityTimelineItem(
                activity = activity,
                isLast = index == plan.activities.lastIndex
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// ── Plan header ────────────────────────────────────────────────────────────────

@Composable
private fun PlanHeader(plan: Plan) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        // Title
        Text(
            text = plan.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Date and cost row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = null,
                tint = Color(0xFF888888),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = plan.date,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF444444)
            )
            Spacer(modifier = Modifier.width(20.dp))
            Icon(
                imageVector = Icons.Default.AttachMoney,
                contentDescription = null,
                tint = Color(0xFF888888),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Est. ${'$'}${plan.estimatedCostPerPerson}/pp",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF444444)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Activity count row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                contentDescription = null,
                tint = Color(0xFF888888),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${plan.activityCount} Activities",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF444444)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionButton(
                icon = Icons.Default.Map,
                label = "View\nMap",
                modifier = Modifier.weight(1f),
                onClick = { /* TODO: open map */ }
            )
            ActionButton(
                icon = Icons.Default.Add,
                label = "Add\nActivity",
                modifier = Modifier.weight(1f),
                onClick = { /* TODO: add activity */ }
            )
            ActionButton(
                icon = Icons.Default.CheckCircle,
                label = "RSVP",
                modifier = Modifier.weight(1f),
                onClick = { /* TODO: RSVP flow */ }
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Coral,
            contentColor = Color.White
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Participants section ────────────────────────────────────────────────────────

// Maps avatarColorIndex → background color (dark coral to near-white)
private val avatarColors = listOf(
    Coral,
    Color(0xFFE08B72),
    Color(0xFFEDB5A3),
    Color(0xFFF5D8CF)
)

@Composable
private fun ParticipantsSection(participants: List<Participant>, onInvite: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Text(
            text = "Participants (${participants.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            participants.forEach { participant ->
                ParticipantAvatar(participant = participant)
            }
            // Add participant button — navigates to InviteScreen
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.5.dp, Color(0xFFCCCCCC), RoundedCornerShape(12.dp))
                    .clickable { onInvite() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Invite participants",
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ParticipantAvatar(participant: Participant) {
    val bgColor = avatarColors.getOrElse(participant.avatarColorIndex) { CoralLight }
    val textColor = if (participant.avatarColorIndex <= 1) Color.White else Color(0xFF444444)

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = participant.initials,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

// ── Activity timeline ───────────────────────────────────────────────────────────

@Composable
private fun ActivityTimelineItem(activity: Activity, isLast: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp)
    ) {
        // Left: timeline line + icon
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CoralLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = activity.iconType.toIcon(),
                    contentDescription = activity.category,
                    tint = Coral,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(24.dp)
                        .background(Color(0xFFDDDDDD))
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right: activity card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (isLast) 0.dp else 8.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // Name + category chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activity.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    CategoryChip(label = activity.category)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Description
                Text(
                    text = activity.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF666666)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Address
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF888888),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = activity.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF888888)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .border(1.dp, CoralLight, RoundedCornerShape(50.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Coral,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// Maps ActivityIcon enum to a Material icon vector
private fun ActivityIcon.toIcon(): ImageVector = when (this) {
    ActivityIcon.COFFEE   -> Icons.Default.LocalCafe
    ActivityIcon.OUTDOORS -> Icons.Default.Park
    ActivityIcon.FOOD     -> Icons.Default.Restaurant
    ActivityIcon.MUSIC    -> Icons.Default.MusicNote
    ActivityIcon.DEFAULT  -> Icons.Default.Star
}
