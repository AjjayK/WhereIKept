package com.whereikept.app.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import java.util.UUID

/**
 * Represents the current state of the capture workflow
 */
enum class CaptureWorkflowState {
    IDLE,                    // Initial state - ready to record
    RECORDING,               // Actively capturing audio
    IMAGE_CAPTURE,           // Prompt user to take photo
    TRANSCRIBING,           // Whisper processing audio
    ANALYZING,              // Gemma analyzing transcript + image
    REVIEW_EDITING,         // User reviews/edits tags on image
    SUBMITTING,             // Generating DB commands and saving
    SUCCESS,                // Confirmation shown
    ERROR                   // Error state with retry options
}

/**
 * UI state for the capture workflow
 */
data class CaptureUiState(
    val workflowState: CaptureWorkflowState = CaptureWorkflowState.IDLE,

    // Recording data
    val isRecording: Boolean = false,
    val recordingDuration: Long = 0L,
    val audioLevel: Float = 0f,
    val audioFilePath: String? = null,

    // Image data
    val capturedImageUri: Uri? = null,
    val capturedImageBitmap: Bitmap? = null,

    // Transcription data
    val transcriptionText: String = "",
    val transcriptionProgress: Float = 0f,
    val isTranscribing: Boolean = false,

    // Analysis data
    val extractedTags: List<ImageTag> = emptyList(),
    val analysisProgress: Float = 0f,

    // Tag editing
    val editableTags: List<EditableTag> = emptyList(),
    val selectedTagId: String? = null,
    val unplacedTags: List<ImageTag> = emptyList(),

    // Processing states
    val isProcessing: Boolean = false,
    val processingMessage: String = "",

    // Results
    val savedItemsCount: Int = 0,
    val savedItemsSummary: String = "",

    // Error handling
    val error: ErrorState? = null,

    // Permissions
    val hasMicPermission: Boolean = false,
    val hasCameraPermission: Boolean = false,

    // Dialogs
    val showAddTagDialog: Boolean = false,
    val showEditTagDialog: Boolean = false,
    val showPermissionDialog: Boolean = false
)

/**
 * Represents a tag extracted from analysis or added by user
 */
data class ImageTag(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val objectName: String = "",      // Parsed object name
    val location: String = "",        // Parsed location
    val objectAttribute: String? = null,  // Object attributes (color, size, shape, material)
    val locationParent: String? = null,   // High-level location category (home, office, farm)
    val positionX: Float? = null,     // Null = not positioned yet
    val positionY: Float? = null
)

/**
 * Wrapper for tags in the editing state
 */
data class EditableTag(
    val tag: ImageTag,
    val isDragging: Boolean = false,
    val isEditing: Boolean = false,
    val isSelected: Boolean = false
)

/**
 * Error state information
 */
data class ErrorState(
    val type: ErrorType,
    val message: String,
    val isRetryable: Boolean = true,
    val canSkip: Boolean = false
)

/**
 * Types of errors that can occur
 */
enum class ErrorType {
    PERMISSION_DENIED,
    WHISPER_FAILED,
    GEMMA_FAILED,
    CAMERA_FAILED,
    NETWORK_ERROR,
    DB_ERROR,
    UNKNOWN
}

/**
 * Camera capture result
 */
data class CameraCaptureResult(
    val uri: Uri,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Tag position update
 */
data class TagPosition(
    val tagId: String,
    val x: Float,
    val y: Float
)
