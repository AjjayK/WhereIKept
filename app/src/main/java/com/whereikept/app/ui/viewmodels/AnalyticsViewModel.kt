package com.whereikept.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whereikept.app.data.AnalyticsRepository
import com.whereikept.app.data.DeviceMetricEntity
import com.whereikept.app.data.InferenceMetricEntity
import com.whereikept.app.data.VersionMetricEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for Analytics Statistics Screen
 * Manages UI state for analytics data visualization
 */
class AnalyticsViewModel(
    application: Application,
    private val repository: AnalyticsRepository
) : AndroidViewModel(application) {

    // ========== Analytics Enabled State ==========

    /**
     * Current analytics enabled state (reactive)
     */
    val analyticsEnabled: StateFlow<Boolean> = repository.getAnalyticsEnabledFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * Toggle analytics on/off
     */
    fun toggleAnalytics(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                repository.enableAnalytics()
            } else {
                repository.disableAnalytics()
            }
        }
    }

    // ========== Version Metrics ==========

    /**
     * All version metrics (historical config tracking)
     */
    val versionMetrics: StateFlow<List<VersionMetricEntity>> = repository.getAllVersionMetrics()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // ========== Device Metrics ==========

    /**
     * Device metrics (usually just one entry)
     */
    val deviceMetrics: StateFlow<List<DeviceMetricEntity>> = repository.getAllDeviceMetrics()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // ========== Inference Metrics ==========

    /**
     * Recent inference metrics (last 100)
     */
    val recentInferences: StateFlow<List<InferenceMetricEntity>> = repository.getRecentInferences(100)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Gemma-specific inferences
     */
    val gemmaInferences: StateFlow<List<InferenceMetricEntity>> = repository.getInferencesByModel("gemma", 100)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Whisper-specific inferences
     */
    val whisperInferences: StateFlow<List<InferenceMetricEntity>> = repository.getInferencesByModel("whisper", 100)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Failed inferences (for debugging)
     */
    val failedInferences: StateFlow<List<InferenceMetricEntity>> = repository.getFailedInferences(50)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // ========== Statistics State ==========

    private val _statistics = MutableStateFlow(AnalyticsStatistics())
    val statistics: StateFlow<AnalyticsStatistics> = _statistics.asStateFlow()

    /**
     * Selected model type for stats display
     */
    private val _selectedModelType = MutableStateFlow("gemma")
    val selectedModelType: StateFlow<String> = _selectedModelType.asStateFlow()

    /**
     * Loading state
     */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Error state
     */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // Load initial statistics
        loadStatistics()
    }

    /**
     * Change selected model type (gemma/whisper)
     */
    fun selectModelType(modelType: String) {
        _selectedModelType.value = modelType
        loadStatistics()
    }

    /**
     * Reload statistics from database
     */
    fun loadStatistics() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                val modelType = _selectedModelType.value
                val stats = AnalyticsStatistics(
                    avgInferenceTimeMs = repository.getAverageInferenceTime(modelType),
                    avgTTFTMs = repository.getAverageTTFT(modelType),
                    avgDecodeSpeed = repository.getAverageDecodeSpeed(modelType),
                    peakMemoryMb = repository.getPeakMemory(modelType),
                    successRate = repository.getSuccessRate(modelType),
                    totalInferences = repository.getTotalInferenceCount(),
                    errorDistribution = repository.getErrorDistribution(),
                    operationCounts = repository.getInferenceCountByOperation()
                )

                _statistics.value = stats
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load statistics: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ========== Data Export ==========

    /**
     * Export state
     */
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    /**
     * Export all inference metrics as CSV/JSON
     */
    fun exportInferences(format: ExportFormat = ExportFormat.CSV) {
        viewModelScope.launch {
            try {
                _exportState.value = ExportState.Exporting
                val inferences = repository.exportAllInferences()

                // Convert to format (implementation depends on export format)
                // For now, just mark as complete
                _exportState.value = ExportState.Success(inferences.size)
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "Export failed")
            }
        }
    }

    /**
     * Reset export state
     */
    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }

    // ========== Data Management ==========

    private val _deleteState = MutableStateFlow<DeleteState>(DeleteState.Idle)
    val deleteState: StateFlow<DeleteState> = _deleteState.asStateFlow()

    /**
     * Delete old inference metrics (older than specified days)
     */
    fun deleteOldInferences(olderThanDays: Int) {
        viewModelScope.launch {
            try {
                _deleteState.value = DeleteState.Deleting
                val olderThanMs = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
                val deletedCount = repository.deleteOldInferences(olderThanMs)
                _deleteState.value = DeleteState.Success(deletedCount)

                // Reload statistics after deletion
                loadStatistics()
            } catch (e: Exception) {
                _deleteState.value = DeleteState.Error(e.message ?: "Delete failed")
            }
        }
    }

    /**
     * Delete all inference metrics
     */
    fun deleteAllInferences() {
        viewModelScope.launch {
            try {
                _deleteState.value = DeleteState.Deleting
                val deletedCount = repository.deleteAllInferences()
                _deleteState.value = DeleteState.Success(deletedCount)

                // Reload statistics after deletion
                loadStatistics()
            } catch (e: Exception) {
                _deleteState.value = DeleteState.Error(e.message ?: "Delete failed")
            }
        }
    }

    /**
     * Reset delete state
     */
    fun resetDeleteState() {
        _deleteState.value = DeleteState.Idle
    }

    // ========== Configuration Info ==========

    /**
     * Get current LLM configuration summary
     */
    fun getConfigurationSummary(): String {
        return repository.getConfigurationSummary()
    }

    /**
     * Get metrics count (for storage usage display)
     */
    suspend fun getMetricsCount(): AnalyticsRepository.MetricsCount {
        return repository.getMetricsCount()
    }
}

/**
 * Statistics data class for display
 */
data class AnalyticsStatistics(
    val avgInferenceTimeMs: Float? = null,
    val avgTTFTMs: Float? = null,
    val avgDecodeSpeed: Float? = null,
    val peakMemoryMb: Float? = null,
    val successRate: Float = 0f,
    val totalInferences: Int = 0,
    val errorDistribution: Map<String, Int> = emptyMap(),
    val operationCounts: Map<String, Int> = emptyMap()
)

/**
 * Export state
 */
sealed class ExportState {
    data object Idle : ExportState()
    data object Exporting : ExportState()
    data class Success(val count: Int) : ExportState()
    data class Error(val message: String) : ExportState()
}

/**
 * Delete state
 */
sealed class DeleteState {
    data object Idle : DeleteState()
    data object Deleting : DeleteState()
    data class Success(val deletedCount: Int) : DeleteState()
    data class Error(val message: String) : DeleteState()
}

/**
 * Export format
 */
enum class ExportFormat {
    CSV,
    JSON
}
