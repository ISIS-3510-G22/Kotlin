package com.example.plansync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.plansync.model.User
import com.example.plansync.ui.theme.Coral

val crewAvatarColors = listOf(
    Color(0xFFF3B9A0),
    Color(0xFFCBB8E8),
    Color(0xFFAEB9D9),
    Color(0xFFF6DCC0),
    Color(0xFFA8DCC9)
)

fun crewInitials(name: String, lastName: String = ""): String {
    val firstWord = name.trim().substringBefore(" ")
    val secondWord = if (lastName.isNotBlank()) lastName.trim() else name.trim().substringAfter(" ", "")
    return (firstWord.take(1) + secondWord.take(1)).uppercase()
}

@Composable
fun CrewAvatar(
    name: String,
    lastName: String,
    colorIndex: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    photoUrl: String = ""
) {
    val initials = crewInitials(name, lastName)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(crewAvatarColors[colorIndex % crewAvatarColors.size]),
        contentAlignment = Alignment.Center
    ) {
        if (initials.isEmpty()) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Color(0xFF555555),
                modifier = Modifier.size(size / 2)
            )
        } else {
            Text(
                text = initials,
                style = if (size < 40.dp) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF444444)
            )
        }
        if (photoUrl.isNotBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun CrewTopBar(title: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(36.dp)
                .clip(CircleShape)
                .background(Coral.copy(alpha = 0.12f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CrewErrorMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun EmptyCrewMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF888888)
        )
    }
}

private const val MAX_AVATARS_SHOWN = 3

@Composable
fun GroupAvatarCluster(memberIds: List<String>, groupMembers: Map<String, User>) {
    val shownIds = memberIds.take(MAX_AVATARS_SHOWN)
    val overflowCount = memberIds.size - shownIds.size

    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        shownIds.forEachIndexed { index, memberId ->
            val member = groupMembers[memberId]
            CrewAvatar(
                name = member?.name.orEmpty(),
                lastName = member?.lastName.orEmpty(),
                colorIndex = index,
                size = 32.dp,
                modifier = Modifier.border(2.dp, Color.White, CircleShape),
                photoUrl = member?.photoUrl.orEmpty()
            )
        }
        if (overflowCount > 0) {
            OverflowCircle(count = overflowCount)
        }
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
fun CrewSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Color(0xFF888888),
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

@Composable
fun CrewPersonCard(
    name: String,
    lastName: String,
    subtitle: String,
    photoUrl: String,
    colorIndex: Int,
    buttonLabel: String,
    buttonEnabled: Boolean,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            CrewAvatar(
                name = name,
                lastName = lastName,
                colorIndex = colorIndex,
                size = 44.dp,
                photoUrl = photoUrl
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$name $lastName".trim(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF888888)
                )
            }
            Button(
                onClick = onButtonClick,
                enabled = buttonEnabled,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White)
            ) {
                Text(text = buttonLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CrewDecisionButtons(
    acceptLabel: String,
    enabled: Boolean,
    onAccept: () -> Unit,
    onDeny: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(
            onClick = onAccept,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759), contentColor = Color.White)
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = acceptLabel, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onDeny,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF00000), contentColor = Color.White)
        ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Deny", fontWeight = FontWeight.Bold)
        }
    }
}
