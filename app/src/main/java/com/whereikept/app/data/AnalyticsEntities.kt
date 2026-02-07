package com.whereikept.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Type 1: Version-Level Metrics
 * Collected once per app version upgrade
 * Purpose: A/B testing across versions with different LLM configurations
 */
@Entity(tableName = "version_metrics")
data class VersionMetricEntity(
    @PrimaryKey
    val versionCode: Int,  // Primary key = version code
    val versionName: String,
    val timestamp: Long,
    val configVariant: String,  // For A/B testing (e.g., "variant_a", "variant_b", "default")

    // LLM Parameters - Gemma
    val gemmaTemperature: Float,
    val gemmaTopK: Int,
    val gemmaTopP: Float,
    val gemmaMaxTokens: Int,
    val gemmaPromptVersion: String,
    val gemmaSystemPromptHash: String,
    val gemmaModelVariant: String,

    // LLM Parameters - Whisper
    val whisperModelVariant: String,
    val whisperBeamSize: Int?,
    val whisperLanguage: String?
)

/**
 * Type 2: Device-Level Metrics
 * Collected once per device (first launch)
 * Purpose: Understand user hardware capabilities and performance characteristics
 */
@Entity(tableName = "device_metrics")
data class DeviceMetricEntity(
    @PrimaryKey
    val deviceId: String,  // Firebase Installation ID
    val timestamp: Long,
    val lastUpdated: Long,

    // Device Info
    val manufacturer: String,
    val model: String,
    val brand: String,
    val osVersion: String,
    val sdkInt: Int,

    // CPU/Hardware
    val cpuModel: String,
    val cpuCores: Int,
    val cpuMaxFreqMhz: Int,
    val gpuVendor: String,
    val gpuModel: String,
    val ramMb: Int,
    val storageTotalGb: Int,
    val storageAvailableGb: Int,

    // AI Acceleration
    val hasVulkan: Boolean,
    val vulkanVersion: String?,
    val hasNnapi: Boolean,
    val nnapiVersion: String?,
    val hasGpuDelegate: Boolean,
    val hasHexagonDelegate: Boolean,

    // Runtime Versions
    val aiEdgeLitertVersion: String,
    val tfliteVersion: String?
)

/**
 * Type 3: Inference-Level Metrics
 * Collected per LLM inference (Whisper + Gemma)
 * Purpose: Track every inference for performance monitoring
 */
@Entity(
    tableName = "inference_metrics",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["modelType"]),
        Index(value = ["success"]),
        Index(value = ["operationType"])
    ]
)
data class InferenceMetricEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Identifiers
    val inferenceId: String,  // UUID
    val timestamp: Long,
    val appVersion: String,
    val deviceId: String,

    // Model Info
    val modelType: String,  // "whisper" or "gemma"
    val modelName: String,

    // Token Metrics
    val promptTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,

    // Timing Metrics (milliseconds)
    val tokenizeMs: Long,
    val prefillMs: Long,
    val ttftMs: Long,  // Time to first token
    val decodeMs: Long,
    val totalMs: Long,

    // Performance Metrics
    val prefillToksPerS: Float,
    val decodeToksPerS: Float,

    // Resource Metrics
    val peakMemMb: Int,
    val thermalStatusStart: Int,  // 0-6 scale
    val thermalStatusEnd: Int,
    val batteryLevelStart: Int,   // 0-100
    val batteryLevelEnd: Int,
    val cpuUsageAvg: Float,       // 0-100%

    // Reliability
    val success: Boolean,
    val errorCode: String?,
    val fallbackReason: String?,
    val retryCount: Int,

    // Context
    val operationType: String,  // "voice_extraction", "image_extraction", "optimization"
    val hadImage: Boolean,
    val audioDurationSec: Float?,  // For Whisper
    val imageResolution: String?,  // For Gemma multimodal
    val acceleratorUsed: String    // "cpu", "gpu", "nnapi", "hexagon"
)

/**
 * Aggregate statistics computed from inference metrics
 * Used for quick stats display without querying all inference records
 */
data class LlmStatsAggregate(
    val modelType: String,
    val totalInferences: Int,
    val successfulInferences: Int,
    val failedInferences: Int,
    val avgTotalMs: Float,
    val avgTtftMs: Float,
    val avgDecodeToksPerS: Float,
    val avgPeakMemMb: Float,
    val p50TotalMs: Long,  // Median
    val p95TotalMs: Long,  // 95th percentile
    val p99TotalMs: Long   // 99th percentile
)

/**
 * Daily trend data for charts
 */
data class LlmDailyStats(
    val date: String,  // YYYY-MM-DD
    val modelType: String,
    val inferenceCount: Int,
    val avgTotalMs: Float,
    val avgDecodeToksPerS: Float,
    val successRate: Float  // 0-100%
)
