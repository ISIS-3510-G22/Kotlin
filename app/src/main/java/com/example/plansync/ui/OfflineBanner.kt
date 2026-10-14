package com.example.plansync.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * UI layer — reusable offline indicator banner.
 *
 * Shown at the top of screens when the device has no internet connection.
 * Animates in/out smoothly so the layout does not jump.
 *
 * Usage: place above the main screen content inside a Column, and pass
 * [isOnline] from a ViewModel state that collects [NetworkMonitor.isOnline].
 *
 * Design pattern: Composable with animated visibility — UI reacts to
 * connectivity state exposed by ViewModel via StateFlow (Observer pattern).
 */
@Composable
fun OfflineBanner(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !isOnline,
        enter = expandVertically(expandFrom = Alignment.Top),
        exit  = shrinkVertically(shrinkTowards = Alignment.Top),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFB00020))
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "You're offline — changes will sync when you reconnect",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
