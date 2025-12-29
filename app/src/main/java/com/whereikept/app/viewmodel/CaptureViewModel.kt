package com.whereikept.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whereikept.app.data.*
import com.whereikept.app.utils.SpeechRecognitionHelper
import com.whereikept.app.utils.LlmService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for the enhanced capture workflow
 * Manages state machine for: Record → Capture Image → Analyze → Edit Tags → Save
 */
class CaptureViewModel(application: Application) : AndroidViewModel(application) {

    private val database = WhereIKeptDatabase.getDatabase(application)
    private val repository = WhereIKeptRepository(
        database.itemDao(),
        database.recordingDao(),
        database.imageDao()
    )

    val speechHelper = SpeechRecognitionHelper(application)
    private val llmService = LlmService(application)

    // UI State
    private val _captureUiState = MutableStateFlow(CaptureUiState())
    val captureUiState: StateFlow<CaptureUiState> = _captureUiState.asStateFlow()

    val allItems: StateFlow<List<ItemEntity>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Initialize services on background thread
        viewModelScope.launch(Dispatchers.IO) {
            speechHelper.initialize()
            llmService.initialize()
        }

        // Observe speech recognition state
        viewModelScope.launch {
            speechHelper.state.collect { state ->
                when (state) {
                    is SpeechRecognitionHelper.RecognitionState.Idle -> {
                        _captureUiState.update { it.copy(isRecording = false) }
                    }
                    is SpeechRecognitionHelper.RecognitionState.Recording -> {
                        _captureUiState.update {
                            it.copy(
                                isRecording = true,
                                workflowState = CaptureWorkflowState.RECORDING,
                                error = null
                            )
                        }
                    }
                    is SpeechRecognitionHelper.RecognitionState.Processing -> {
                        // Transcription started
                        _captureUiState.update {
                            it.copy(
                                isRecording = false,
                                isTranscribing = true
                            )
                        }
                    }
                    is SpeechRecognitionHelper.RecognitionState.Result -> {
                        // Transcription completed
                        _captureUiState.update {
                            it.copy(
                                transcriptionText = state.text,
                                transcriptionProgress = 1f,
                                isTranscribing = false
                            )
                        }

                        // If image already captured, move to analysis
                        if (_captureUiState.value.capturedImageUri != null) {
                            analyzeWithGemma()
                        }
                    }
                    is SpeechRecognitionHelper.RecognitionState.Error -> {
                        _captureUiState.update {
                            it.copy(
                                workflowState = CaptureWorkflowState.ERROR,
                                error = ErrorState(
                                    type = ErrorType.WHISPER_FAILED,
                                    message = state.message,
                                    isRetryable = true
                                ),
                                isRecording = false,
                                isTranscribing = false
                            )
                        }
                    }
                    else -> {}
                }
            }
        }

        // Observe recording duration
        viewModelScope.launch {
            speechHelper.recordingDuration.collect { duration ->
                _captureUiState.update { it.copy(recordingDuration = duration) }
            }
        }

        // Observe audio level
        viewModelScope.launch {
            speechHelper.audioLevel.collect { level ->
                _captureUiState.update { it.copy(audioLevel = level) }
            }
        }
    }

    // ========================================
    // Workflow State Transitions
    // ========================================

    fun startRecording() {
        Log.d(TAG, "Starting recording workflow")
        speechHelper.startRecording()
    }

    fun stopRecording() {
        Log.d(TAG, "Stopping recording, transitioning to IMAGE_CAPTURE")
        speechHelper.stopRecording()

        _captureUiState.update {
            it.copy(
                workflowState = CaptureWorkflowState.IMAGE_CAPTURE,
                isRecording = false
            )
        }
    }

    fun cancelRecording() {
        Log.d(TAG, "Canceling recording workflow")
        speechHelper.cancelRecording()
        resetToIdle()
    }

    fun onImageCaptured(uri: Uri) {
        Log.d(TAG, "Image captured: $uri")

        _captureUiState.update {
            it.copy(
                capturedImageUri = uri,
                workflowState = if (it.transcriptionText.isNotBlank()) {
                    CaptureWorkflowState.ANALYZING
                } else {
                    CaptureWorkflowState.TRANSCRIBING
                }
            )
        }

        // If transcription already done, proceed to analysis
        if (_captureUiState.value.transcriptionText.isNotBlank()) {
            analyzeWithGemma()
        }
    }

    fun skipImage() {
        Log.d(TAG, "Skipping image capture")

        _captureUiState.update {
            it.copy(
                capturedImageUri = null,
                workflowState = if (it.transcriptionText.isNotBlank()) {
                    // If transcription done, analyze text only
                    CaptureWorkflowState.ANALYZING
                } else {
                    CaptureWorkflowState.TRANSCRIBING
                }
            )
        }

        // If transcription done, analyze without image
        if (_captureUiState.value.transcriptionText.isNotBlank()) {
            analyzeWithGemma()
        }
    }

    private fun analyzeWithGemma() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _captureUiState.update {
                    it.copy(
                        workflowState = CaptureWorkflowState.ANALYZING,
                        isProcessing = true,
                        processingMessage = "Analyzing with Gemma 3N..."
                    )
                }

                val transcript = _captureUiState.value.transcriptionText
                val imageUri = _captureUiState.value.capturedImageUri

                Log.d(TAG, "Analyzing: transcript='$transcript', imageUri=$imageUri")

                val extractionResponse = if (imageUri != null) {
                    llmService.extractItemsWithImage(transcript, imageUri)
                } else {
                    llmService.extractItemsFromTranscription(transcript)
                }

                // Convert to ImageTags
                val imageTags = extractionResponse.items.map { item ->
                    ImageTag(
                        text = "${item.objectName} → ${item.location}",
                        objectName = item.objectName,
                        location = item.location,
                        confidence = item.confidence,
                        evidence = item.evidence
                    )
                }

                // All tags start as unplaced
                val editableTags = imageTags.map { EditableTag(tag = it) }

                _captureUiState.update {
                    it.copy(
                        workflowState = CaptureWorkflowState.REVIEW_EDITING,
                        extractedTags = imageTags,
                        editableTags = editableTags,
                        unplacedTags = imageTags,
                        isProcessing = false,
                        processingMessage = ""
                    )
                }

                Log.d(TAG, "Analysis complete, extracted ${imageTags.size} tags")

            } catch (e: Exception) {
                Log.e(TAG, "Error during analysis: ${e.message}", e)
                _captureUiState.update {
                    it.copy(
                        workflowState = CaptureWorkflowState.ERROR,
                        error = ErrorState(
                            type = ErrorType.GEMMA_FAILED,
                            message = "Analysis failed: ${e.message}",
                            isRetryable = true,
                            canSkip = true
                        ),
                        isProcessing = false
                    )
                }
            }
        }
    }

    // ========================================
    // Tag Management
    // ========================================

    fun updateTagPosition(tagId: String, x: Float, y: Float) {
        _captureUiState.update { state ->
            val updatedEditableTags = state.editableTags.map { editableTag ->
                if (editableTag.tag.id == tagId) {
                    editableTag.copy(
                        tag = editableTag.tag.copy(
                            positionX = x,
                            positionY = y
                        )
                    )
                } else {
                    editableTag
                }
            }

            // Update unplaced tags list (remove tag if it now has position)
            val unplaced = updatedEditableTags
                .filter { it.tag.positionX == null || it.tag.positionY == null }
                .map { it.tag }

            state.copy(
                editableTags = updatedEditableTags,
                unplacedTags = unplaced
            )
        }
    }

    fun selectTag(tagId: String) {
        _captureUiState.update { state ->
            state.copy(selectedTagId = tagId)
        }
    }

    fun updateTagText(tagId: String, newText: String) {
        _captureUiState.update { state ->
            state.copy(
                editableTags = state.editableTags.map { editableTag ->
                    if (editableTag.tag.id == tagId) {
                        // Parse new text
                        val parts = newText.split("→").map { it.trim() }
                        val objectName = parts.getOrNull(0) ?: newText
                        val location = parts.getOrNull(1) ?: ""

                        editableTag.copy(
                            tag = editableTag.tag.copy(
                                text = newText,
                                objectName = objectName,
                                location = location
                            )
                        )
                    } else {
                        editableTag
                    }
                }
            )
        }
    }

    fun deleteTag(tagId: String) {
        _captureUiState.update { state ->
            val updatedTags = state.editableTags.filter { it.tag.id != tagId }
            val unplaced = updatedTags
                .filter { it.tag.positionX == null || it.tag.positionY == null }
                .map { it.tag }

            state.copy(
                editableTags = updatedTags,
                unplacedTags = unplaced,
                selectedTagId = if (state.selectedTagId == tagId) null else state.selectedTagId
            )
        }
    }

    fun addNewTag(text: String) {
        val parts = text.split("→").map { it.trim() }
        val objectName = parts.getOrNull(0) ?: text
        val location = parts.getOrNull(1) ?: ""

        val newTag = ImageTag(
            text = text,
            objectName = objectName,
            location = location,
            confidence = 1.0f // User-added tags have full confidence
        )

        _captureUiState.update { state ->
            val newEditableTag = EditableTag(tag = newTag)
            state.copy(
                editableTags = state.editableTags + newEditableTag,
                unplacedTags = state.unplacedTags + newTag
            )
        }
    }

    fun showAddTagDialog() {
        _captureUiState.update { it.copy(showAddTagDialog = true) }
    }

    fun hideAddTagDialog() {
        _captureUiState.update { it.copy(showAddTagDialog = false) }
    }

    fun showEditTagDialog(tagId: String) {
        _captureUiState.update {
            it.copy(
                showEditTagDialog = true,
                selectedTagId = tagId
            )
        }
    }

    fun hideEditTagDialog() {
        _captureUiState.update {
            it.copy(
                showEditTagDialog = false,
                selectedTagId = null
            )
        }
    }

    // ========================================
    // Save & Submit
    // ========================================

    fun saveAndSubmit() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _captureUiState.update {
                    it.copy(
                        workflowState = CaptureWorkflowState.SUBMITTING,
                        isProcessing = true,
                        processingMessage = "Saving items..."
                    )
                }

                val transcript = _captureUiState.value.transcriptionText
                val imageUri = _captureUiState.value.capturedImageUri
                val tags = _captureUiState.value.editableTags.map { it.tag }

                Log.d(TAG, "Saving ${tags.size} items to database")

                // Convert tags to database items
                val items = tags.map { tag ->
                    ItemEntity(
                        objectName = tag.objectName.ifBlank { tag.text },
                        location = tag.location,
                        description = "From capture: \"$transcript\"",
                        confidence = tag.confidence,
                        evidence = tag.evidence,
                        imagePath = imageUri?.toString(),
                        sourceType = "capture"
                    )
                }

                // Save to database
                repository.insertItems(items)

                // Save recording
                val recording = RecordingEntity(
                    transcription = transcript,
                    isProcessed = true,
                    processedText = items.joinToString("\n") { "${it.objectName} -> ${it.location}" }
                )
                repository.insertRecording(recording)

                // Save image reference if present
                if (imageUri != null) {
                    val imageEntity = ImageEntity(
                        imagePath = imageUri.toString(),
                        summary = items.joinToString(", ") { it.objectName },
                        isProcessed = true
                    )
                    repository.insertImage(imageEntity)
                }

                val summary = items.joinToString("\n") { "• ${it.objectName} → ${it.location}" }

                _captureUiState.update {
                    it.copy(
                        workflowState = CaptureWorkflowState.SUCCESS,
                        savedItemsCount = items.size,
                        savedItemsSummary = summary,
                        isProcessing = false,
                        processingMessage = ""
                    )
                }

                Log.d(TAG, "Successfully saved ${items.size} items")

                // Auto-reset after 3 seconds
                delay(3000)
                resetToIdle()

            } catch (e: Exception) {
                Log.e(TAG, "Error saving items: ${e.message}", e)
                _captureUiState.update {
                    it.copy(
                        workflowState = CaptureWorkflowState.ERROR,
                        error = ErrorState(
                            type = ErrorType.DB_ERROR,
                            message = "Failed to save: ${e.message}",
                            isRetryable = true
                        ),
                        isProcessing = false
                    )
                }
            }
        }
    }

    // ========================================
    // Error Handling & Navigation
    // ========================================

    fun retryFromError() {
        val errorType = _captureUiState.value.error?.type

        when (errorType) {
            ErrorType.WHISPER_FAILED -> {
                // Retry transcription
                _captureUiState.update {
                    it.copy(
                        error = null,
                        workflowState = CaptureWorkflowState.IMAGE_CAPTURE
                    )
                }
            }
            ErrorType.GEMMA_FAILED -> {
                // Retry analysis
                _captureUiState.update { it.copy(error = null) }
                analyzeWithGemma()
            }
            ErrorType.DB_ERROR -> {
                // Retry save
                _captureUiState.update { it.copy(error = null) }
                saveAndSubmit()
            }
            else -> {
                resetToIdle()
            }
        }
    }

    fun skipError() {
        when (_captureUiState.value.error?.type) {
            ErrorType.GEMMA_FAILED -> {
                // Skip to manual tag entry
                _captureUiState.update {
                    it.copy(
                        workflowState = CaptureWorkflowState.REVIEW_EDITING,
                        error = null,
                        editableTags = emptyList(),
                        unplacedTags = emptyList()
                    )
                }
            }
            else -> resetToIdle()
        }
    }

    fun resetToIdle() {
        Log.d(TAG, "Resetting to IDLE state")
        speechHelper.resetState()
        _captureUiState.value = CaptureUiState()
    }

    override fun onCleared() {
        super.onCleared()
        speechHelper.destroy()
        llmService.release()
    }

    companion object {
        private const val TAG = "CaptureViewModel"
    }
}
