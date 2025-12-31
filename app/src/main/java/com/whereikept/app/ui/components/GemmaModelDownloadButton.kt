package com.whereikept.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.whereikept.app.data.ModelDownloadStatus
import com.whereikept.app.data.ModelDownloadStatusType

/**
 * Download button for Gemma model with progress indicator.
 *
 * Shows different states:
 * - NOT_DOWNLOADED: "Download Model" button
 * - IN_PROGRESS: Progress bar with percentage and download stats
 * - SUCCEEDED: "Model Ready" status
 * - FAILED: Error message with retry button
 * - PARTIALLY_DOWNLOADED: "Resume Download" button
 *
 * Based on Google AI Edge Gallery's DownloadAndTryButton.kt
 */
@Composable
fun GemmaModelDownloadButton(
    downloadStatus: ModelDownloadStatus?,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val status = downloadStatus?.status ?: ModelDownloadStatusType.NOT_DOWNLOADED

    when (status) {
        ModelDownloadStatusType.NOT_DOWNLOADED -> {
            DownloadButton(
                text = "Download AI Model",
                onClick = onDownloadClick,
                enabled = enabled,
                modifier = modifier
            )
        }

        ModelDownloadStatusType.PARTIALLY_DOWNLOADED -> {
            DownloadButton(
                text = "Resume Download",
                onClick = onDownloadClick,
                enabled = enabled,
                modifier = modifier
            )
        }

        ModelDownloadStatusType.IN_PROGRESS -> {
            DownloadProgressIndicator(
                downloadStatus = downloadStatus!!,
                onCancelClick = onCancelClick,
                modifier = modifier
            )
        }

        ModelDownloadStatusType.SUCCEEDED -> {
            SuccessIndicator(modifier = modifier)
        }

        ModelDownloadStatusType.FAILED -> {
            ErrorIndicator(
                errorMessage = downloadStatus?.errorMessage ?: "Unknown error",
                onRetryClick = onDownloadClick,
                modifier = modifier
            )
        }

        ModelDownloadStatusType.CANCELLED -> {
            DownloadButton(
                text = "Download AI Model",
                onClick = onDownloadClick,
                enabled = enabled,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun DownloadButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = Icons.Outlined.FileDownload,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text)
    }
}

@Composable
private fun DownloadProgressIndicator(
    downloadStatus: ModelDownloadStatus,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (downloadStatus.totalBytes > 0) {
        (downloadStatus.receivedBytes.toFloat() / downloadStatus.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val percentage = (progress * 100).toInt()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Progress bar with cancel button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Downloading AI Model...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            IconButton(
                onClick = onCancelClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Cancel download",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Download stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (downloadStatus.bytesPerSecond > 0) {
                val speedMBps = downloadStatus.bytesPerSecond / 1024f / 1024f
                val etaMinutes = downloadStatus.remainingMs / 1000 / 60

                Text(
                    text = "${String.format("%.1f", speedMBps)} MB/s · ${formatETA(etaMinutes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SuccessIndicator(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = "✓ AI Model Ready",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ErrorIndicator(
    errorMessage: String,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Download failed: $errorMessage",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )

        Button(
            onClick = onRetryClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text(text = "Retry Download")
        }
    }
}

/**
 * Format ETA in human-readable format
 */
private fun formatETA(minutes: Long): String {
    return when {
        minutes < 1 -> "< 1 min"
        minutes < 60 -> "$minutes min"
        else -> {
            val hours = minutes / 60
            val mins = minutes % 60
            if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
        }
    }
}

/**
 * Compact version for use in smaller spaces
 */
@Composable
fun CompactGemmaModelDownloadButton(
    downloadStatus: ModelDownloadStatus?,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val status = downloadStatus?.status ?: ModelDownloadStatusType.NOT_DOWNLOADED

    when (status) {
        ModelDownloadStatusType.IN_PROGRESS -> {
            val progress = if (downloadStatus?.totalBytes ?: 0 > 0) {
                (downloadStatus!!.receivedBytes.toFloat() / downloadStatus.totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onCancelClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Cancel",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        ModelDownloadStatusType.SUCCEEDED -> {
            Text(
                text = "✓ Ready",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = modifier
            )
        }

        else -> {
            TextButton(
                onClick = onDownloadClick,
                enabled = enabled,
                modifier = modifier
            ) {
                Icon(
                    imageVector = Icons.Outlined.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Download")
            }
        }
    }
}
