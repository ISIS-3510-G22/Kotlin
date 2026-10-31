package com.example.plansync.ui

import android.widget.Toast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plansync.R
import com.example.plansync.model.Activity
import com.example.plansync.ui.theme.Coral
import com.example.plansync.viewmodel.ActivityDetailViewModel

@Composable
fun ActivityDetailScreen(
    activityId: String,
    viewModel: ActivityDetailViewModel = viewModel(),
    onBack: () -> Unit = {},
    onEdit: (String) -> Unit = {},
    onAddToPlan: (String) -> Unit = {},
    onDeleted: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.didDelete) {
        if (uiState.didDelete) {
            if (uiState.deletedOffline) {
                Toast.makeText(context, "Deleted on your phone. It will sync when you reconnect.", Toast.LENGTH_LONG).show()
            }
            onDeleted()
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete activity?") },
            text = { Text("This activity will be deleted for everyone. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteActivity()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LaunchedEffect(activityId) {
        viewModel.load(activityId)
    }

    PhotoSourcePicker(
        show = showPhotoSourceDialog,
        title = "Activity Photo",
        onDismiss = { showPhotoSourceDialog = false },
        onPicked = viewModel::onPhotoPicked
    )

    Scaffold(
        containerColor = Color(0xFFF5F5F5),
        bottomBar = {
            if (uiState.activity != null) {
                Button(
                    onClick = { onAddToPlan(activityId) },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp)
                ) {
                    Text("Add to Plan", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { innerPadding ->
        val activity = uiState.activity
        when {
            activity == null && uiState.errorMessage != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(text = uiState.errorMessage!!, color = MaterialTheme.colorScheme.error)
                }
            }
            activity == null -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Coral)
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                ) {
                    OfflineBanner(
                        isOnline = uiState.isOnline,
                        message = "You're offline. Likes will sync when you reconnect, changing the photo needs a connection."
                    )
                    ActivityPhotoHeader(
                        activity = activity,
                        isOwner = uiState.isOwner,
                        canChangePhoto = uiState.isOnline && !uiState.isPending,
                        isUploadingPhoto = uiState.isUploadingPhoto,
                        isSharing = uiState.isSharing,
                        isDeleting = uiState.isDeleting,
                        onBack = onBack,
                        onEdit = { onEdit(activity.id) },
                        onChangePhoto = { showPhotoSourceDialog = true },
                        onShare = viewModel::shareActivity,
                        onDelete = { showDeleteDialog = true }
                    )
                    ActivityInfo(
                        activity = activity,
                        isLiked = uiState.isLiked,
                        onToggleLike = viewModel::toggleLike
                    )
                    if (uiState.isPending) {
                        Text(
                            text = "This activity is waiting to upload. It will sync when you're back online.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF888888),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    uiState.errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityPhotoHeader(
    activity: Activity,
    isOwner: Boolean,
    canChangePhoto: Boolean,
    isUploadingPhoto: Boolean,
    isSharing: Boolean,
    isDeleting: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onChangePhoto: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary)
                )
            )
    ) {
        if (activity.photoUrl.isNotBlank()) {
            CachedImage(
                url = activity.photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.banner_login),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(R.drawable.banner_login),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isUploadingPhoto) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HeaderButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Coral)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderButton(onClick = { if (!isSharing) onShare() }) {
                    if (isSharing) {
                        CircularProgressIndicator(color = Coral, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Filled.Share, contentDescription = "Share activity", tint = Coral)
                    }
                }
                if (isOwner) {
                    if (canChangePhoto) {
                        HeaderButton(onClick = onChangePhoto) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = "Change photo", tint = Coral)
                        }
                    }
                    HeaderButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit activity", tint = Coral)
                    }
                    HeaderButton(onClick = { if (!isDeleting) onDelete() }) {
                        if (isDeleting) {
                            CircularProgressIndicator(color = Coral, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete activity", tint = Coral)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun ActivityInfo(activity: Activity, isLiked: Boolean, onToggleLike: () -> Unit) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = activity.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggleLike) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isLiked) "Unlike" else "Like",
                    tint = Coral
                )
            }
            Text(
                text = activity.likedBy.size.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (activity.tags.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                activity.tags.forEach { tag ->
                    Surface(shape = RoundedCornerShape(50), color = Coral.copy(alpha = 0.15f)) {
                        Text(
                            text = tag.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Coral,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = activity.address, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF666666))
        }

        DetailSection(label = "EXPECTED PRICE", value = "$${"%,d".format(activity.price)}")

        if (activity.description.isNotBlank()) {
            DetailSection(label = "NOTES", value = activity.description)
        }
    }
}

@Composable
private fun DetailSection(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF888888)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
