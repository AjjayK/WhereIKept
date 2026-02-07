package com.whereikept.app.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.whereikept.app.data.AnalyticsPreferencesManager
import com.whereikept.app.data.DeviceMetricEntity
import com.whereikept.app.data.InferenceMetricEntity
import com.whereikept.app.data.VersionMetricEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Firebase Analytics wrapper with consent checking
 * Only logs events if user has opted in to analytics
 * All metrics are also stored locally in Room database regardless of consent
 */
class AnalyticsService private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val preferencesManager = AnalyticsPreferencesManager(context)

    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var isInitialized = false

    companion object {
        private const val TAG = "AnalyticsService"

        @Volatile
        private var INSTANCE: AnalyticsService? = null

        /**
         * Get singleton instance
         */
        fun getInstance(context: Context): AnalyticsService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AnalyticsService(context.applicationContext).also { INSTANCE = it }
            }
        }

        // Firebase Event Names
        private const val EVENT_VERSION_METRICS = "version_metrics_collected"
        private const val EVENT_DEVICE_METRICS = "device_metrics_collected"
        private const val EVENT_INFERENCE_COMPLETED = "inference_completed"

        // Firebase Parameter Names (max 40 events, max 25 parameters per event)
        // Version metrics parameters
        private const val PARAM_VERSION_CODE = "version_code"
        private const val PARAM_VERSION_NAME = "version_name"
        private const val PARAM_CONFIG_VARIANT = "config_variant"
        private const val PARAM_GEMMA_TEMP = "gemma_temperature"
        private const val PARAM_GEMMA_TOP_K = "gemma_top_k"
        private const val PARAM_GEMMA_TOP_P = "gemma_top_p"
        private const val PARAM_GEMMA_MAX_TOKENS = "gemma_max_tokens"
        private const val PARAM_GEMMA_MODEL = "gemma_model"
        private const val PARAM_WHISPER_MODEL = "whisper_model"

        // Device metrics parameters
        private const val PARAM_MANUFACTURER = "manufacturer"
        private const val PARAM_MODEL = "model"
        private const val PARAM_SDK_INT = "sdk_int"
        private const val PARAM_RAM_MB = "ram_mb"
        private const val PARAM_CPU_CORES = "cpu_cores"
        private const val PARAM_HAS_VULKAN = "has_vulkan"
        private const val PARAM_HAS_NNAPI = "has_nnapi"
        private const val PARAM_HAS_GPU_DELEGATE = "has_gpu_delegate"
        private const val PARAM_GPU_MODEL = "gpu_model"

        // Inference metrics parameters
        private const val PARAM_MODEL_TYPE = "model_type"
        private const val PARAM_MODEL_NAME = "model_name"
        private const val PARAM_OPERATION_TYPE = "operation_type"
        private const val PARAM_PROMPT_TOKENS = "prompt_tokens"
        private const val PARAM_OUTPUT_TOKENS = "output_tokens"
        private const val PARAM_TOTAL_MS = "total_ms"
        private const val PARAM_TTFT_MS = "ttft_ms"
        private const val PARAM_DECODE_TOKS_PER_S = "decode_toks_per_s"
        private const val PARAM_PEAK_MEM_MB = "peak_mem_mb"
        private const val PARAM_SUCCESS = "success"
        private const val PARAM_ACCELERATOR = "accelerator"
        private const val PARAM_ERROR_CODE = "error_code"
        private const val PARAM_FALLBACK_REASON = "fallback_reason"
    }

    /**
     * Initialize Firebase Analytics
     * Call this in Application.onCreate()
     */
    suspend fun initialize() {
        if (isInitialized) return

        try {
            // Only initialize if user has consented
            if (preferencesManager.isAnalyticsEnabled()) {
                firebaseAnalytics = Firebase.analytics
                firebaseAnalytics?.setAnalyticsCollectionEnabled(true)
                Log.i(TAG, "Firebase Analytics initialized (user opted in)")
            } else {
                Log.i(TAG, "Firebase Analytics disabled (user opted out)")
            }
            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase Analytics", e)
        }
    }

    /**
     * Enable analytics collection (user opted in)
     */
    suspend fun enableAnalytics() {
        try {
            preferencesManager.enableAnalytics()
            firebaseAnalytics = Firebase.analytics
            firebaseAnalytics?.setAnalyticsCollectionEnabled(true)
            Log.i(TAG, "Analytics enabled by user")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable analytics", e)
        }
    }

    /**
     * Disable analytics collection (user opted out)
     */
    suspend fun disableAnalytics() {
        try {
            preferencesManager.disableAnalytics()
            firebaseAnalytics?.setAnalyticsCollectionEnabled(false)
            firebaseAnalytics = null
            Log.i(TAG, "Analytics disabled by user")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable analytics", e)
        }
    }

    /**
     * Log version metrics (one-time per version)
     */
    fun logVersionMetrics(metric: VersionMetricEntity) {
        scope.launch {
            try {
                if (!preferencesManager.isAnalyticsEnabled()) {
                    Log.d(TAG, "Skipping version metrics logging (user opted out)")
                    return@launch
                }

                val bundle = Bundle().apply {
                    putInt(PARAM_VERSION_CODE, metric.versionCode)
                    putString(PARAM_VERSION_NAME, metric.versionName)
                    putString(PARAM_CONFIG_VARIANT, metric.configVariant)
                    putDouble(PARAM_GEMMA_TEMP, metric.gemmaTemperature.toDouble())
                    putInt(PARAM_GEMMA_TOP_K, metric.gemmaTopK)
                    putDouble(PARAM_GEMMA_TOP_P, metric.gemmaTopP.toDouble())
                    putInt(PARAM_GEMMA_MAX_TOKENS, metric.gemmaMaxTokens)
                    putString(PARAM_GEMMA_MODEL, metric.gemmaModelVariant)
                    putString(PARAM_WHISPER_MODEL, metric.whisperModelVariant)
                }

                firebaseAnalytics?.logEvent(EVENT_VERSION_METRICS, bundle)
                Log.d(TAG, "Logged version metrics: ${metric.versionName}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log version metrics", e)
            }
        }
    }

    /**
     * Log device metrics (one-time per device)
     */
    fun logDeviceMetrics(metric: DeviceMetricEntity) {
        scope.launch {
            try {
                if (!preferencesManager.isAnalyticsEnabled()) {
                    Log.d(TAG, "Skipping device metrics logging (user opted out)")
                    return@launch
                }

                val bundle = Bundle().apply {
                    putString(PARAM_MANUFACTURER, metric.manufacturer)
                    putString(PARAM_MODEL, metric.model)
                    putInt(PARAM_SDK_INT, metric.sdkInt)
                    putInt(PARAM_RAM_MB, metric.ramMb)
                    putInt(PARAM_CPU_CORES, metric.cpuCores)
                    putBoolean(PARAM_HAS_VULKAN, metric.hasVulkan)
                    putBoolean(PARAM_HAS_NNAPI, metric.hasNnapi)
                    putBoolean(PARAM_HAS_GPU_DELEGATE, metric.hasGpuDelegate)
                    putString(PARAM_GPU_MODEL, metric.gpuModel)
                }

                firebaseAnalytics?.logEvent(EVENT_DEVICE_METRICS, bundle)
                Log.d(TAG, "Logged device metrics: ${metric.manufacturer} ${metric.model}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log device metrics", e)
            }
        }
    }

    /**
     * Log inference metrics (per LLM inference)
     */
    fun logInferenceMetrics(metric: InferenceMetricEntity) {
        scope.launch {
            try {
                if (!preferencesManager.isAnalyticsEnabled()) {
                    // Don't log at DEBUG level for every inference to avoid spam
                    return@launch
                }

                val bundle = Bundle().apply {
                    putString(PARAM_MODEL_TYPE, metric.modelType)
                    putString(PARAM_MODEL_NAME, metric.modelName)
                    putString(PARAM_OPERATION_TYPE, metric.operationType)
                    putInt(PARAM_PROMPT_TOKENS, metric.promptTokens)
                    putInt(PARAM_OUTPUT_TOKENS, metric.outputTokens)
                    putLong(PARAM_TOTAL_MS, metric.totalMs)
                    putLong(PARAM_TTFT_MS, metric.ttftMs)
                    putDouble(PARAM_DECODE_TOKS_PER_S, metric.decodeToksPerS.toDouble())
                    putInt(PARAM_PEAK_MEM_MB, metric.peakMemMb)
                    putBoolean(PARAM_SUCCESS, metric.success)
                    putString(PARAM_ACCELERATOR, metric.acceleratorUsed)

                    metric.errorCode?.let { putString(PARAM_ERROR_CODE, it) }
                    metric.fallbackReason?.let { putString(PARAM_FALLBACK_REASON, it) }
                }

                firebaseAnalytics?.logEvent(EVENT_INFERENCE_COMPLETED, bundle)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log inference metrics", e)
            }
        }
    }

    /**
     * Log a custom event (for future use)
     */
    fun logEvent(eventName: String, params: Bundle? = null) {
        scope.launch {
            try {
                if (!preferencesManager.isAnalyticsEnabled()) return@launch
                firebaseAnalytics?.logEvent(eventName, params)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log custom event: $eventName", e)
            }
        }
    }
}
