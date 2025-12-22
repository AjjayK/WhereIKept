package com.whereikept.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whereikept.app.data.*
import com.whereikept.app.utils.SpeechRecognitionHelper
import com.whereikept.app.utils.LlmService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = WhereIKeptDatabase.getDatabase(application)
    private val repository = WhereIKeptRepository(
        database.itemDao(),
        database.recordingDao(),
        database.imageDao()
    )

    val speechHelper = SpeechRecognitionHelper(application)
    private val llmService = LlmService(application)

    // UI State
    data class UiState(
        val isRecording: Boolean = false,
        val transcribedText: String = "",
        val recordingDuration: Long = 0L,
        val audioLevel: Float = 0f,
        val isProcessing: Boolean = false,
        val searchQuery: String = "",
        val searchResults: List<ItemEntity> = emptyList(),
        val error: String? = null,
        val successMessage: String? = null,
        val itemCount: Int = 0
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val allItems: StateFlow<List<ItemEntity>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Initialize speech helper and LLM on background thread to avoid blocking UI
        viewModelScope.launch(Dispatchers.IO) {
            speechHelper.initialize()
            llmService.initialize()
        }

        // Observe speech recognition state
        viewModelScope.launch {
            speechHelper.state.collect { state ->
                when (state) {
                    is SpeechRecognitionHelper.RecognitionState.Idle -> {
                        _uiState.update { it.copy(isRecording = false) }
                    }
                    is SpeechRecognitionHelper.RecognitionState.Recording -> {
                        _uiState.update { it.copy(isRecording = true, error = null) }
                    }
                    is SpeechRecognitionHelper.RecognitionState.Processing -> {
                        _uiState.update { it.copy(isRecording = false, isProcessing = true) }
                    }
                    is SpeechRecognitionHelper.RecognitionState.Result -> {
                        _uiState.update {
                            it.copy(
                                isRecording = false,
                                transcribedText = state.text,
                                recordingDuration = 0L,
                                audioLevel = 0f
                            )
                        }
                        if (state.text.isNotBlank()) {
                            processTranscription(state.text)
                        }
                    }
                    is SpeechRecognitionHelper.RecognitionState.Error -> {
                        _uiState.update {
                            it.copy(
                                isRecording = false,
                                isProcessing = false,
                                error = state.message
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
                _uiState.update { it.copy(recordingDuration = duration) }
            }
        }

        // Observe audio level
        viewModelScope.launch {
            speechHelper.audioLevel.collect { level ->
                _uiState.update { it.copy(audioLevel = level) }
            }
        }

        // Update item count
        viewModelScope.launch {
            allItems.collect { items ->
                _uiState.update { it.copy(itemCount = items.size) }
            }
        }
    }

    fun startRecording() {
        speechHelper.startRecording()
    }

    fun stopRecording() {
        speechHelper.stopRecording()
    }

    fun cancelRecording() {
        speechHelper.cancelRecording()
        _uiState.update { it.copy(transcribedText = "", recordingDuration = 0L, audioLevel = 0f) }
    }

    private fun processTranscription(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }

            try {
                val recording = RecordingEntity(
                    transcription = text,
                    timestamp = System.currentTimeMillis()
                )
                val recordingId = repository.insertRecording(recording)

                // Use LLM for extraction instead of regex
                Log.d(TAG, "Starting LLM extraction for: \"$text\"")
                val extractionResponse = llmService.extractItemsFromTranscription(text)

                // VERIFICATION: Log the actual extraction response
                Log.i(TAG, "=== LLM EXTRACTION VERIFICATION ===")
                Log.i(TAG, "Extraction completed successfully")
                Log.i(TAG, "Items count: ${extractionResponse.items.size}")
                Log.i(TAG, "Full response: ${com.google.gson.Gson().toJson(extractionResponse)}")
                Log.i(TAG, "====================================")

                if (extractionResponse.items.isNotEmpty()) {
                    // Map LLM response to ItemEntity with new fields
                    val items = extractionResponse.items.map { extracted ->
                        ItemEntity(
                            objectName = extracted.objectName,
                            location = extracted.location,
                            description = "From recording: \"$text\"",
                            nearby = extracted.nearby,
                            timeHint = extracted.timeHint,
                            confidence = extracted.confidence,
                            evidence = extracted.evidence,
                            sourceType = "voice"
                        )
                    }
                    repository.insertItems(items)

                    // Store JSON response as processedText
                    repository.updateRecording(recording.copy(
                        id = recordingId,
                        isProcessed = true,
                        processedText = com.google.gson.Gson().toJson(extractionResponse)
                    ))

                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            successMessage = "Saved ${extractionResponse.items.size} item(s)!",
                            transcribedText = ""
                        )
                    }

                    Log.d(TAG, "Successfully saved ${extractionResponse.items.size} item(s)")
                } else {
                    Log.w(TAG, "No items extracted from transcription")
                    Log.w(TAG, "LLM returned empty items array - this means LLM ran successfully but found nothing to extract")
                    Log.w(TAG, "Input was: \"$text\"")
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            error = "No items found in the recording. Try saying something like 'I'm putting my keys in the drawer'."
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing transcription: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        error = "Error processing: ${e.message}"
                    )
                }
            }
        }
    }

    fun searchItems(query: String) {
        _uiState.update { it.copy(searchQuery = query) }

        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            try {
                val results = repository.searchItems(query)
                _uiState.update { it.copy(isProcessing = false, searchResults = results) }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching: ${e.message}")
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        error = "Search error: ${e.message}"
                    )
                }
            }
        }
    }

    fun addItemManually(objectName: String, location: String, description: String = "") {
        if (objectName.isBlank() || location.isBlank()) return

        viewModelScope.launch {
            try {
                val item = ItemEntity(
                    objectName = objectName.trim(),
                    location = location.trim(),
                    description = description.trim(),
                    sourceType = "manual"
                )
                repository.insertItem(item)
                _uiState.update { it.copy(successMessage = "Item saved!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to save item: ${e.message}") }
            }
        }
    }

    fun deleteItem(item: ItemEntity) {
        viewModelScope.launch {
            try {
                repository.deleteItem(item)
                _uiState.update { it.copy(successMessage = "Item deleted") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete item: ${e.message}") }
            }
        }
    }

    fun deleteAllItems() {
        viewModelScope.launch {
            try {
                repository.deleteAllItems()
                _uiState.update { it.copy(successMessage = "All items deleted") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete items: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    private data class ExtractedItem(
        val objectName: String,
        val location: String
    )

    private fun extractItemsSimple(text: String): List<ExtractedItem> {
        val patterns = listOf(
            Regex("""(?:put|placing|stored?|keep(?:ing)?|left)\s+(?:my\s+)?(\w+(?:\s+\w+)?)\s+(?:in|on|at|inside|under)\s+(?:the\s+)?(.+?)(?:\.|,|$)""", RegexOption.IGNORE_CASE),
            Regex("""(\w+(?:\s+\w+)?)\s+(?:is|are|goes?)\s+(?:in|on|at|inside|under)\s+(?:the\s+)?(.+?)(?:\.|,|$)""", RegexOption.IGNORE_CASE)
        )

        val results = mutableListOf<ExtractedItem>()
        for (pattern in patterns) {
            pattern.findAll(text).forEach { match ->
                val objectName = match.groupValues.getOrNull(1)?.trim() ?: return@forEach
                val location = match.groupValues.getOrNull(2)?.trim() ?: return@forEach
                if (objectName.isNotEmpty() && location.isNotEmpty()) {
                    results.add(ExtractedItem(objectName, location))
                }
            }
        }
        return results.distinctBy { it.objectName.lowercase() }
    }

    override fun onCleared() {
        super.onCleared()
        speechHelper.destroy()
        llmService.release()
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}
