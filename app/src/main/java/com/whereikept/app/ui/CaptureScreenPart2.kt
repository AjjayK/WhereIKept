package com.whereikept.app.ui

import android.graphics.Bitmap
import android.view.View
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.whereikept.app.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect

// ========================================
// STATE: REVIEW_EDITING - Modern Redesign
// ========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewEditingStateScreen(
    uiState: CaptureUiState,
    viewModel: CaptureViewModel
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    // Callback to capture screenshot and submit
    val captureAndSubmit: () -> Unit = {
        coroutineScope.launch {
            val bitmap = withContext(Dispatchers.Main) {
                captureViewAsBitmap(view)
            }
            viewModel.saveAndSubmit(bitmap)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        // Fullscreen Image with overlays
        if (uiState.capturedImageUri != null) {
            // Background Image (full screen)
            AsyncImage(
                model = uiState.capturedImageUri,
                contentDescription = "Captured image",
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f),
                contentScale = ContentScale.Crop
            )

            // Gradient overlay at top for better button visibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .zIndex(1f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
            )
        } else {
            // No image placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1F2937)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = Color.White.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No image captured",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        // Top floating action bar with glassmorphism
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .zIndex(10f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            FloatingActionButton(
                onClick = { viewModel.resetToIdle() },
                modifier = Modifier.size(48.dp),
                containerColor = Color.Black.copy(alpha = 0.3f),
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(0.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            // Right side buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Add Tag button
                Button(
                    onClick = { viewModel.showAddTagDialog() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black.copy(alpha = 0.3f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Tag", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }

                // Done button
                Button(
                    onClick = { captureAndSubmit() },
                    enabled = uiState.editableTags.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4F46E5), // Indigo primary
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF4F46E5).copy(alpha = 0.5f),
                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.height(48.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 4.dp
                    )
                ) {
                    Text("Done", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Placed tags with connector lines - MUST BE AFTER IMAGE
        uiState.editableTags
            .filter { it.tag.positionX != null && it.tag.positionY != null }
            .forEach { editableTag ->
                TagWithConnector(
                    tag = editableTag.tag,
                    isDragging = editableTag.isDragging,
                    onDragEnd = { finalX, finalY ->
                        viewModel.updateTagPosition(
                            editableTag.tag.id,
                            finalX.coerceIn(0f, screenWidth),
                            finalY.coerceIn(0f, screenHeight)
                        )
                    },
                    onEdit = { viewModel.showEditTagDialog(editableTag.tag.id) },
                    onDelete = { viewModel.deleteTag(editableTag.tag.id) }
                )
            }

        // Bottom sheet with detected tags (glassmorphism)
        BottomTagSheet(
            unplacedTags = uiState.unplacedTags,
            onTagEdit = { viewModel.showEditTagDialog(it) },
            onTagDelete = { viewModel.deleteTag(it) },
            onAddTag = { viewModel.showAddTagDialog() },
            onTagPlaced = { tagId, x, y ->
                // Clamp positions to screen bounds
                viewModel.updateTagPosition(
                    tagId,
                    x.coerceIn(0f, screenWidth - 200f), // Leave room for tag width
                    y.coerceIn(100f, screenHeight - 300f) // Keep away from top/bottom bars
                )
            }
        )
    }

    // Dialogs
    if (uiState.showAddTagDialog) {
        ModernAddTagDialog(
            onDismiss = { viewModel.hideAddTagDialog() },
            onConfirm = { text ->
                viewModel.addNewTag(text)
                viewModel.hideAddTagDialog()
            }
        )
    }

    if (uiState.showEditTagDialog && uiState.selectedTagId != null) {
        val tag = uiState.editableTags.find { it.tag.id == uiState.selectedTagId }?.tag
        if (tag != null) {
            ModernEditTagDialog(
                tag = tag,
                onDismiss = { viewModel.hideEditTagDialog() },
                onSave = { newText ->
                    viewModel.updateTagText(tag.id, newText)
                    viewModel.hideEditTagDialog()
                },
                onDelete = {
                    viewModel.deleteTag(tag.id)
                    viewModel.hideEditTagDialog()
                }
            )
        }
    }
}

// ========================================
// Tag with Connector Line
// ========================================

@Composable
fun TagWithConnector(
    tag: ImageTag,
    isDragging: Boolean,
    onDragEnd: (Float, Float) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // Use local state for smooth dragging
    var localOffsetX by remember(tag.id, tag.positionX) { mutableStateOf(tag.positionX ?: 100f) }
    var localOffsetY by remember(tag.id, tag.positionY) { mutableStateOf(tag.positionY ?: 100f) }
    var isCurrentlyDragging by remember { mutableStateOf(false) }

    // Sync with ViewModel when tag position changes from external source
    LaunchedEffect(tag.positionX, tag.positionY) {
        if (!isCurrentlyDragging) {
            tag.positionX?.let { localOffsetX = it }
            tag.positionY?.let { localOffsetY = it }
        }
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    localOffsetX.roundToInt(),
                    localOffsetY.roundToInt()
                )
            }
            .zIndex(if (isCurrentlyDragging) 1000f else 50f) // Always above image
    ) {
        // Connector line and dot
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = 44.dp)
        ) {
            // Line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(28.dp)
                    .background(Color.White)
            )
            // Dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .border(2.dp, Color(0xFF4F46E5), CircleShape)
                    .background(Color.White, CircleShape)
                    .shadow(4.dp, CircleShape)
            )
        }

        // Tag chip with edit/delete buttons
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.92f),
            shadowElevation = if (isCurrentlyDragging) 12.dp else 6.dp,
            modifier = Modifier
                .pointerInput(tag.id) {
                    detectDragGestures(
                        onDragStart = {
                            isCurrentlyDragging = true
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Update local state immediately for smooth dragging
                            localOffsetX += dragAmount.x
                            localOffsetY += dragAmount.y
                        },
                        onDragEnd = {
                            isCurrentlyDragging = false
                            // Update ViewModel with final position
                            onDragEnd(localOffsetX, localOffsetY)
                        }
                    )
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            ) {
                // Pulsing indicator dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF4F46E5), CircleShape)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = tag.text,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1F2937)
                    )
                )

                // Divider
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .width(1.dp)
                        .height(20.dp)
                        .background(Color(0xFFD1D5DB))
                )

                // Edit button
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF6B7280)
                    )
                }

                // Delete button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Delete",
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF9CA3AF)
                    )
                }
            }
        }
    }
}

// ========================================
// Bottom Tag Sheet (Glassmorphism)
// ========================================

@Composable
fun BottomTagSheet(
    unplacedTags: List<ImageTag>,
    onTagEdit: (String) -> Unit,
    onTagDelete: (String) -> Unit,
    onAddTag: () -> Unit,
    onTagPlaced: (String, Float, Float) -> Unit = { _, _, _ -> }
) {
    if (unplacedTags.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 88.dp) // Above bottom nav
            .zIndex(5f)
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Glassmorphism card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DETECTED TAGS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.2.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    )
                    Text(
                        text = "Drag to place",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tags row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(unplacedTags) { tag ->
                        UnplacedTagChip(
                            tag = tag,
                            onEdit = { onTagEdit(tag.id) },
                            onDelete = { onTagDelete(tag.id) },
                            onPlaced = onTagPlaced
                        )
                    }

                    // Add new tag button
                    item {
                        OutlinedButton(
                            onClick = onAddTag,
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(
                                2.dp,
                                Color.White.copy(alpha = 0.3f)
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.White.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ========================================
// Unplaced Tag Chip
// ========================================

@Composable
fun UnplacedTagChip(
    tag: ImageTag,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPlaced: (String, Float, Float) -> Unit = { _, _, _ -> }
) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var initialAbsoluteX by remember { mutableStateOf(0f) }
    var initialAbsoluteY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    offsetX.roundToInt(),
                    offsetY.roundToInt()
                )
            }
            .onGloballyPositioned { coordinates ->
                // Capture initial absolute position when chip is laid out
                if (!isDragging && offsetX == 0f && offsetY == 0f) {
                    val position = coordinates.positionInRoot()
                    initialAbsoluteX = position.x
                    initialAbsoluteY = position.y
                }
            }
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = if (isDragging) 12.dp else 4.dp,
            modifier = Modifier
                .height(40.dp)
                .pointerInput(tag.id) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        },
                        onDragEnd = {
                            isDragging = false
                            // If dragged upward significantly (offsetY negative means up)
                            if (offsetY < -150f) {
                                // Calculate final absolute position
                                val finalX = initialAbsoluteX + offsetX
                                val finalY = initialAbsoluteY + offsetY
                                onPlaced(tag.id, finalX, finalY)
                            }
                            // Reset position
                            offsetX = 0f
                            offsetY = 0f
                        }
                    )
                }
                .zIndex(if (isDragging) 100f else 1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Text(
                    text = tag.text,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1F2937)
                    )
                )

                // Divider
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .width(1.dp)
                        .height(20.dp)
                        .background(Color(0xFFE5E7EB))
                )

                // Edit button
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF9CA3AF)
                    )
                }

                // Delete button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Delete",
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF9CA3AF)
                    )
                }
            }
        }
    }
}

// ========================================
// Modern Dialogs
// ========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernAddTagDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var tagText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Add New Tag",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter object and location (e.g. 'Laptop → Table')",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = tagText,
                    onValueChange = { tagText = it },
                    label = { Text("Tag") },
                    placeholder = { Text("Laptop → Table") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(tagText) },
                enabled = tagText.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Add Tag")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernEditTagDialog(
    tag: ImageTag,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit
) {
    var tagText by remember { mutableStateOf(tag.text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Edit Tag",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = tagText,
                    onValueChange = { tagText = it },
                    label = { Text("Tag") },
                    placeholder = { Text("Laptop → Table") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (tag.confidence > 0f) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "AI Confidence: ${(tag.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }

                Button(
                    onClick = { onSave(tagText) },
                    enabled = tagText.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

// ========================================
// STATE: SUBMITTING
// ========================================

@Composable
fun SubmittingStateScreen(uiState: CaptureUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Saving items...",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = uiState.processingMessage.ifBlank { "Please wait" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ========================================
// STATE: SUCCESS
// ========================================

@Composable
fun SuccessStateScreen(
    uiState: CaptureUiState,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Success!",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Saved ${uiState.savedItemsCount} item(s)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (uiState.savedItemsSummary.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = uiState.savedItemsSummary,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("Done", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ========================================
// STATE: ERROR
// ========================================

@Composable
fun ErrorStateScreen(
    uiState: CaptureUiState,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = getErrorTitle(uiState.error?.type),
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = uiState.error?.message ?: "An unknown error occurred",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Retry button
        if (uiState.error?.isRetryable == true) {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Skip button (for skippable errors)
        if (uiState.error?.canSkip == true) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(0.7f).height(48.dp)
            ) {
                Text("Skip & Continue")
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Cancel button
        TextButton(onClick = onCancel) {
            Text("Cancel & Start Over")
        }
    }
}

fun getErrorTitle(errorType: ErrorType?): String {
    return when (errorType) {
        ErrorType.PERMISSION_DENIED -> "Permission Required"
        ErrorType.WHISPER_FAILED -> "Transcription Failed"
        ErrorType.GEMMA_FAILED -> "Analysis Failed"
        ErrorType.CAMERA_FAILED -> "Camera Error"
        ErrorType.DB_ERROR -> "Save Failed"
        else -> "Error Occurred"
    }
}

/**
 * Capture the current view as a Bitmap for screenshot purposes.
 * This is used to capture the review screen with positioned tags before submitting to Gemma.
 */
private fun captureViewAsBitmap(view: View): Bitmap? {
    return try {
        // Get the root view's drawing cache
        val bitmap = Bitmap.createBitmap(
            view.width,
            view.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        view.draw(canvas)
        bitmap
    } catch (e: Exception) {
        android.util.Log.e("CaptureScreenPart2", "Failed to capture view as bitmap: ${e.message}", e)
        null
    }
}
