package com.whereikept.app.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Kotlin wrapper for Whisper JNI interface
 * Provides offline speech-to-text transcription using Whisper model
 */
class LibWhisper(private val context: Context) {

    companion object {
        private const val TAG = "LibWhisper"

        private var isLibraryLoaded = false

        init {
            try {
                System.loadLibrary("whisper_android")
                isLibraryLoaded = true
                Log.i(TAG, "Whisper native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load Whisper native library", e)
                Log.e(TAG, "The native library has not been compiled yet.")
                Log.e(TAG, "Build the app to compile the native code, or disable Whisper for now.")
                isLibraryLoaded = false
                // Don't throw - let the app continue without Whisper
            }
        }
    }

    private var contextPtr: Long = 0
    private var isInitialized = false

    /**
     * Initialize Whisper model from file path
     * @param modelPath Path to the .bin model file (e.g., ggml-small-q5_1.bin)
     * @return true if initialization successful
     */
    fun initialize(modelPath: String): Boolean {
        if (!isLibraryLoaded) {
            Log.e(TAG, "Cannot initialize - native library not loaded")
            return false
        }

        Log.i(TAG, "================================================")
        Log.i(TAG, "Initializing LibWhisper")
        Log.i(TAG, "Model path: $modelPath")

        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            Log.e(TAG, "ERROR: Model file does not exist at path: $modelPath")
            Log.e(TAG, "Please download the model first")
            return false
        }

        Log.i(TAG, "Model file found")
        Log.i(TAG, "Model file size: ${modelFile.length() / 1024 / 1024} MB")

        contextPtr = initContext(modelPath)
        isInitialized = contextPtr != 0L

        if (isInitialized) {
            Log.i(TAG, "LibWhisper initialized successfully!")
            Log.i(TAG, "Context pointer: $contextPtr")
        } else {
            Log.e(TAG, "FAILED to initialize LibWhisper")
        }
        Log.i(TAG, "================================================")

        return isInitialized
    }

    /**
     * Transcribe audio from a WAV file
     * @param wavFile The WAV file to transcribe
     * @return Transcribed text or error message
     */
    fun transcribeFromWav(wavFile: File): String {
        if (!isLibraryLoaded) {
            Log.e(TAG, "ERROR: Native library not loaded")
            return ""
        }

        if (!isInitialized) {
            Log.e(TAG, "ERROR: LibWhisper not initialized! Call initialize() first")
            return ""
        }

        if (!wavFile.exists()) {
            Log.e(TAG, "ERROR: WAV file does not exist: ${wavFile.absolutePath}")
            return ""
        }

        Log.i(TAG, "================================================")
        Log.i(TAG, "Transcribing WAV file: ${wavFile.name}")
        Log.i(TAG, "File size: ${wavFile.length()} bytes")

        try {
            // Read WAV file and convert to float array
            val audioData = readWavFile(wavFile)

            if (audioData.isEmpty()) {
                Log.e(TAG, "ERROR: Failed to read audio data from WAV file")
                return ""
            }

            Log.i(TAG, "Audio data loaded: ${audioData.size} samples")
            Log.i(TAG, "Starting transcription...")

            val startTime = System.currentTimeMillis()
            val transcription = transcribe(contextPtr, audioData)
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            Log.i(TAG, "Transcription completed in $duration ms")
            Log.i(TAG, "Result length: ${transcription.length} characters")
            Log.i(TAG, "================================================")

            return transcription

        } catch (e: Exception) {
            Log.e(TAG, "ERROR during transcription", e)
            Log.e(TAG, "Exception: ${e.message}")
            return ""
        }
    }

    /**
     * Read WAV file and convert to float array for Whisper
     * Expects: 16kHz, mono, PCM 16-bit
     */
    private fun readWavFile(file: File): FloatArray {
        Log.d(TAG, "Reading WAV file...")

        try {
            val bytes = file.readBytes()
            Log.d(TAG, "Total file bytes: ${bytes.size}")

            // Skip WAV header (44 bytes)
            val headerSize = 44
            if (bytes.size <= headerSize) {
                Log.e(TAG, "ERROR: File too small to contain WAV header")
                return floatArrayOf()
            }

            // Extract audio data (PCM 16-bit samples)
            val audioBytes = bytes.copyOfRange(headerSize, bytes.size)
            val numSamples = audioBytes.size / 2 // 2 bytes per 16-bit sample

            Log.d(TAG, "Audio data bytes: ${audioBytes.size}")
            Log.d(TAG, "Number of samples: $numSamples")

            val audioData = FloatArray(numSamples)
            val buffer = ByteBuffer.wrap(audioBytes).order(ByteOrder.LITTLE_ENDIAN)

            // Convert 16-bit PCM to normalized float [-1.0, 1.0]
            for (i in 0 until numSamples) {
                val sample = buffer.getShort(i * 2).toInt()
                audioData[i] = sample / 32768.0f
            }

            // Log statistics
            val maxVal = audioData.maxOrNull() ?: 0f
            val minVal = audioData.minOrNull() ?: 0f
            Log.d(TAG, "Audio converted to float array")
            Log.d(TAG, "Sample range: [$minVal, $maxVal]")

            return audioData

        } catch (e: Exception) {
            Log.e(TAG, "ERROR reading WAV file", e)
            return floatArrayOf()
        }
    }

    /**
     * Release Whisper context and free memory
     */
    fun release() {
        if (isInitialized) {
            Log.i(TAG, "Releasing LibWhisper resources...")
            freeContext(contextPtr)
            contextPtr = 0
            isInitialized = false
            Log.i(TAG, "LibWhisper released")
        }
    }

    // Native methods
    private external fun initContext(modelPath: String): Long
    private external fun transcribe(contextPtr: Long, audioData: FloatArray): String
    private external fun freeContext(contextPtr: Long)
}
