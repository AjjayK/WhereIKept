package com.whereikept.app.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Helper class for speech recognition functionality.
 */
class SpeechRecognitionHelper(private val context: Context) {
    
    sealed class RecognitionState {
        object Idle : RecognitionState()
        object Listening : RecognitionState()
        object Processing : RecognitionState()
        data class Result(val text: String) : RecognitionState()
        data class Error(val message: String) : RecognitionState()
    }
    
    private val _state = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val state: StateFlow<RecognitionState> = _state.asStateFlow()
    
    private val _partialResults = MutableStateFlow("")
    val partialResults: StateFlow<String> = _partialResults.asStateFlow()
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var shouldKeepListening = false
    private var accumulatedText = StringBuilder()
    
    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
            _state.value = RecognitionState.Listening
        }
        
        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Beginning of speech")
        }
        
        override fun onRmsChanged(rmsdB: Float) {
            // Can be used for audio level visualization
        }
        
        override fun onBufferReceived(buffer: ByteArray?) {}
        
        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech")
            _state.value = RecognitionState.Processing
        }
        
        override fun onError(error: Int) {
            val errorMessage = getErrorMessage(error)
            Log.e(TAG, "Recognition error: $errorMessage (code: $error)")

            // If we should keep listening and it's just a "no match" or "timeout" error, restart
            if (shouldKeepListening && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                Log.d(TAG, "Auto-restarting recognition (no speech detected)")
                isListening = false
                restartListening()
            } else {
                _state.value = RecognitionState.Error(errorMessage)
                isListening = false
                shouldKeepListening = false
            }
        }
        
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            Log.d(TAG, "Final result: $text")

            // Accumulate text if we're in continuous mode
            if (shouldKeepListening && text.isNotBlank()) {
                if (accumulatedText.isNotEmpty()) {
                    accumulatedText.append(" ")
                }
                accumulatedText.append(text)
                Log.d(TAG, "Accumulated text: $accumulatedText")

                // Update partial results with accumulated text
                _partialResults.value = accumulatedText.toString()

                // Restart listening for more input
                isListening = false
                restartListening()
            } else {
                // Single-shot mode or final result
                _state.value = RecognitionState.Result(text)
                isListening = false
                shouldKeepListening = false
            }
        }
        
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""

            // Show accumulated text + current partial
            if (shouldKeepListening && accumulatedText.isNotEmpty()) {
                _partialResults.value = accumulatedText.toString() + " " + text
            } else {
                _partialResults.value = text
            }
        }
        
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
    
    fun initialize() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
            }
            Log.d(TAG, "Speech recognizer initialized")
        } else {
            Log.e(TAG, "Speech recognition not available")
            _state.value = RecognitionState.Error("Speech recognition not available on this device")
        }
    }
    
    fun startListening() {
        if (isListening) {
            Log.d(TAG, "Already listening")
            return
        }

        if (speechRecognizer == null) {
            initialize()
        }

        // Enable continuous listening mode
        shouldKeepListening = true
        accumulatedText.clear()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Extended listening for longer recordings
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }

        try {
            _partialResults.value = ""
            speechRecognizer?.startListening(intent)
            isListening = true
            Log.d(TAG, "Started continuous listening")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition: ${e.message}")
            _state.value = RecognitionState.Error("Failed to start speech recognition")
            shouldKeepListening = false
        }
    }

    private fun restartListening() {
        if (!shouldKeepListening) {
            Log.d(TAG, "Not restarting - shouldKeepListening is false")
            return
        }

        Log.d(TAG, "Restarting recognition...")

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            _state.value = RecognitionState.Listening
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting speech recognition: ${e.message}")
            _state.value = RecognitionState.Error("Failed to restart speech recognition")
            shouldKeepListening = false
        }
    }
    
    fun stopListening() {
        shouldKeepListening = false // Stop continuous mode

        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            Log.d(TAG, "Stopped listening")
        }

        // Return accumulated text as final result
        if (accumulatedText.isNotEmpty()) {
            val finalText = accumulatedText.toString()
            Log.d(TAG, "Final accumulated text: $finalText")
            _state.value = RecognitionState.Result(finalText)
            accumulatedText.clear()
        }
    }
    
    fun cancelListening() {
        shouldKeepListening = false
        speechRecognizer?.cancel()
        isListening = false
        _state.value = RecognitionState.Idle
        _partialResults.value = ""
        accumulatedText.clear()
        Log.d(TAG, "Cancelled listening")
    }
    
    fun resetState() {
        shouldKeepListening = false
        accumulatedText.clear()
        _state.value = RecognitionState.Idle
        _partialResults.value = ""
    }
    
    fun destroy() {
        shouldKeepListening = false
        accumulatedText.clear()
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
        Log.d(TAG, "Speech recognizer destroyed")
    }
    
    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Please try again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input. Please try again."
            else -> "Unknown error"
        }
    }
    
    companion object {
        private const val TAG = "SpeechRecognitionHelper"
    }
}
