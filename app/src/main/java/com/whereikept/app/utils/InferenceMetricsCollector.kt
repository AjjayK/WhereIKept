package com.whereikept.app.utils

import com.whereikept.app.BuildConfig
import com.whereikept.app.data.InferenceMetricEntity
import java.util.UUID

/**
 * Collects metrics for a single LLM inference (Whisper or Gemma)
 * Tracks timing, tokens, memory, thermal, and battery metrics
 *
 * Usage pattern:
 * ```
 * val collector = InferenceMetricsCollector.start(...)
 * // ... run inference ...
 * collector.recordFirstToken()  // When first token arrives
 * // ... continue inference ...
 * val metric = collector.finish(...)
 * ```
 */
class InferenceMetricsCollector private constructor(
    private val modelType: String,
    private val modelName: String,
    private val operationType: String,
    private val hadImage: Boolean,
    private val audioDurationSec: Float? = null,
    private val imageResolution: String? = null,
    private val acceleratorUsed: String = "cpu"
) {
    private val inferenceId = UUID.randomUUID().toString()
    private val startTime = System.currentTimeMillis()

    private var tokenizeStartMs: Long = 0
    private var prefillStartMs: Long = 0
    private var firstTokenMs: Long? = null
    private var decodeStartMs: Long = 0

    companion object {
        /**
         * Start collecting metrics for a new inference
         *
         * @param modelType "whisper" or "gemma"
         * @param modelName Specific model variant (e.g., "gemma-3n-e2b-it-int4")
         * @param operationType "voice_extraction", "image_extraction", "optimization", "audio_transcription"
         * @param hadImage True if inference includes image input (for multimodal)
         * @param audioDurationSec Audio duration in seconds (for Whisper)
         * @param imageResolution Image resolution string (e.g., "1920x1080")
         * @param acceleratorUsed "cpu", "gpu", "nnapi", "hexagon"
         */
        fun start(
            modelType: String,
            modelName: String,
            operationType: String,
            hadImage: Boolean = false,
            audioDurationSec: Float? = null,
            imageResolution: String? = null,
            acceleratorUsed: String = "cpu"
        ): InferenceMetricsCollector {
            return InferenceMetricsCollector(
                modelType, modelName, operationType,
                hadImage, audioDurationSec, imageResolution, acceleratorUsed
            )
        }
    }

    /**
     * Mark the start of tokenization phase
     * Optional - call if you want to track tokenization time separately
     */
    fun startTokenization() {
        tokenizeStartMs = System.currentTimeMillis()
    }

    /**
     * Mark the start of prefill phase
     * Optional - call if you want to track prefill time separately
     */
    fun startPrefill() {
        prefillStartMs = System.currentTimeMillis()
    }

    /**
     * Record when the first token arrives (Time To First Token - TTFT)
     * Important for measuring inference responsiveness
     */
    fun recordFirstToken() {
        if (firstTokenMs == null) {
            firstTokenMs = System.currentTimeMillis()
            decodeStartMs = firstTokenMs!!
        }
    }

    /**
     * Finish collecting metrics and return the entity
     *
     * @param promptTokens Number of input tokens
     * @param outputTokens Number of output tokens
     * @param success True if inference succeeded
     * @param errorCode Error code if failed (use ErrorClassifier)
     * @param fallbackReason Why fallback occurred (e.g., "gpu_failed_cpu_retry")
     * @param retryCount Number of retries before success/failure
     * @param resourceStats Resource statistics from ResourceMonitor
     * @param totalMs Optional override for total time (useful for Whisper)
     * @param deviceId Device ID (Firebase Installation ID)
     */
    fun finish(
        promptTokens: Int,
        outputTokens: Int,
        success: Boolean,
        errorCode: String? = null,
        fallbackReason: String? = null,
        retryCount: Int = 0,
        resourceStats: ResourceMonitor.ResourceStats,
        totalMs: Long? = null,
        deviceId: String = "unknown"
    ): InferenceMetricEntity {
        val endTime = System.currentTimeMillis()
        val actualTotalMs = totalMs ?: (endTime - startTime)

        // Calculate timing metrics
        val tokenizeMs = if (prefillStartMs > 0) prefillStartMs - tokenizeStartMs else 0L
        val ttftMs = firstTokenMs?.let { it - startTime } ?: 0L
        val prefillMs = if (firstTokenMs != null && prefillStartMs > 0) {
            firstTokenMs!! - prefillStartMs
        } else 0L
        val decodeMs = if (firstTokenMs != null) endTime - firstTokenMs!! else 0L

        // Calculate throughput (tokens per second)
        val prefillToksPerS = if (prefillMs > 0 && promptTokens > 0) {
            (promptTokens.toFloat() / prefillMs) * 1000f
        } else 0f

        val decodeToksPerS = if (decodeMs > 0 && outputTokens > 0) {
            (outputTokens.toFloat() / decodeMs) * 1000f
        } else 0f

        return InferenceMetricEntity(
            inferenceId = inferenceId,
            timestamp = startTime,
            appVersion = BuildConfig.VERSION_NAME,
            deviceId = deviceId,

            modelType = modelType,
            modelName = modelName,

            promptTokens = promptTokens,
            outputTokens = outputTokens,
            totalTokens = promptTokens + outputTokens,

            tokenizeMs = tokenizeMs,
            prefillMs = prefillMs,
            ttftMs = ttftMs,
            decodeMs = decodeMs,
            totalMs = actualTotalMs,

            prefillToksPerS = prefillToksPerS,
            decodeToksPerS = decodeToksPerS,

            peakMemMb = resourceStats.peakMemMb,
            thermalStatusStart = resourceStats.thermalStatusStart,
            thermalStatusEnd = resourceStats.thermalStatusEnd,
            batteryLevelStart = resourceStats.batteryLevelStart,
            batteryLevelEnd = resourceStats.batteryLevelEnd,
            cpuUsageAvg = resourceStats.cpuUsageAvg,

            success = success,
            errorCode = errorCode,
            fallbackReason = fallbackReason,
            retryCount = retryCount,

            operationType = operationType,
            hadImage = hadImage,
            audioDurationSec = audioDurationSec,
            imageResolution = imageResolution,
            acceleratorUsed = acceleratorUsed
        )
    }
}
