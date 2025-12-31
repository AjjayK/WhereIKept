package com.whereikept.app.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.Observer
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.whereikept.app.data.*
import com.whereikept.app.worker.GemmaDownloadWorker
import java.util.UUID

private const val TAG = "GemmaDownloadRepository"
private const val GEMMA_DOWNLOAD_WORK_NAME = "gemma_model_download"

/**
 * Repository for managing Gemma model downloads using WorkManager.
 *
 * This class provides methods to:
 * - Start/cancel model downloads
 * - Observe download progress
 * - Check download status
 *
 * Adapted from Google AI Edge Gallery's DefaultDownloadRepository.kt
 */
class GemmaDownloadRepository(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)

    /**
     * Start downloading the Gemma model
     *
     * @param model The Gemma model to download
     * @param onStatusUpdated Callback for download status updates
     */
    fun downloadModel(
        model: GemmaModel,
        onStatusUpdated: (ModelDownloadStatus) -> Unit
    ) {
        Log.i(TAG, "===========================================")
        Log.i(TAG, "Starting Gemma Model Download")
        Log.i(TAG, "===========================================")
        Log.i(TAG, "Model: ${model.displayName}")
        Log.i(TAG, "URL: ${model.url}")
        Log.i(TAG, "Size: ${model.sizeInBytes / 1024 / 1024} MB")

        // Create input data for the worker
        val inputData = Data.Builder()
            .putString(KEY_MODEL_NAME, model.displayName)
            .putString(KEY_MODEL_URL, model.url)
            .putString(KEY_MODEL_VERSION, model.version)
            .putString(KEY_MODEL_DOWNLOAD_FILE_NAME, model.downloadFileName)
            .putLong(KEY_MODEL_SIZE_BYTES, model.sizeInBytes)
            .apply {
                if (model.accessToken != null) {
                    putString(KEY_MODEL_ACCESS_TOKEN, model.accessToken)
                    Log.i(TAG, "Using HuggingFace access token")
                }
            }
            .build()

        // Create download work request
        val downloadWorkRequest = OneTimeWorkRequestBuilder<GemmaDownloadWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(inputData)
            .addTag(GEMMA_DOWNLOAD_WORK_NAME)
            .build()

        val workerId = downloadWorkRequest.id
        Log.i(TAG, "Created work request: $workerId")

        // Enqueue the work (replace any existing download)
        workManager.enqueueUniqueWork(
            GEMMA_DOWNLOAD_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            downloadWorkRequest
        )

        // Observe download progress
        observeWorkerProgress(workerId, model, onStatusUpdated)
    }

    /**
     * Cancel ongoing model download
     */
    fun cancelDownload() {
        Log.i(TAG, "Cancelling Gemma model download")
        workManager.cancelUniqueWork(GEMMA_DOWNLOAD_WORK_NAME)
    }

    /**
     * Get current download status
     *
     * @return Download status if a download is in progress, null otherwise
     */
    fun getCurrentDownloadStatus(model: GemmaModel): ModelDownloadStatusType {
        return when {
            model.isDownloaded(context) -> ModelDownloadStatusType.SUCCEEDED
            model.isPartiallyDownloaded(context) -> ModelDownloadStatusType.PARTIALLY_DOWNLOADED
            else -> ModelDownloadStatusType.NOT_DOWNLOADED
        }
    }

    /**
     * Observe WorkManager progress updates
     */
    private fun observeWorkerProgress(
        workerId: UUID,
        model: GemmaModel,
        onStatusUpdated: (ModelDownloadStatus) -> Unit
    ) {
        val workInfoLiveData = workManager.getWorkInfoByIdLiveData(workerId)

        val observer = object : Observer<WorkInfo?> {
            override fun onChanged(workInfo: WorkInfo?) {
                if (workInfo == null) return

                when (workInfo.state) {
                    WorkInfo.State.ENQUEUED -> {
                        Log.d(TAG, "Download enqueued")
                        onStatusUpdated(
                            ModelDownloadStatus(
                                status = ModelDownloadStatusType.IN_PROGRESS,
                                totalBytes = model.sizeInBytes
                            )
                        )
                    }

                    WorkInfo.State.RUNNING -> {
                        val receivedBytes = workInfo.progress.getLong(KEY_DOWNLOAD_RECEIVED_BYTES, 0L)
                        val downloadRate = workInfo.progress.getLong(KEY_DOWNLOAD_RATE, 0L)
                        val remainingMs = workInfo.progress.getLong(KEY_DOWNLOAD_REMAINING_MS, 0L)

                        if (receivedBytes > 0) {
                            Log.d(TAG, "Download progress: ${receivedBytes / 1024 / 1024} MB / ${model.sizeInBytes / 1024 / 1024} MB")
                            onStatusUpdated(
                                ModelDownloadStatus(
                                    status = ModelDownloadStatusType.IN_PROGRESS,
                                    totalBytes = model.sizeInBytes,
                                    receivedBytes = receivedBytes,
                                    bytesPerSecond = downloadRate,
                                    remainingMs = remainingMs
                                )
                            )
                        }
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        Log.i(TAG, "===========================================")
                        Log.i(TAG, "Download SUCCEEDED")
                        Log.i(TAG, "===========================================")
                        onStatusUpdated(
                            ModelDownloadStatus(status = ModelDownloadStatusType.SUCCEEDED)
                        )
                        workInfoLiveData.removeObserver(this)
                    }

                    WorkInfo.State.FAILED -> {
                        val errorMessage = workInfo.outputData.getString(KEY_DOWNLOAD_ERROR_MESSAGE)
                            ?: "Unknown error"
                        Log.e(TAG, "===========================================")
                        Log.e(TAG, "Download FAILED: $errorMessage")
                        Log.e(TAG, "===========================================")
                        onStatusUpdated(
                            ModelDownloadStatus(
                                status = ModelDownloadStatusType.FAILED,
                                errorMessage = errorMessage
                            )
                        )
                        workInfoLiveData.removeObserver(this)
                    }

                    WorkInfo.State.CANCELLED -> {
                        Log.i(TAG, "Download CANCELLED")
                        onStatusUpdated(
                            ModelDownloadStatus(status = ModelDownloadStatusType.CANCELLED)
                        )
                        workInfoLiveData.removeObserver(this)
                    }

                    else -> {}
                }
            }
        }

        workInfoLiveData.observeForever(observer)
    }

    /**
     * Check if a download is currently in progress
     */
    fun isDownloadInProgress(): Boolean {
        val workInfos = workManager.getWorkInfosForUniqueWork(GEMMA_DOWNLOAD_WORK_NAME).get()
        return workInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    }
}
