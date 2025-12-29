package com.whereikept.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.whereikept.app.viewmodel.*
import java.io.File
import kotlin.math.roundToInt

/**
 * Main capture screen with state machine workflow
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(viewModel: CaptureViewModel) {
    val uiState by viewModel.captureUiState.collectAsState()
    val context = LocalContext.current

    // Camera launcher
    val cameraImageUri = remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri.value != null) {
            viewModel.onImageCaptured(cameraImageUri.value!!)
        }
    }

    // Function to launch camera
    val launchCamera = {
        val photoFile = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        cameraImageUri.value = uri
        cameraLauncher.launch(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(getStateTitle(uiState.workflowState))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (uiState.workflowState) {
                CaptureWorkflowState.IDLE -> IdleStateScreen(
                    onStartRecording = { viewModel.startRecording() }
                )

                CaptureWorkflowState.RECORDING -> RecordingStateScreen(
                    uiState = uiState,
                    onStopRecording = { viewModel.stopRecording() },
                    onCancel = { viewModel.cancelRecording() }
                )

                CaptureWorkflowState.IMAGE_CAPTURE -> ImageCaptureStateScreen(
                    uiState = uiState,
                    onCaptureImage = { launchCamera() },
                    onSkipImage = { viewModel.skipImage() }
                )

                CaptureWorkflowState.TRANSCRIBING -> TranscribingStateScreen(
                    uiState = uiState
                )

                CaptureWorkflowState.ANALYZING -> AnalyzingStateScreen(
                    uiState = uiState
                )

                CaptureWorkflowState.REVIEW_EDITING -> ReviewEditingStateScreen(
                    uiState = uiState,
                    viewModel = viewModel
                )

                CaptureWorkflowState.SUBMITTING -> SubmittingStateScreen(
                    uiState = uiState
                )

                CaptureWorkflowState.SUCCESS -> SuccessStateScreen(
                    uiState = uiState,
                    onDone = { viewModel.resetToIdle() }
                )

                CaptureWorkflowState.ERROR -> ErrorStateScreen(
                    uiState = uiState,
                    onRetry = { viewModel.retryFromError() },
                    onSkip = { viewModel.skipError() },
                    onCancel = { viewModel.resetToIdle() }
                )
            }
        }
    }

    // Dialogs
    if (uiState.showAddTagDialog) {
        AddTagDialog(
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
            EditTagDialog(
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

fun getStateTitle(state: CaptureWorkflowState): String {
    return when (state) {
        CaptureWorkflowState.IDLE -> "Where I Kept"
        CaptureWorkflowState.RECORDING -> "Recording..."
        CaptureWorkflowState.IMAGE_CAPTURE -> "Capture Image"
        CaptureWorkflowState.TRANSCRIBING -> "Transcribing..."
        CaptureWorkflowState.ANALYZING -> "Analyzing..."
        CaptureWorkflowState.REVIEW_EDITING -> "Review & Edit"
        CaptureWorkflowState.SUBMITTING -> "Saving..."
        CaptureWorkflowState.SUCCESS -> "Success!"
        CaptureWorkflowState.ERROR -> "Error"
    }
}

// ========================================
// STATE: IDLE
// ========================================

@Composable
fun IdleStateScreen(onStartRecording: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Tap to start recording",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Describe what you're storing and where",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onStartRecording,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(Icons.Default.Mic, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Recording", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ========================================
// STATE: RECORDING
// ========================================

@Composable
fun RecordingStateScreen(
    uiState: CaptureUiState,
    onStopRecording: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Recording visualization with animation
        AnimatedRecordingIndicator(isRecording = true, audioLevel = uiState.audioLevel)

        Spacer(modifier = Modifier.height(32.dp))

        // Timer
        Text(
            text = formatDuration(uiState.recordingDuration),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Audio level indicator
        LinearProgressIndicator(
            progress = { uiState.audioLevel.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.error,
        )

        Spacer(modifier = Modifier.weight(1f))

        // Stop button
        Button(
            onClick = onStopRecording,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Stop Recording", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cancel button
        TextButton(onClick = onCancel) {
            Text("Cancel")
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun AnimatedRecordingIndicator(isRecording: Boolean, audioLevel: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        // Ripple circles
        if (isRecording) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size((100 + index * 30).dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.error.copy(
                                alpha = (0.3f - index * 0.1f) * (0.5f + audioLevel * 0.5f)
                            )
                        )
                )
            }
        }

        // Center icon
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.error
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000) / 60
    return String.format("%02d:%02d", minutes, seconds)
}

// ========================================
// STATE: IMAGE_CAPTURE
// ========================================

@Composable
fun ImageCaptureStateScreen(
    uiState: CaptureUiState,
    onCaptureImage: () -> Unit,
    onSkipImage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Capture an image of where you placed the items",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This helps us better understand the context",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Show transcription progress if still transcribing
        if (uiState.isTranscribing) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Transcribing audio in background...", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onCaptureImage,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Capture Image", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onSkipImage) {
            Text("Skip Image")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ========================================
// STATE: TRANSCRIBING
// ========================================

@Composable
fun TranscribingStateScreen(uiState: CaptureUiState) {
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
            text = "Transcribing audio...",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Please wait while we process your recording",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ========================================
// STATE: ANALYZING
// ========================================

@Composable
fun AnalyzingStateScreen(uiState: CaptureUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Show image if captured
        if (uiState.capturedImageUri != null) {
            AsyncImage(
                model = uiState.capturedImageUri,
                contentDescription = "Captured image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Show transcript
        if (uiState.transcriptionText.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Transcript:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.transcriptionText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        CircularProgressIndicator(modifier = Modifier.size(64.dp))

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Analyzing with Gemma 3N...",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Extracting objects and locations",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

// (Continue in next message due to length - will create ReviewEditingStateScreen and remaining states)
