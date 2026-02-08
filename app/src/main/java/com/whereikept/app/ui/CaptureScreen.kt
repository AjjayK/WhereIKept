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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // Remove Scaffold and TopAppBar for modern fullscreen experience
    Box(
        modifier = Modifier.fillMaxSize()
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

    // Dialogs now use modern style from CaptureScreenPart2
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF4F46E5).copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated mic icon with gradient
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF4F46E5).copy(alpha = 0.2f),
                                Color(0xFF4F46E5).copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color(0xFF4F46E5)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Ready to Record",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Describe what you're storing and where",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Example phrase
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.85f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = Color(0xFF4F46E5).copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "\"I'm putting my passport in the bedroom drawer\"",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Modern gradient button
            Button(
                onClick = onStartRecording,
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4F46E5)
                ),
                shape = RoundedCornerShape(32.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 4.dp
                )
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Start Recording",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                )
            }
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFEF4444).copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            // Recording visualization with animation
            AnimatedRecordingIndicator(isRecording = true, audioLevel = uiState.audioLevel)

            Spacer(modifier = Modifier.height(40.dp))

            // Timer with modern styling
            Text(
                text = formatDuration(uiState.recordingDuration),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF4444)
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Audio level card with glassmorphism
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(80.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Audio Level",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { uiState.audioLevel.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFFEF4444),
                        trackColor = Color(0xFFEF4444).copy(alpha = 0.2f)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Stop button with modern styling
            Button(
                onClick = onStopRecording,
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF4444)
                ),
                shape = RoundedCornerShape(32.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 4.dp
                )
            ) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Stop Recording",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cancel button
            TextButton(onClick = onCancel) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // Camera preview card with modern styling
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shadowElevation = 4.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = Color(0xFF4F46E5).copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Capture an Image",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Take a photo of where you placed the items for better context",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Show transcription progress if still transcribing
            if (uiState.isTranscribing) {
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Transcribing audio...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Capture button
            Button(
                onClick = onCaptureImage,
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4F46E5)
                ),
                shape = RoundedCornerShape(32.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 4.dp
                )
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Take Photo",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Skip button
            TextButton(onClick = onSkipImage) {
                Text(
                    "Skip for Now",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ========================================
// STATE: TRANSCRIBING
// ========================================

@Composable
fun TranscribingStateScreen(uiState: CaptureUiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF4F46E5).copy(alpha = 0.05f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Modern loader with gradient background
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF4F46E5).copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = Color(0xFF4F46E5),
                    strokeWidth = 4.dp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Transcribing Audio",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Processing your recording with Whisper AI",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

// ========================================
// STATE: ANALYZING
// ========================================

@Composable
fun AnalyzingStateScreen(uiState: CaptureUiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Show image if captured with modern styling
            if (uiState.capturedImageUri != null) {
                AsyncImage(
                    model = uiState.capturedImageUri,
                    contentDescription = "Captured image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Show transcript with modern card
            if (uiState.transcriptionText.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                tint = Color(0xFF4F46E5),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Transcript",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF4F46E5)
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.transcriptionText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // Modern AI processing indicator
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF10B981).copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = Color(0xFF10B981),
                    strokeWidth = 4.dp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // AI icon with label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gemma 3N AI",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF10B981)
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Analyzing content and extracting tags",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
