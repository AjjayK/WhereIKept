package com.whereikept.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.whereikept.app.viewmodel.*
import kotlin.math.roundToInt

// ========================================
// STATE: REVIEW_EDITING
// ========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewEditingStateScreen(
    uiState: CaptureUiState,
    viewModel: CaptureViewModel
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Image with tag overlays
        if (uiState.capturedImageUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                TagOverlayCanvas(
                    imageUri = uiState.capturedImageUri,
                    editableTags = uiState.editableTags.filter {
                        it.tag.positionX != null && it.tag.positionY != null
                    },
                    onTagDragged = { tagId, x, y ->
                        viewModel.updateTagPosition(tagId, x, y)
                    },
                    onTagClick = { tagId ->
                        viewModel.showEditTagDialog(tagId)
                    }
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // Unplaced tags section
        if (uiState.unplacedTags.isNotEmpty()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Tags (Tap to edit, drag to position on image):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.unplacedTags) { tag ->
                        TagChip(
                            tag = tag,
                            onClick = { viewModel.showEditTagDialog(tag.id) }
                        )
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // All tags list
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "All Tags (${uiState.editableTags.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                OutlinedButton(
                    onClick = { viewModel.showAddTagDialog() },
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.editableTags.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Label,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No tags found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Tap 'Add' to create tags manually",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                uiState.editableTags.forEach { editableTag ->
                    TagListItem(
                        tag = editableTag.tag,
                        onEdit = { viewModel.showEditTagDialog(editableTag.tag.id) },
                        onDelete = { viewModel.deleteTag(editableTag.tag.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // Transcript section (collapsible)
        if (uiState.transcriptionText.isNotBlank()) {
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            var isExpanded by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { isExpanded = !isExpanded }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Transcript",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand"
                        )
                    }

                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.transcriptionText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save button
        Button(
            onClick = { viewModel.saveAndSubmit() },
            enabled = uiState.editableTags.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save & Submit", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun TagOverlayCanvas(
    imageUri: android.net.Uri,
    editableTags: List<EditableTag>,
    onTagDragged: (String, Float, Float) -> Unit,
    onTagClick: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Background image
        AsyncImage(
            model = imageUri,
            contentDescription = "Captured image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Overlay tags
        editableTags.forEach { editableTag ->
            val tag = editableTag.tag
            if (tag.positionX != null && tag.positionY != null) {
                DraggableTagChip(
                    tag = tag,
                    isDragging = editableTag.isDragging,
                    onDrag = { deltaX, deltaY ->
                        onTagDragged(
                            tag.id,
                            (tag.positionX + deltaX).coerceIn(0f, 1000f),
                            (tag.positionY + deltaY).coerceIn(0f, 1000f)
                        )
                    },
                    onClick = { onTagClick(tag.id) }
                )
            }
        }
    }
}

@Composable
fun DraggableTagChip(
    tag: ImageTag,
    isDragging: Boolean,
    onDrag: (Float, Float) -> Unit,
    onClick: () -> Unit
) {
    var offsetX by remember { mutableStateOf(tag.positionX ?: 50f) }
    var offsetY by remember { mutableStateOf(tag.positionY ?: 50f) }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    offsetX.roundToInt(),
                    offsetY.roundToInt()
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            }
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = if (isDragging) 8.dp else 4.dp,
            modifier = Modifier.border(
                2.dp,
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(20.dp)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = tag.text,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun TagChip(
    tag: ImageTag,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.border(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            RoundedCornerShape(20.dp)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = tag.text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun TagListItem(
    tag: ImageTag,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tag.text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (tag.confidence > 0f) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Confidence: ${(tag.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
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

// ========================================
// DIALOGS
// ========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTagDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var tagText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Tag") },
        text = {
            Column {
                Text("Enter object and location (e.g. 'keys → drawer')")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tagText,
                    onValueChange = { tagText = it },
                    label = { Text("Tag") },
                    placeholder = { Text("keys → drawer") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(tagText) },
                enabled = tagText.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTagDialog(
    tag: ImageTag,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit
) {
    var tagText by remember { mutableStateOf(tag.text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Tag") },
        text = {
            Column {
                OutlinedTextField(
                    value = tagText,
                    onValueChange = { tagText = it },
                    label = { Text("Tag") },
                    placeholder = { Text("keys → drawer") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(tagText) },
                enabled = tagText.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )) {
                    Text("Delete")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
