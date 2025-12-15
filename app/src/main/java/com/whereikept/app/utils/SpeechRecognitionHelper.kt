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

    // Audio recording settings
    private val sampleRate = 16000 // 16kHz for speech
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

    fun initialize() {
        try {
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
                Log.d(TAG, "Audio recorder initialized")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing microphone permission: ${e.message}")
            _state.value = RecognitionState.Error("Microphone permission required")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing audio recorder: ${e.message}")
            _state.value = RecognitionState.Error("Failed to initialize audio recorder: ${e.message}")
        }
    }

    fun startRecording() {
        if (isRecording) {
            Log.d(TAG, "Already recording")
            return
        }

        if (audioRecord == null || audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            initialize()
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
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
            Log.d(TAG, "Started recording")

            recordingJob = scope.launch {
                recordAudio()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}")
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
            Log.d(TAG, "Not currently recording")
            return
        }

        Log.d(TAG, "Stopping recording")
        isRecording = false

        try {
            audioRecord?.stop()
            recordingJob?.cancel()

            _state.value = RecognitionState.Processing

            // Process the recorded audio
            scope.launch {
                processRecording()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}")
            _state.value = RecognitionState.Error("Failed to stop recording: ${e.message}")
        }
    }

    private suspend fun processRecording() = withContext(Dispatchers.IO) {
        try {
            if (audioData.isEmpty()) {
                Log.w(TAG, "No audio data recorded")
                withContext(Dispatchers.Main) {
                    _state.value = RecognitionState.Error("No audio recorded")
                }
                return@withContext
            }

            Log.d(TAG, "Processing ${audioData.size} audio chunks")

            // Combine all audio data into a single byte array
            val totalSize = audioData.sumOf { it.size }
            val combinedAudio = ByteArray(totalSize)
            var offset = 0
            audioData.forEach { chunk ->
                System.arraycopy(chunk, 0, combinedAudio, offset, chunk.size)
                offset += chunk.size
            }

            // Save to temporary file for LLM processing
            val audioFile = saveAudioToFile(combinedAudio)
            Log.d(TAG, "Saved audio to: ${audioFile.absolutePath}, size: ${audioFile.length()} bytes")

            // Transcribe using on-device LLM
            val transcription = transcribeWithLLM(audioFile)

            withContext(Dispatchers.Main) {
                _state.value = RecognitionState.Result(transcription)
            }

            // Clean up temporary file
            audioFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing recording: ${e.message}")
            withContext(Dispatchers.Main) {
                _state.value = RecognitionState.Error("Failed to process recording: ${e.message}")
            }
        } finally {
            audioData.clear()
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
        // TODO: Implement on-device LLM transcription
        // This is a placeholder that should be replaced with actual LLM integration
        // Example integration points:
        // - Whisper model integration
        // - TensorFlow Lite model
        // - ONNX Runtime model
        // - Other on-device speech-to-text models

        Log.d(TAG, "TODO: Transcribe audio file with on-device LLM: ${audioFile.absolutePath}")
        Log.d(TAG, "Audio file size: ${audioFile.length()} bytes")
        Log.d(TAG, "Sample rate: $sampleRate Hz")
        Log.d(TAG, "Channels: Mono")
        Log.d(TAG, "Format: PCM 16-bit")

        // Placeholder response
        return@withContext "[Transcription placeholder - LLM integration pending]"
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
        cancelRecording()
        audioRecord?.release()
        audioRecord = null
        Log.d(TAG, "Audio recorder destroyed")
    }

    companion object {
        private const val TAG = "SpeechRecognitionHelper"
    }
}
