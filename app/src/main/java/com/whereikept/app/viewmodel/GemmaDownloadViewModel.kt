package com.whereikept.app.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whereikept.app.auth.HuggingFaceAuthHelper
import com.whereikept.app.data.GemmaModel
import com.whereikept.app.data.ModelDownloadStatus
import com.whereikept.app.data.ModelDownloadStatusType
import com.whereikept.app.repository.GemmaDownloadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "GemmaDownloadViewModel"

/**
 * ViewModel for managing Gemma model download state and operations.
 *
 * Provides:
 * - Download status tracking
 * - Download start/cancel operations
 * - HuggingFace OAuth token management
 */
class GemmaDownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GemmaDownloadRepository(application.applicationContext)
    private val gemmaModel = GemmaModel()

    // HuggingFace auth helper (managed at ViewModel level to survive activity pauses)
    val authHelper = HuggingFaceAuthHelper(application.applicationContext)

    // Download status state
    private val _downloadStatus = MutableStateFlow<ModelDownloadStatus?>(null)
    val downloadStatus: StateFlow<ModelDownloadStatus?> = _downloadStatus.asStateFlow()

    // HuggingFace access token (set after OAuth flow)
    private val _accessToken = MutableStateFlow<String?>(null)
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    init {
        // Check initial download status
        checkInitialStatus()
        // Load stored token if available
        loadStoredToken()
    }

    /**
     * Check the initial download status on ViewModel creation
     */
    private fun checkInitialStatus() {
        viewModelScope.launch {
            val status = repository.getCurrentDownloadStatus(gemmaModel)
            _downloadStatus.value = ModelDownloadStatus(status = status)

            Log.i(TAG, "Initial download status: $status")

            // Check for partial downloads
            if (status == ModelDownloadStatusType.PARTIALLY_DOWNLOADED) {
                val partialSize = gemmaModel.getPartialDownloadSize(getApplication())
                Log.i(TAG, "Found partial download: ${partialSize / 1024 / 1024} MB")
            }
        }
    }

    /**
     * Load stored token from DataStore on initialization
     */
    private fun loadStoredToken() {
        viewModelScope.launch {
            val storedToken = authHelper.getStoredToken()
            if (storedToken != null) {
                Log.i(TAG, "Loaded stored access token from DataStore")
                _accessToken.value = storedToken
                gemmaModel.accessToken = storedToken
            } else {
                Log.i(TAG, "No stored access token found")
            }
        }
    }

    /**
     * Start downloading the Gemma model
     */
    fun startDownload() {
        Log.i(TAG, "Starting Gemma model download")

        // Update token if available
        _accessToken.value?.let {
            gemmaModel.accessToken = it
        }

        repository.downloadModel(gemmaModel) { status ->
            _downloadStatus.value = status
            Log.d(TAG, "Download status updated: ${status.status}")
        }
    }

    /**
     * Cancel ongoing download
     */
    fun cancelDownload() {
        Log.i(TAG, "Cancelling download")
        repository.cancelDownload()
        _downloadStatus.value = ModelDownloadStatus(status = ModelDownloadStatusType.CANCELLED)
    }

    /**
     * Set HuggingFace access token (called after OAuth flow completes)
     */
    fun setAccessToken(token: String) {
        Log.i(TAG, "Access token set")
        _accessToken.value = token
        gemmaModel.accessToken = token
    }

    /**
     * Check if model is downloaded and ready to use
     */
    fun isModelReady(): Boolean {
        return gemmaModel.isDownloaded(getApplication())
    }

    /**
     * Get the model file path (for LlmService)
     */
    fun getModelPath(): String {
        return gemmaModel.getPath(getApplication())
    }

    /**
     * Check if download is currently in progress
     */
    fun isDownloadInProgress(): Boolean {
        return repository.isDownloadInProgress()
    }

    /**
     * Retry failed download
     */
    fun retryDownload() {
        Log.i(TAG, "Retrying download")
        startDownload()
    }

    /**
     * Check if the model URL requires authentication
     * Returns HTTP response code
     */
    suspend fun testModelUrlAccess(accessToken: String? = null): Int {
        return authHelper.testUrlAccess(gemmaModel.url, accessToken)
    }

    /**
     * Check if we have a valid stored token
     */
    suspend fun hasValidToken(): Boolean {
        return authHelper.isTokenValid()
    }

    /**
     * Clear stored token (for testing or logout)
     */
    suspend fun clearStoredToken() {
        authHelper.clearToken()
        _accessToken.value = null
        gemmaModel.accessToken = null
        Log.i(TAG, "Stored token cleared")
    }

    /**
     * Clean up resources when ViewModel is cleared
     */
    override fun onCleared() {
        super.onCleared()
        Log.i(TAG, "ViewModel cleared, disposing auth helper")
        authHelper.dispose()
    }
}
