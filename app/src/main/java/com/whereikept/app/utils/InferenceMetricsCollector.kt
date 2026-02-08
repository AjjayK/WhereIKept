package com.whereikept.app.utils

import android.os.SystemClock
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
    private val imageCount: Int = 0,
    private val audioDurationSec: Float? = null,
    private val imageResolution: String? = null,
    private val acceleratorUsed: String = "cpu"
) {
    private val inferenceId = UUID.randomUUID().toString()
    // Use wall clock for timestamp (when the inference happened)
    private val timestampMs = System.currentTimeMillis()
    // Use monotonic clock for duration measurements (won't jump if time changes)
    private val startTimeMonotonic = SystemClock.elapsedRealtime()

    private var tokenizeStartMs: Long = 0
    private var prefillStartMs: Long = 0
    private var firstTokenMs: Long? = null
    private var decodeStartMs: Long = 0

    // Actual token counts from LiteRT-LM SDK
    private var actualPrefillTokens: Int = 0
    private var decodeTokenCount: Int = 0

    companion object {
        /**
         * Start collecting metrics for a new inference
         *
         * @param modelType "whisper" or "gemma"
         * @param modelName Specific model variant (e.g., "gemma-3n-e2b-it-int4")
         * @param operationType "voice_extraction", "image_extraction", "optimization", "audio_transcription"
         * @param hadImage True if inference includes image input (for multimodal)
         * @param imageCount Number of images in the input (for estimating image tokens)
         * @param audioDurationSec Audio duration in seconds (for Whisper and audio tokens estimation)
         * @param imageResolution Image resolution string (e.g., "1920x1080")
         * @param acceleratorUsed "cpu", "gpu", "nnapi", "hexagon"
         */
        fun start(
            modelType: String,
            modelName: String,
            operationType: String,
            hadImage: Boolean = false,
            imageCount: Int = 0,
            audioDurationSec: Float? = null,
            imageResolution: String? = null,
            acceleratorUsed: String = "cpu"
        ): InferenceMetricsCollector {
            return InferenceMetricsCollector(
                modelType, modelName, operationType,
                hadImage, imageCount, audioDurationSec, imageResolution, acceleratorUsed
            )
        }

        // Token estimation constants from Google AI Edge Gallery
        // Image: 257 tokens per image (fixed for Gemma 3N vision encoder)
        const val TOKENS_PER_IMAGE = 257
        // Audio: 1 token per 150ms of audio
        const val AUDIO_MS_PER_TOKEN = 150f
    }

    /**
     * Mark the start of tokenization phase
     * Optional - call if you want to track tokenization time separately
     */
    fun startTokenization() {
        tokenizeStartMs = SystemClock.elapsedRealtime()
    }

    /**
     * Mark the start of prefill phase
     * Optional - call if you want to track prefill time separately
     */
    fun startPrefill() {
        prefillStartMs = SystemClock.elapsedRealtime()
    }

    /**
     * Record when the first token/chunk arrives (Time To First Token - TTFT)
     * Important for measuring inference responsiveness.
     *
     * @param prefillTokenCount The actual prefill token count from
     *   conversation.getBenchmarkInfo().lastPrefillTokenCount (experimental API).
     *   Pass 0 if not available - will use estimates for images/audio.
     */
    fun recordFirstToken(prefillTokenCount: Int = 0) {
        if (firstTokenMs == null) {
            firstTokenMs = SystemClock.elapsedRealtime()
            decodeStartMs = firstTokenMs!!
            // Store actual prefill token count from SDK
            actualPrefillTokens = prefillTokenCount
        }
        // First emission counts as first decode token
        decodeTokenCount = 1
    }

    /**
     * Record a decode token/chunk emission.
     * Call this on each streaming callback AFTER the first one.
     * Note: Each emission is counted as ~1 token (close approximation for streaming).
     */
    fun recordDecodeToken() {
        decodeTokenCount++
    }

    /**
     * Finish collecting metrics and return the entity.
     *
     * Token counts are now calculated using:
     * - Prefill: getBenchmarkInfo().lastPrefillTokenCount from SDK + image/audio estimates
     * - Decode: Count of streaming emissions (each emission ≈ 1 token)
     *
     * @param success True if inference succeeded
     * @param errorCode Error code if failed (use ErrorClassifier)
     * @param fallbackReason Why fallback occurred (e.g., "gpu_failed_cpu_retry")
     * @param retryCount Number of retries before success/failure
     * @param resourceStats Resource statistics from ResourceMonitor
     * @param totalMs Optional override for total time (useful for Whisper)
     * @param deviceId Device ID (Firebase Installation ID)
     */
    fun finish(
        success: Boolean,
        errorCode: String? = null,
        fallbackReason: String? = null,
        retryCount: Int = 0,
        resourceStats: ResourceMonitor.ResourceStats,
        totalMs: Long? = null,
        deviceId: String = "unknown"
    ): InferenceMetricEntity {
        val endTimeMonotonic = SystemClock.elapsedRealtime()
        val actualTotalMs = totalMs ?: (endTimeMonotonic - startTimeMonotonic)

        // Calculate timing metrics using monotonic clock
        val tokenizeMs = if (prefillStartMs > 0) prefillStartMs - tokenizeStartMs else 0L
        val ttftMs = firstTokenMs?.let { it - startTimeMonotonic } ?: 0L
        val prefillMs = if (firstTokenMs != null && prefillStartMs > 0) {
            firstTokenMs!! - prefillStartMs
        } else 0L
        val decodeMs = if (firstTokenMs != null) endTimeMonotonic - firstTokenMs!! else 0L

        // Calculate actual token counts
        // Prefill: SDK count + image tokens (257 per image) + audio tokens (1 per 150ms)
        val imageTokens = imageCount * TOKENS_PER_IMAGE
        val audioTokens = audioDurationSec?.let { (it * 1000f / AUDIO_MS_PER_TOKEN).toInt() } ?: 0
        val totalPrefillTokens = actualPrefillTokens + imageTokens + audioTokens

        // Decode: counted via recordDecodeToken() calls
        val totalDecodeTokens = decodeTokenCount

        // Calculate throughput (tokens per second) - now using actual counts!
        val prefillToksPerS = if (prefillMs > 0 && totalPrefillTokens > 0) {
            (totalPrefillTokens.toFloat() / prefillMs) * 1000f
        } else 0f

        val decodeToksPerS = if (decodeMs > 0 && totalDecodeTokens > 0) {
            (totalDecodeTokens.toFloat() / decodeMs) * 1000f
        } else 0f

        return InferenceMetricEntity(
            inferenceId = inferenceId,
            timestamp = timestampMs,  // Use wall clock for "when" (stored timestamp)
            appVersion = BuildConfig.VERSION_NAME,
            deviceId = deviceId,

            modelType = modelType,
            modelName = modelName,

            promptTokens = totalPrefillTokens,
            outputTokens = totalDecodeTokens,
            totalTokens = totalPrefillTokens + totalDecodeTokens,

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
