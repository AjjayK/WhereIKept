package com.whereikept.app.data

import android.content.Context
import android.util.Log
import com.whereikept.app.analytics.AnalyticsService
import com.whereikept.app.utils.DeviceInfoCollector
import com.whereikept.app.utils.VersionMetricsCollector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

/**
 * Repository for analytics data operations
 * Abstracts database and Firebase Analytics interactions
 * Provides clean API for ViewModels
 */
class AnalyticsRepository(
    private val context: Context,
    private val database: WhereIKeptDatabase,
    private val analyticsService: AnalyticsService
) {
    private val metricsDao = database.metricsDao()
    private val preferencesManager = AnalyticsPreferencesManager(context)

    companion object {
        private const val TAG = "AnalyticsRepository"
        private const val DEVICE_UPDATE_INTERVAL_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
    }

    // ========== Consent Management ==========

    /**
     * Check if analytics is enabled
     */
    suspend fun isAnalyticsEnabled(): Boolean {
        return preferencesManager.isAnalyticsEnabled()
    }

    /**
     * Get analytics enabled state as Flow for reactive UI
     */
    fun getAnalyticsEnabledFlow(): Flow<Boolean> {
        return preferencesManager.analyticsEnabledFlow
    }

    /**
     * Enable analytics (user opted in)
     */
    suspend fun enableAnalytics() {
        preferencesManager.enableAnalytics()
        analyticsService.enableAnalytics()
        Log.i(TAG, "Analytics enabled by user")
    }

    /**
     * Disable analytics (user opted out)
     */
    suspend fun disableAnalytics() {
        preferencesManager.disableAnalytics()
        analyticsService.disableAnalytics()
        Log.i(TAG, "Analytics disabled by user")
    }

    /**
     * Check if consent dialog has been shown
     */
    suspend fun hasShownConsentDialog(): Boolean {
        return preferencesManager.hasShownConsentDialog()
    }

    /**
     * Mark consent dialog as shown
     */
    suspend fun markConsentDialogShown() {
        preferencesManager.markConsentDialogShown()
    }

    // ========== Version Metrics ==========

    /**
     * Collect and store version metrics (one-time per version)
     * Returns true if this is a new version (metrics were collected)
     */
    suspend fun collectVersionMetricsIfNeeded(versionCode: Int): Boolean {
        try {
            // Check if we already have metrics for this version
            val existing = metricsDao.getVersionMetrics(versionCode)
            if (existing != null) {
                Log.d(TAG, "Version metrics already exist for version $versionCode")
                return false
            }

            // Collect and store new version metrics
            val metrics = VersionMetricsCollector.collectVersionMetrics()
            metricsDao.insertVersionMetrics(metrics)
            Log.i(TAG, "Collected version metrics for ${metrics.versionName}")

            // Log to Firebase if enabled
            analyticsService.logVersionMetrics(metrics)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to collect version metrics", e)
            return false
        }
    }

    /**
     * Get version metrics for a specific version code
     */
    suspend fun getVersionMetrics(versionCode: Int): VersionMetricEntity? {
        return metricsDao.getVersionMetrics(versionCode)
    }

    /**
     * Get all version metrics (for stats screen)
     */
    fun getAllVersionMetrics(): Flow<List<VersionMetricEntity>> {
        return metricsDao.getAllVersionMetrics()
    }

    // ========== Device Metrics ==========

    /**
     * Collect and store device metrics
     * Updates if more than 30 days old, or if never collected
     * Returns true if metrics were collected/updated
     */
    suspend fun collectDeviceMetricsIfNeeded(): Boolean {
        try {
            val collector = DeviceInfoCollector(context)
            val newMetrics = collector.collectDeviceMetrics()

            // Check if we need to update
            val existing = metricsDao.getDeviceMetrics(newMetrics.deviceId)
            val shouldUpdate = existing == null ||
                (System.currentTimeMillis() - existing.lastUpdated) > DEVICE_UPDATE_INTERVAL_MS

            if (!shouldUpdate) {
                Log.d(TAG, "Device metrics are up to date")
                return false
            }

            // Store/update device metrics
            metricsDao.insertDeviceMetrics(newMetrics)
            Log.i(TAG, "Collected device metrics for ${newMetrics.manufacturer} ${newMetrics.model}")

            // Log to Firebase if enabled
            analyticsService.logDeviceMetrics(newMetrics)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to collect device metrics", e)
            return false
        }
    }

    /**
     * Get device metrics for a specific device ID
     */
    suspend fun getDeviceMetrics(deviceId: String): DeviceMetricEntity? {
        return metricsDao.getDeviceMetrics(deviceId)
    }

    /**
     * Get all device metrics (usually just one per device)
     */
    fun getAllDeviceMetrics(): Flow<List<DeviceMetricEntity>> {
        return metricsDao.getAllDeviceMetrics()
    }

    // ========== Inference Metrics ==========

    /**
     * Log an inference metric to database and Firebase
     * Called by LlmService/WhisperService after each inference
     */
    suspend fun logInferenceMetric(metric: InferenceMetricEntity) {
        try {
            // Always store locally (even if analytics disabled)
            val id = metricsDao.insertInferenceMetric(metric)
            Log.d(TAG, "Logged inference metric: ${metric.modelType} ${metric.operationType} (${metric.totalMs}ms)")

            // Log to Firebase if enabled
            analyticsService.logInferenceMetrics(metric)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log inference metric", e)
        }
    }

    /**
     * Get recent inference metrics (for stats screen)
     */
    fun getRecentInferences(limit: Int = 100): Flow<List<InferenceMetricEntity>> {
        return metricsDao.getRecentInferences(limit)
    }

    /**
     * Get inference metrics for a specific model type
     */
    fun getInferencesByModel(modelType: String, limit: Int = 100): Flow<List<InferenceMetricEntity>> {
        return metricsDao.getRecentInferencesByModel(modelType, limit)
    }

    /**
     * Get only failed inferences (for debugging)
     */
    fun getFailedInferences(limit: Int = 50): Flow<List<InferenceMetricEntity>> {
        return metricsDao.getFailedInferences(limit)
    }

    // ========== Statistics ==========

    /**
     * Get average inference time for a model type
     */
    suspend fun getAverageInferenceTime(modelType: String): Float? {
        return metricsDao.getAverageTotalMs(modelType)
    }

    /**
     * Get average TTFT for a model type
     */
    suspend fun getAverageTTFT(modelType: String): Float? {
        return metricsDao.getAverageTtftMs(modelType)
    }

    /**
     * Get average decode speed for a model type
     */
    suspend fun getAverageDecodeSpeed(modelType: String): Float? {
        return metricsDao.getAverageDecodeToksPerS(modelType)
    }

    /**
     * Get peak memory usage for a model type
     */
    suspend fun getPeakMemory(modelType: String): Float? {
        return metricsDao.getAveragePeakMemMb(modelType)
    }

    /**
     * Get success rate for a model type
     */
    suspend fun getSuccessRate(modelType: String): Float {
        val total = metricsDao.getTotalInferences(modelType)
        val successful = metricsDao.getSuccessfulInferences(modelType)
        return if (total > 0) (successful.toFloat() / total.toFloat()) * 100f else 0f
    }

    /**
     * Get error distribution (for debugging)
     */
    suspend fun getErrorDistribution(): Map<String, Int> {
        // Error distribution from raw query - simplified approach
        val failed = metricsDao.getFailedInferenceCount()
        return if (failed > 0) mapOf("errors" to failed) else emptyMap()
    }

    /**
     * Get inference count by operation type
     */
    suspend fun getInferenceCountByOperation(): Map<String, Int> {
        val gemmaCount = metricsDao.getInferenceCountByModel("gemma")
        val whisperCount = metricsDao.getInferenceCountByModel("whisper")
        return buildMap {
            if (gemmaCount > 0) put("gemma", gemmaCount)
            if (whisperCount > 0) put("whisper", whisperCount)
        }
    }

    // ========== Data Export ==========

    /**
     * Export all inference metrics as list (for data export feature)
     */
    suspend fun exportAllInferences(): List<InferenceMetricEntity> {
        return metricsDao.getSlowestInferences("gemma", 1000) +
               metricsDao.getSlowestInferences("whisper", 1000)
    }

    // ========== Data Management ==========

    /**
     * Delete old inference metrics (older than specified time)
     * Returns number of deleted rows
     */
    suspend fun deleteOldInferences(olderThanMs: Long): Int {
        val count = metricsDao.deleteOldInferences(olderThanMs)
        Log.i(TAG, "Deleted $count old inference metrics")
        return count
    }

    /**
     * Delete all inference metrics (for privacy/reset)
     * Returns number of deleted rows
     */
    suspend fun deleteAllInferences(): Int {
        val count = metricsDao.deleteAllInferences()
        Log.i(TAG, "Deleted all $count inference metrics")
        return count
    }

    /**
     * Get total count of stored inference metrics
     */
    suspend fun getTotalInferenceCount(): Int {
        return metricsDao.getInferenceCount()
    }

    /**
     * Get database size estimate (sum of all metrics)
     */
    suspend fun getMetricsCount(): MetricsCount {
        return MetricsCount(
            versionMetrics = 1, // One per version
            deviceMetrics = 1,  // One per device
            inferenceMetrics = metricsDao.getInferenceCount()
        )
    }

    data class MetricsCount(
        val versionMetrics: Int,
        val deviceMetrics: Int,
        val inferenceMetrics: Int
    ) {
        val total: Int get() = versionMetrics + deviceMetrics + inferenceMetrics
    }

    // ========== Configuration Summary ==========

    /**
     * Get current configuration summary (for debug screen)
     */
    fun getConfigurationSummary(): String {
        return VersionMetricsCollector.getConfigurationSummary()
    }
}
