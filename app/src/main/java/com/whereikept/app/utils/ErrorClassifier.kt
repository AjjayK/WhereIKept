package com.whereikept.app.utils

import java.io.IOException

/**
 * Classifies exceptions into standardized error codes for analytics
 * Helps identify common failure patterns across devices
 */
object ErrorClassifier {

    /**
     * Map an exception to a standardized error code
     * Used for analytics and debugging
     */
    fun classifyError(exception: Throwable): String {
        return when {
            exception is OutOfMemoryError -> "OOM"
            exception.message?.contains("timeout", ignoreCase = true) == true -> "TIMEOUT"
            exception.message?.contains("model", ignoreCase = true) == true -> "MODEL_LOAD_FAILED"
            exception.message?.contains("gpu", ignoreCase = true) == true -> "GPU_FAILED"
            exception.message?.contains("nnapi", ignoreCase = true) == true -> "NNAPI_FAILED"
            exception.message?.contains("vulkan", ignoreCase = true) == true -> "VULKAN_FAILED"
            exception is IOException -> "IO_ERROR"
            exception is IllegalStateException -> "INVALID_STATE"
            exception is IllegalArgumentException -> "INVALID_INPUT"
            exception is NullPointerException -> "NULL_POINTER"
            exception is SecurityException -> "PERMISSION_DENIED"
            else -> "UNKNOWN_ERROR"
        }
    }

    /**
     * Determine why a fallback to CPU occurred
     * Used when GPU/NNAPI acceleration fails
     */
    fun getFallbackReason(accelerator: String, error: String): String? {
        return when {
            accelerator == "gpu" && error.contains("gpu", ignoreCase = true) -> "gpu_failed_cpu_retry"
            accelerator == "nnapi" && error.contains("nnapi", ignoreCase = true) -> "nnapi_failed_cpu_retry"
            accelerator == "vulkan" && error.contains("vulkan", ignoreCase = true) -> "vulkan_failed_cpu_retry"
            accelerator == "hexagon" && error.contains("hexagon", ignoreCase = true) -> "hexagon_failed_cpu_retry"
            else -> null
        }
    }

    /**
     * Check if an error is retryable
     * Some errors (like OOM) may succeed on retry with lower settings
     */
    fun isRetryable(errorCode: String): Boolean {
        return when (errorCode) {
            "OOM" -> true  // Can retry with reduced batch size or context
            "TIMEOUT" -> true  // Network/IO timeouts are retryable
            "IO_ERROR" -> true  // Temporary IO failures
            "GPU_FAILED" -> true  // Can fallback to CPU
            "NNAPI_FAILED" -> true  // Can fallback to CPU
            "VULKAN_FAILED" -> true  // Can fallback to CPU
            else -> false
        }
    }

    /**
     * Get a human-readable error message for display
     */
    fun getErrorMessage(errorCode: String): String {
        return when (errorCode) {
            "OOM" -> "Out of memory. Try closing other apps."
            "TIMEOUT" -> "Operation timed out. Please try again."
            "MODEL_LOAD_FAILED" -> "Failed to load AI model. Please reinstall the app."
            "GPU_FAILED" -> "GPU acceleration failed. Using CPU instead."
            "NNAPI_FAILED" -> "NNAPI acceleration failed. Using CPU instead."
            "VULKAN_FAILED" -> "Vulkan acceleration failed. Using CPU instead."
            "IO_ERROR" -> "File read/write error. Check storage permissions."
            "INVALID_STATE" -> "Invalid application state. Please restart."
            "INVALID_INPUT" -> "Invalid input provided."
            "NULL_POINTER" -> "Internal error. Please report this bug."
            "PERMISSION_DENIED" -> "Permission denied. Grant required permissions."
            "UNKNOWN_ERROR" -> "An unknown error occurred."
            else -> "Error: $errorCode"
        }
    }
}
