package com.whereikept.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.whereikept.app.MainActivity
import com.whereikept.app.R
import com.whereikept.app.data.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "GemmaDownloadWorker"
private const val FOREGROUND_NOTIFICATION_CHANNEL_ID = "gemma_model_download_channel"
private var channelCreated = false

/**
 * WorkManager worker for downloading the Gemma model in the background.
 *
 * Features:
 * - Resume capability for interrupted downloads
 * - Progress tracking with download rate and ETA
 * - Foreground service with notification
 * - HuggingFace authentication support
 *
 * Adapted from Google AI Edge Gallery's DownloadWorker.kt
 */
class GemmaDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val notificationId: Int = params.id.hashCode()

    init {
        if (!channelCreated) {
            // Create notification channel for download progress
            val channel = NotificationChannel(
                FOREGROUND_NOTIFICATION_CHANNEL_ID,
                "Gemma Model Download",
                NotificationManager.IMPORTANCE_LOW // Silent notification
            ).apply {
                description = "Notifications for Gemma model downloading progress"
            }
            notificationManager.createNotificationChannel(channel)
            channelCreated = true
        }
    }

    override suspend fun doWork(): Result {
        val modelUrl = inputData.getString(KEY_MODEL_URL)
        val modelName = inputData.getString(KEY_MODEL_NAME) ?: "Gemma Model"
        val version = inputData.getString(KEY_MODEL_VERSION) ?: "1.0"
        val fileName = inputData.getString(KEY_MODEL_DOWNLOAD_FILE_NAME)
        val totalBytes = inputData.getLong(KEY_MODEL_SIZE_BYTES, 0L)
        val accessToken = inputData.getString(KEY_MODEL_ACCESS_TOKEN)

        return withContext(Dispatchers.IO) {
            if (modelUrl == null || fileName == null) {
                Log.e(TAG, "Missing required parameters: url=$modelUrl, fileName=$fileName")
                return@withContext Result.failure(
                    Data.Builder()
                        .putString(KEY_DOWNLOAD_ERROR_MESSAGE, "Missing download parameters")
                        .build()
                )
            }

            try {
                // Set as foreground service immediately
                setForeground(createForegroundInfo(progress = 0, modelName = modelName))

                Log.i(TAG, "===========================================")
                Log.i(TAG, "Starting Gemma Model Download")
                Log.i(TAG, "===========================================")
                Log.i(TAG, "Model: $modelName")
                Log.i(TAG, "URL: $modelUrl")
                Log.i(TAG, "Size: ${totalBytes / 1024 / 1024} MB")
                Log.i(TAG, "Version: $version")

                // Prepare output directory
                val outputDir = File(
                    applicationContext.getExternalFilesDir("models"),
                    listOf("gemma_3n_e2b_it_int4", version).joinToString(File.separator)
                )
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                    Log.i(TAG, "Created output directory: ${outputDir.absolutePath}")
                }

                // Check for partial download
                val outputTmpFile = File(outputDir, "$fileName$TMP_FILE_EXT")
                val partialBytes = outputTmpFile.length()
                var downloadedBytes = 0L

                if (partialBytes > 0) {
                    Log.i(TAG, "Found partial download: ${partialBytes / 1024 / 1024} MB")
                    Log.i(TAG, "Attempting to resume...")
                    downloadedBytes = partialBytes
                }

                // Open HTTP connection
                val url = URL(modelUrl)
                val connection = url.openConnection() as HttpURLConnection

                // Add HuggingFace auth token if provided
                if (accessToken != null) {
                    Log.d(TAG, "Using HuggingFace access token: ${accessToken.take(10)}...")
                    connection.setRequestProperty("Authorization", "Bearer $accessToken")
                }

                // Request resume from partial bytes
                if (partialBytes > 0) {
                    connection.setRequestProperty("Range", "bytes=$partialBytes-")
                }

                connection.connect()
                val responseCode = connection.responseCode
                Log.i(TAG, "HTTP Response Code: $responseCode")

                // Check if request was successful
                if (responseCode != HttpURLConnection.HTTP_OK &&
                    responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    throw IOException("HTTP error code: $responseCode")
                }

                // Handle Content-Range header for resume
                val contentRange = connection.getHeaderField("Content-Range")
                if (contentRange != null) {
                    Log.i(TAG, "Content-Range: $contentRange")
                    val rangeParts = contentRange.substringAfter("bytes ").split("/")
                    val byteRange = rangeParts[0].split("-")
                    val startByte = byteRange[0].toLong()
                    downloadedBytes = startByte
                    Log.i(TAG, "Resuming from byte: $startByte")
                } else {
                    Log.i(TAG, "Starting download from beginning")
                }

                // Download the file
                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(outputTmpFile, true /* append */)

                val buffer = ByteArray(8192) // 8KB buffer
                var bytesRead: Int
                var lastProgressUpdate = 0L
                var deltaBytes = 0L

                // Buffers for calculating download rate (moving average)
                val bytesReadSizeBuffer = mutableListOf<Long>()
                val bytesReadLatencyBuffer = mutableListOf<Long>()

                Log.i(TAG, "Starting file transfer...")

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    deltaBytes += bytesRead

                    // Update progress every 200ms
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastProgressUpdate > 200) {
                        // Calculate download rate (moving average of last 5 samples)
                        var bytesPerMs = 0f
                        if (lastProgressUpdate != 0L) {
                            if (bytesReadSizeBuffer.size == 5) {
                                bytesReadSizeBuffer.removeAt(0)
                            }
                            bytesReadSizeBuffer.add(deltaBytes)

                            if (bytesReadLatencyBuffer.size == 5) {
                                bytesReadLatencyBuffer.removeAt(0)
                            }
                            bytesReadLatencyBuffer.add(currentTime - lastProgressUpdate)

                            deltaBytes = 0L
                            bytesPerMs = bytesReadSizeBuffer.sum().toFloat() / bytesReadLatencyBuffer.sum()
                        }

                        // Calculate ETA
                        var remainingMs = 0f
                        if (bytesPerMs > 0f && totalBytes > 0L) {
                            remainingMs = (totalBytes - downloadedBytes) / bytesPerMs
                        }

                        // Report progress to WorkManager
                        setProgress(
                            Data.Builder()
                                .putLong(KEY_DOWNLOAD_RECEIVED_BYTES, downloadedBytes)
                                .putLong(KEY_DOWNLOAD_RATE, (bytesPerMs * 1000).toLong())
                                .putLong(KEY_DOWNLOAD_REMAINING_MS, remainingMs.toLong())
                                .build()
                        )

                        // Update foreground notification
                        val progress = if (totalBytes > 0) {
                            (downloadedBytes * 100 / totalBytes).toInt()
                        } else {
                            0
                        }
                        setForeground(createForegroundInfo(progress, modelName))

                        Log.d(TAG, "Progress: $progress% (${downloadedBytes / 1024 / 1024} MB / ${totalBytes / 1024 / 1024} MB)")
                        lastProgressUpdate = currentTime
                    }
                }

                outputStream.close()
                inputStream.close()
                connection.disconnect()

                Log.i(TAG, "Download completed successfully")
                Log.i(TAG, "Downloaded: ${downloadedBytes / 1024 / 1024} MB")

                // Rename temp file to final filename
                val finalFile = File(outputDir, fileName)
                if (finalFile.exists()) {
                    finalFile.delete()
                }
                val renameSuccess = outputTmpFile.renameTo(finalFile)

                if (!renameSuccess) {
                    throw IOException("Failed to rename temp file to final file")
                }

                Log.i(TAG, "File saved to: ${finalFile.absolutePath}")
                Log.i(TAG, "===========================================")

                Result.success()

            } catch (e: IOException) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                Log.e(TAG, "===========================================")
                Result.failure(
                    Data.Builder()
                        .putString(KEY_DOWNLOAD_ERROR_MESSAGE, e.message ?: "Unknown error")
                        .build()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error: ${e.message}", e)
                Log.e(TAG, "===========================================")
                Result.failure(
                    Data.Builder()
                        .putString(KEY_DOWNLOAD_ERROR_MESSAGE, e.message ?: "Unexpected error")
                        .build()
                )
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(0, "Gemma Model")
    }

    /**
     * Create foreground notification for download progress
     */
    private fun createForegroundInfo(progress: Int, modelName: String): ForegroundInfo {
        val title = "Downloading $modelName"
        val content = "Download in progress: $progress%"

        // Intent to return to app when notification is tapped
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, FOREGROUND_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download) // System download icon
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        return ForegroundInfo(
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }
}
