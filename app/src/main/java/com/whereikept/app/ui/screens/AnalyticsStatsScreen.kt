package com.whereikept.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.whereikept.app.data.DeviceMetricEntity
import com.whereikept.app.data.InferenceMetricEntity
import com.whereikept.app.data.VersionMetricEntity
import com.whereikept.app.ui.viewmodels.AnalyticsStatistics
import com.whereikept.app.ui.viewmodels.AnalyticsViewModel
import com.whereikept.app.ui.viewmodels.DeleteState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsStatsScreen(viewModel: AnalyticsViewModel) {
    val statistics by viewModel.statistics.collectAsState()
    val selectedModelType by viewModel.selectedModelType.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val recentInferences by viewModel.recentInferences.collectAsState()
    val versionMetrics by viewModel.versionMetrics.collectAsState()
    val deviceMetrics by viewModel.deviceMetrics.collectAsState()
    val deleteState by viewModel.deleteState.collectAsState()

    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Header
        item {
            Text(
                text = "Performance Stats",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Error message
        item {
            AnimatedVisibility(visible = errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Model Type Selector
        item {
            ModelTypeSelector(
                selectedModelType = selectedModelType,
                onSelect = { viewModel.selectModelType(it) }
            )
        }

        // Loading indicator
        item {
            AnimatedVisibility(visible = isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                }
            }
        }

        // Key Performance Metrics
        item {
            KeyMetricsGrid(statistics = statistics, modelType = selectedModelType)
        }

        // Recent Inferences Section
        if (recentInferences.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Inferences",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(
                items = recentInferences.take(10),
                key = { it.id }
            ) { inference ->
                InferenceCard(inference = inference)
            }
        } else {
            item {
                EmptyStateCard()
            }
        }

        // Device Info Section
        if (deviceMetrics.isNotEmpty()) {
            item {
                Text(
                    text = "Device Info",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                DeviceInfoCard(device = deviceMetrics.first())
            }
        }

        // Version Config Section
        if (versionMetrics.isNotEmpty()) {
            item {
                Text(
                    text = "Model Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                VersionConfigCard(version = versionMetrics.first())
            }
        }

        // Data Management Section
        item {
            DataManagementCard(
                totalInferences = statistics.totalInferences,
                onDeleteAll = { showDeleteConfirmation = true },
                deleteState = deleteState,
                onResetDeleteState = { viewModel.resetDeleteState() }
            )
        }

        // Bottom spacing
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Delete All Metrics?") },
            text = {
                Text("This will permanently delete all stored inference metrics. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllInferences()
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ModelTypeSelector(
    selectedModelType: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedModelType == "gemma",
            onClick = { onSelect("gemma") },
            label = { Text("Gemma (LLM)") },
            leadingIcon = if (selectedModelType == "gemma") {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else null,
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = selectedModelType == "whisper",
            onClick = { onSelect("whisper") },
            label = { Text("Whisper (STT)") },
            leadingIcon = if (selectedModelType == "whisper") {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else null,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun KeyMetricsGrid(statistics: AnalyticsStatistics, modelType: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Timer,
                label = "Avg Inference",
                value = statistics.avgInferenceTimeMs?.let { "%.0f ms".format(it) } ?: "--",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.FlashOn,
                label = "Avg TTFT",
                value = statistics.avgTTFTMs?.let { "%.0f ms".format(it) } ?: "--",
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Speed,
                label = "Decode Speed",
                value = statistics.avgDecodeSpeed?.let { "%.1f tok/s".format(it) } ?: "--",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Memory,
                label = "Avg Peak RAM",
                value = statistics.peakMemoryMb?.let { "%.0f MB".format(it) } ?: "--",
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.CheckCircle,
                label = "Success Rate",
                value = if (statistics.totalInferences > 0) "%.1f%%".format(statistics.successRate) else "--",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Analytics,
                label = "Total Runs",
                value = statistics.totalInferences.toString(),
            )
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun InferenceCard(inference: InferenceMetricEntity) {
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Icon(
                imageVector = if (inference.success) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (inference.success) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = inference.operationType.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = dateFormat.format(Date(inference.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${inference.totalMs}ms",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (inference.outputTokens > 0) {
                        Text(
                            text = "${inference.outputTokens} tok",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (inference.decodeToksPerS > 0) {
                        Text(
                            text = "%.1f tok/s".format(inference.decodeToksPerS),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${inference.peakMemMb}MB",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!inference.success && inference.errorCode != null) {
                    Text(
                        text = "Error: ${inference.errorCode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoCard(device: DeviceMetricEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoRow(label = "Device", value = "${device.manufacturer} ${device.model}")
            InfoRow(label = "Android", value = "API ${device.sdkInt} (${device.osVersion})")
            InfoRow(label = "CPU", value = "${device.cpuModel} (${device.cpuCores} cores)")
            InfoRow(label = "RAM", value = "${device.ramMb} MB")
            InfoRow(label = "GPU", value = device.gpuModel)
            InfoRow(label = "Vulkan", value = if (device.hasVulkan) "Yes (${device.vulkanVersion ?: ""})" else "No")
            InfoRow(label = "NNAPI", value = if (device.hasNnapi) "Yes" else "No")
            InfoRow(label = "GPU Delegate", value = if (device.hasGpuDelegate) "Available" else "Not available")
        }
    }
}

@Composable
private fun VersionConfigCard(version: VersionMetricEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoRow(label = "App Version", value = "${version.versionName} (${version.versionCode})")
            InfoRow(label = "Gemma Model", value = version.gemmaModelVariant)
            InfoRow(label = "Temperature", value = version.gemmaTemperature.toString())
            InfoRow(label = "Top-K", value = version.gemmaTopK.toString())
            InfoRow(label = "Top-P", value = version.gemmaTopP.toString())
            InfoRow(label = "Max Tokens", value = version.gemmaMaxTokens.toString())
            InfoRow(label = "Prompt Version", value = version.gemmaPromptVersion)
            InfoRow(label = "Whisper Model", value = version.whisperModelVariant)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun DataManagementCard(
    totalInferences: Int,
    onDeleteAll: () -> Unit,
    deleteState: DeleteState,
    onResetDeleteState: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Data Management",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "$totalInferences inference records stored locally",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (val state = deleteState) {
                is DeleteState.Success -> {
                    Text(
                        text = "Deleted ${state.deletedCount} records",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LaunchedEffect(state) {
                        kotlinx.coroutines.delay(3000)
                        onResetDeleteState()
                    }
                }
                is DeleteState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {}
            }

            OutlinedButton(
                onClick = onDeleteAll,
                enabled = totalInferences > 0 && deleteState !is DeleteState.Deleting,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (deleteState is DeleteState.Deleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete All Inference Data")
            }
        }
    }
}

@Composable
private fun EmptyStateCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.Analytics,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No inference data yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Use the Capture tab to start recording and analyzing items. Performance metrics will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
