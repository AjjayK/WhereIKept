package com.whereikept.app.data

import android.content.Context
import java.io.File

/**
 * Data class representing the Gemma 3N E2B model for download and management.
 *
 * Based on Google AI Edge Gallery's Model.kt implementation.
 */
data class GemmaModel(
    /**
     * The name of the model (for identification)
     */
    val name: String = "gemma-3n-e2b-it-int4",

    /**
     * Display name shown to users
     */
    val displayName: String = "Gemma 3N E2B (INT4)",

    /**
     * HuggingFace download URL
     */
    val url: String = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-E2B-it-int4.litertlm",

    /**
     * File size in bytes (~3.4 GB actual size from HuggingFace download)
     * Updated based on actual downloaded file size
     */
    val sizeInBytes: Long = 3_655_475_200L, // 3486 MB

    /**
     * The name of the downloaded file
     */
    val downloadFileName: String = "gemma-3n-E2B-it-int4.litertlm",

    /**
     * Model version
     */
    val version: String = "1.0",

    /**
     * HuggingFace access token (for gated model access)
     * Set after OAuth flow completes
     */
    var accessToken: String? = null,
) {
    /**
     * Normalized name for file system storage (replaces special chars with _)
     */
    val normalizedName: String = name.replace(Regex("[^a-zA-Z0-9]"), "_")

    /**
     * Get the full path where the model should be stored
     */
    fun getPath(context: Context): String {
        val baseDir = listOf(
            context.getExternalFilesDir("models")?.absolutePath ?: "",
            normalizedName,
            version
        ).joinToString(File.separator)

        return listOf(baseDir, downloadFileName).joinToString(File.separator)
    }

    /**
     * Get the temporary file path (used during download with .tmp extension)
     */
    fun getTmpPath(context: Context): String {
        return getPath(context) + ".tmp"
    }

    /**
     * Check if model is downloaded and ready
     */
    fun isDownloaded(context: Context): Boolean {
        val modelFile = File(getPath(context))
        val exists = modelFile.exists()
        val actualSize = if (exists) modelFile.length() else 0

        // Log for debugging
        android.util.Log.d("GemmaModel", "Checking if model is downloaded:")
        android.util.Log.d("GemmaModel", "  Expected path: ${getPath(context)}")
        android.util.Log.d("GemmaModel", "  File exists: $exists")
        android.util.Log.d("GemmaModel", "  Expected size: $sizeInBytes bytes (${sizeInBytes / 1024 / 1024} MB)")
        android.util.Log.d("GemmaModel", "  Actual size: $actualSize bytes (${actualSize / 1024 / 1024} MB)")

        // File exists and size is close enough (within 1% tolerance for filesystem differences)
        val sizeTolerance = (sizeInBytes * 0.01).toLong()  // 1% tolerance
        val sizeMatch = exists && kotlin.math.abs(actualSize - sizeInBytes) <= sizeTolerance

        android.util.Log.d("GemmaModel", "  Size match (1% tolerance): $sizeMatch")
        android.util.Log.d("GemmaModel", "  Is downloaded: ${exists && sizeMatch}")

        return exists && sizeMatch
    }

    /**
     * Check if there's a partial download
     */
    fun isPartiallyDownloaded(context: Context): Boolean {
        val tmpFile = File(getTmpPath(context))
        return tmpFile.exists() && tmpFile.length() > 0
    }

    /**
     * Get the size of partial download (in bytes)
     */
    fun getPartialDownloadSize(context: Context): Long {
        val tmpFile = File(getTmpPath(context))
        return if (tmpFile.exists()) tmpFile.length() else 0L
    }
}

/**
 * Download status types
 */
enum class ModelDownloadStatusType {
    NOT_DOWNLOADED,
    PARTIALLY_DOWNLOADED,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED,
    CANCELLED
}

/**
 * Model download status with progress information
 */
data class ModelDownloadStatus(
    val status: ModelDownloadStatusType,
    val totalBytes: Long = 0,
    val receivedBytes: Long = 0,
    val errorMessage: String = "",
    val bytesPerSecond: Long = 0,
    val remainingMs: Long = 0,
)

/**
 * WorkManager data keys for passing download parameters
 */
const val KEY_MODEL_NAME = "model_name"
const val KEY_MODEL_URL = "model_url"
const val KEY_MODEL_VERSION = "model_version"
const val KEY_MODEL_DOWNLOAD_FILE_NAME = "model_download_file_name"
const val KEY_MODEL_SIZE_BYTES = "model_size_bytes"
const val KEY_MODEL_ACCESS_TOKEN = "model_access_token"

// Progress data keys
const val KEY_DOWNLOAD_RECEIVED_BYTES = "download_received_bytes"
const val KEY_DOWNLOAD_RATE = "download_rate"
const val KEY_DOWNLOAD_REMAINING_MS = "download_remaining_ms"
const val KEY_DOWNLOAD_ERROR_MESSAGE = "download_error_message"

// Temporary file extension
const val TMP_FILE_EXT = ".tmp"
