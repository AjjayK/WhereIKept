package com.whereikept.app.utils

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Audio recorder for speech recognition using on-device LLM transcription.
 * Records audio and passes it to an on-device model for transcription.
 */
class SpeechRecognitionHelper(private val context: Context) {

    sealed class RecognitionState {
        object Idle : RecognitionState()
        object Recording : RecognitionState()
        object Processing : RecognitionState()
        data class Result(val text: String) : RecognitionState()
        data class Error(val message: String) : RecognitionState()
    }

    private val _state = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val state: StateFlow<RecognitionState> = _state.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private var audioData = mutableListOf<ByteArray>()
    private var recordingStartTime = 0L

    private val scope = CoroutineScope(Dispatchers.Default)

    // Whisper model for transcription
    private var whisper: LibWhisper? = null
    private var isWhisperInitialized = false

    // Audio recording settings
    private val sampleRate = 16000 // 16kHz for speech
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

    fun initialize() {
        Log.i(TAG, "===========================================")
        Log.i(TAG, "Initializing SpeechRecognitionHelper")
        Log.i(TAG, "===========================================")

        try {
            // Initialize AudioRecord
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                _state.value = RecognitionState.Error("Failed to initialize audio recorder")
            } else {
                Log.i(TAG, "✓ AudioRecord initialized successfully")
                Log.i(TAG, "  Sample rate: $sampleRate Hz")
                Log.i(TAG, "  Channels: Mono")
                Log.i(TAG, "  Format: PCM 16-bit")
                Log.i(TAG, "  Buffer size: $bufferSize bytes")
            }

            // Initialize Whisper model
            initializeWhisper()

        } catch (e: SecurityException) {
            Log.e(TAG, "ERROR: Missing microphone permission: ${e.message}")
            _state.value = RecognitionState.Error("Microphone permission required")
        } catch (e: Exception) {
            Log.e(TAG, "ERROR initializing audio recorder: ${e.message}", e)
            _state.value = RecognitionState.Error("Failed to initialize audio recorder: ${e.message}")
        }
    }

    private fun initializeWhisper() {
        Log.i(TAG, "-------------------------------------------")
        Log.i(TAG, "Initializing Whisper model...")

        try {
            // Look for Whisper model in app's files directory
            val modelFileName = "ggml-small.en-q5_1.bin"
            val modelPath = File(context.filesDir, modelFileName)

            if (!modelPath.exists()) {
                Log.w(TAG, "Whisper model not found at: ${modelPath.absolutePath}")
                Log.w(TAG, "Please download model to: ${modelPath.absolutePath}")
                Log.w(TAG, "Download from: https://huggingface.co/ggerganov/whisper.cpp/tree/main")
                Log.w(TAG, "Transcription will use placeholder until model is available")
                isWhisperInitialized = false
                return
            }

            whisper = LibWhisper(context)
            isWhisperInitialized = whisper?.initialize(modelPath.absolutePath) ?: false

            if (isWhisperInitialized) {
                Log.i(TAG, "✓ Whisper model initialized successfully")
            } else {
                Log.w(TAG, "✗ Whisper initialization failed")
            }

        } catch (e: Exception) {
            Log.e(TAG, "ERROR initializing Whisper: ${e.message}", e)
            isWhisperInitialized = false
        }

        Log.i(TAG, "-------------------------------------------")
    }

    fun startRecording() {
        Log.i(TAG, "===========================================")
        Log.i(TAG, "Starting Audio Recording")
        Log.i(TAG, "===========================================")

        if (isRecording) {
            Log.w(TAG, "Already recording - ignoring request")
            return
        }

        if (audioRecord == null || audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.i(TAG, "AudioRecord not initialized, initializing now...")
            initialize()
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "ERROR: Audio recorder not initialized")
            _state.value = RecognitionState.Error("Audio recorder not initialized")
            return
        }

        audioData.clear()
        recordingStartTime = System.currentTimeMillis()
        _recordingDuration.value = 0L
        _audioLevel.value = 0f

        try {
            audioRecord?.startRecording()
            isRecording = true
            _state.value = RecognitionState.Recording
            Log.i(TAG, "✓ Recording started successfully")
            Log.i(TAG, "  Start time: $recordingStartTime")
            Log.i(TAG, "  Waiting for audio input...")

            recordingJob = scope.launch {
                recordAudio()
            }
        } catch (e: Exception) {
            Log.e(TAG, "ERROR starting recording: ${e.message}", e)
            _state.value = RecognitionState.Error("Failed to start recording: ${e.message}")
            isRecording = false
        }
    }

    private suspend fun recordAudio() = withContext(Dispatchers.IO) {
        val buffer = ByteArray(bufferSize)

        while (isRecording) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0

            if (read > 0) {
                // Store audio data
                val data = buffer.copyOf(read)
                audioData.add(data)

                // Calculate audio level for visualization
                val level = calculateAudioLevel(buffer, read)
                _audioLevel.value = level

                // Update recording duration
                val duration = System.currentTimeMillis() - recordingStartTime
                _recordingDuration.value = duration
            }
        }
    }

    private fun calculateAudioLevel(buffer: ByteArray, read: Int): Float {
        var sum = 0.0
        for (i in 0 until read step 2) {
            val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
            sum += sample * sample
        }
        val rms = Math.sqrt(sum / (read / 2))
        return (rms / Short.MAX_VALUE).toFloat()
    }

    fun stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "Not currently recording - ignoring stop request")
            return
        }

        Log.i(TAG, "===========================================")
        Log.i(TAG, "Stopping Audio Recording")
        Log.i(TAG, "===========================================")

        val recordingDuration = System.currentTimeMillis() - recordingStartTime
        Log.i(TAG, "Recording duration: ${recordingDuration / 1000.0} seconds")
        Log.i(TAG, "Audio chunks captured: ${audioData.size}")

        isRecording = false

        try {
            audioRecord?.stop()
            recordingJob?.cancel()
            Log.i(TAG, "✓ Recording stopped successfully")

            _state.value = RecognitionState.Processing
            Log.i(TAG, "State changed to: Processing")
            Log.i(TAG, "Starting audio processing and transcription...")

            // Process the recorded audio
            scope.launch {
                processRecording()
            }
        } catch (e: Exception) {
            Log.e(TAG, "ERROR stopping recording: ${e.message}", e)
            _state.value = RecognitionState.Error("Failed to stop recording: ${e.message}")
        }
    }

    private suspend fun processRecording() = withContext(Dispatchers.IO) {
        Log.i(TAG, "===========================================")
        Log.i(TAG, "Processing Recorded Audio")
        Log.i(TAG, "===========================================")

        try {
            if (audioData.isEmpty()) {
                Log.w(TAG, "WARNING: No audio data recorded")
                withContext(Dispatchers.Main) {
                    _state.value = RecognitionState.Error("No audio recorded")
                }
                return@withContext
            }

            Log.i(TAG, "Processing ${audioData.size} audio chunks")

            // Combine all audio data into a single byte array
            val totalSize = audioData.sumOf { it.size }
            Log.i(TAG, "Total audio data size: $totalSize bytes (${totalSize / 1024} KB)")

            val combinedAudio = ByteArray(totalSize)
            var offset = 0
            audioData.forEach { chunk ->
                System.arraycopy(chunk, 0, combinedAudio, offset, chunk.size)
                offset += chunk.size
            }
            Log.i(TAG, "✓ Audio chunks combined successfully")

            // Save to temporary file for LLM processing
            val audioFile = saveAudioToFile(combinedAudio)
            Log.i(TAG, "✓ Audio saved to WAV file")
            Log.i(TAG, "  Path: ${audioFile.absolutePath}")
            Log.i(TAG, "  Size: ${audioFile.length()} bytes (${audioFile.length() / 1024} KB)")

            // Transcribe using on-device LLM
            val transcription = transcribeWithLLM(audioFile)

            Log.i(TAG, "===========================================")
            Log.i(TAG, "Updating State with Results")
            Log.i(TAG, "===========================================")
            Log.i(TAG, "Final transcription: \"$transcription\"")

            withContext(Dispatchers.Main) {
                _state.value = RecognitionState.Result(transcription)
                Log.i(TAG, "✓ State updated to: Result")
            }

            // Clean up temporary file
            val deleted = audioFile.delete()
            if (deleted) {
                Log.i(TAG, "✓ Temporary WAV file deleted")
            } else {
                Log.w(TAG, "WARNING: Failed to delete temporary WAV file")
            }

        } catch (e: Exception) {
            Log.e(TAG, "ERROR processing recording: ${e.message}", e)
            withContext(Dispatchers.Main) {
                _state.value = RecognitionState.Error("Failed to process recording: ${e.message}")
            }
        } finally {
            audioData.clear()
            Log.i(TAG, "✓ Audio buffer cleared")
            Log.i(TAG, "===========================================")
        }
    }

    private fun saveAudioToFile(audioData: ByteArray): File {
        val file = File(context.cacheDir, "recording_${System.currentTimeMillis()}.wav")

        FileOutputStream(file).use { fos ->
            // Write WAV header
            writeWavHeader(fos, audioData.size)

            // Write audio data
            fos.write(audioData)
        }

        return file
    }

    private fun writeWavHeader(fos: FileOutputStream, audioDataSize: Int) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        header.put("RIFF".toByteArray())
        header.putInt(36 + audioDataSize) // File size - 8
        header.put("WAVE".toByteArray())

        // fmt subchunk
        header.put("fmt ".toByteArray())
        header.putInt(16) // Subchunk size
        header.putShort(1) // Audio format (1 = PCM)
        header.putShort(1) // Number of channels (1 = mono)
        header.putInt(sampleRate) // Sample rate
        header.putInt(sampleRate * 2) // Byte rate
        header.putShort(2) // Block align
        header.putShort(16) // Bits per sample

        // data subchunk
        header.put("data".toByteArray())
        header.putInt(audioDataSize)

        fos.write(header.array())
    }

    private suspend fun transcribeWithLLM(audioFile: File): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "===========================================")
        Log.i(TAG, "Starting Transcription")
        Log.i(TAG, "===========================================")
        Log.i(TAG, "Audio file: ${audioFile.name}")
        Log.i(TAG, "File path: ${audioFile.absolutePath}")
        Log.i(TAG, "File size: ${audioFile.length()} bytes (${audioFile.length() / 1024} KB)")
        Log.i(TAG, "Sample rate: $sampleRate Hz")
        Log.i(TAG, "Channels: Mono")
        Log.i(TAG, "Format: PCM 16-bit WAV")

        val audioDurationSeconds = (audioFile.length() - 44) / (sampleRate * 2) // 2 bytes per sample
        Log.i(TAG, "Audio duration: ~$audioDurationSeconds seconds")
        Log.i(TAG, "-------------------------------------------")

        try {
            if (!isWhisperInitialized || whisper == null) {
                Log.w(TAG, "Whisper model not initialized")
                Log.w(TAG, "Using placeholder transcription")
                Log.w(TAG, "To use real transcription:")
                Log.w(TAG, "  1. Download ggml-small.en-q5_1.bin from HuggingFace")
                Log.w(TAG, "  2. Place it in: ${context.filesDir}/ggml-small.en-q5_1.bin")
                Log.w(TAG, "  3. Restart the app")
                return@withContext "[Placeholder: Whisper model not loaded. See logs for setup instructions.]"
            }

            Log.i(TAG, "Using Whisper model for transcription")
            val transcriptionStartTime = System.currentTimeMillis()

            val transcription = whisper?.transcribeFromWav(audioFile) ?: ""

            val transcriptionEndTime = System.currentTimeMillis()
            val transcriptionDuration = transcriptionEndTime - transcriptionStartTime

            Log.i(TAG, "-------------------------------------------")
            Log.i(TAG, "Transcription Results:")
            Log.i(TAG, "  Processing time: $transcriptionDuration ms")
            Log.i(TAG, "  Transcription length: ${transcription.length} characters")
            Log.i(TAG, "  Transcription: \"$transcription\"")
            Log.i(TAG, "===========================================")

            if (transcription.isBlank()) {
                Log.w(TAG, "WARNING: Transcription is empty!")
                Log.w(TAG, "Possible causes:")
                Log.w(TAG, "  - Audio is too quiet or silent")
                Log.w(TAG, "  - Model failed to detect speech")
                Log.w(TAG, "  - Model incompatibility issue")
                return@withContext "[No speech detected in audio]"
            }

            return@withContext transcription

        } catch (e: Exception) {
            Log.e(TAG, "ERROR during transcription", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Stack trace:")
            e.printStackTrace()
            return@withContext "[Transcription failed: ${e.message}]"
        }
    }

    fun cancelRecording() {
        isRecording = false
        audioRecord?.stop()
        recordingJob?.cancel()
        audioData.clear()
        _state.value = RecognitionState.Idle
        _recordingDuration.value = 0L
        _audioLevel.value = 0f
        Log.d(TAG, "Recording cancelled")
    }

    fun resetState() {
        _state.value = RecognitionState.Idle
        _recordingDuration.value = 0L
        _audioLevel.value = 0f
    }

    fun destroy() {
        Log.i(TAG, "===========================================")
        Log.i(TAG, "Destroying SpeechRecognitionHelper")
        Log.i(TAG, "===========================================")

        cancelRecording()

        audioRecord?.release()
        audioRecord = null
        Log.i(TAG, "✓ AudioRecord released")

        whisper?.release()
        whisper = null
        isWhisperInitialized = false
        Log.i(TAG, "✓ Whisper resources released")

        Log.i(TAG, "SpeechRecognitionHelper destroyed")
        Log.i(TAG, "===========================================")
    }

    companion object {
        private const val TAG = "SpeechRecognitionHelper"
    }
}
