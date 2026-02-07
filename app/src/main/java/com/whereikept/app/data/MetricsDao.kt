package com.whereikept.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MetricsDao {

    // ========== Version Metrics ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersionMetrics(versionMetric: VersionMetricEntity)

    @Query("SELECT * FROM version_metrics WHERE versionCode = :versionCode LIMIT 1")
    suspend fun getVersionMetrics(versionCode: Int): VersionMetricEntity?

    @Query("SELECT * FROM version_metrics ORDER BY versionCode DESC")
    fun getAllVersionMetrics(): Flow<List<VersionMetricEntity>>

    @Query("SELECT * FROM version_metrics ORDER BY versionCode DESC LIMIT 1")
    suspend fun getLatestVersionMetrics(): VersionMetricEntity?

    // ========== Device Metrics ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeviceMetrics(deviceMetric: DeviceMetricEntity)

    @Update
    suspend fun updateDeviceMetrics(deviceMetric: DeviceMetricEntity)

    @Query("SELECT * FROM device_metrics WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getDeviceMetrics(deviceId: String): DeviceMetricEntity?

    @Query("SELECT * FROM device_metrics LIMIT 1")
    suspend fun getAnyDeviceMetrics(): DeviceMetricEntity?

    @Query("SELECT * FROM device_metrics")
    fun getAllDeviceMetrics(): Flow<List<DeviceMetricEntity>>

    // ========== Inference Metrics ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInferenceMetric(inferenceMetric: InferenceMetricEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInferenceMetrics(inferenceMetrics: List<InferenceMetricEntity>)

    @Query("SELECT * FROM inference_metrics WHERE id = :id LIMIT 1")
    suspend fun getInferenceMetric(id: Long): InferenceMetricEntity?

    @Query("SELECT * FROM inference_metrics ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentInferences(limit: Int = 100): Flow<List<InferenceMetricEntity>>

    @Query("SELECT * FROM inference_metrics WHERE modelType = :modelType ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentInferencesByModel(modelType: String, limit: Int = 100): Flow<List<InferenceMetricEntity>>

    @Query("SELECT * FROM inference_metrics WHERE operationType = :operationType ORDER BY timestamp DESC LIMIT :limit")
    fun getInferencesByOperation(operationType: String, limit: Int = 100): Flow<List<InferenceMetricEntity>>

    @Query("SELECT * FROM inference_metrics WHERE success = 0 ORDER BY timestamp DESC LIMIT :limit")
    fun getFailedInferences(limit: Int = 100): Flow<List<InferenceMetricEntity>>

    @Query("SELECT * FROM inference_metrics WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getInferencesByTimeRange(startTime: Long, endTime: Long): Flow<List<InferenceMetricEntity>>

    // ========== Statistics & Aggregates ==========

    @Query("""
        SELECT COUNT(*) as totalInferences
        FROM inference_metrics
        WHERE modelType = :modelType
    """)
    suspend fun getTotalInferences(modelType: String): Int

    @Query("""
        SELECT COUNT(*) as successfulInferences
        FROM inference_metrics
        WHERE modelType = :modelType AND success = 1
    """)
    suspend fun getSuccessfulInferences(modelType: String): Int

    @Query("""
        SELECT AVG(totalMs) as avgTotalMs
        FROM inference_metrics
        WHERE modelType = :modelType AND success = 1
    """)
    suspend fun getAverageTotalMs(modelType: String): Float?

    @Query("""
        SELECT AVG(ttftMs) as avgTtftMs
        FROM inference_metrics
        WHERE modelType = :modelType AND success = 1 AND ttftMs > 0
    """)
    suspend fun getAverageTtftMs(modelType: String): Float?

    @Query("""
        SELECT AVG(decodeToksPerS) as avgDecodeToksPerS
        FROM inference_metrics
        WHERE modelType = :modelType AND success = 1 AND decodeToksPerS > 0
    """)
    suspend fun getAverageDecodeToksPerS(modelType: String): Float?

    @Query("""
        SELECT AVG(peakMemMb) as avgPeakMemMb
        FROM inference_metrics
        WHERE modelType = :modelType AND success = 1
    """)
    suspend fun getAveragePeakMemMb(modelType: String): Float?

    @Query("""
        SELECT totalMs
        FROM inference_metrics
        WHERE modelType = :modelType AND success = 1
        ORDER BY totalMs ASC
    """)
    suspend fun getAllTotalMsForPercentile(modelType: String): List<Long>

    @Query("""
        SELECT
            strftime('%Y-%m-%d', datetime(timestamp / 1000, 'unixepoch')) as date,
            modelType,
            COUNT(*) as inferenceCount,
            AVG(totalMs) as avgTotalMs,
            AVG(decodeToksPerS) as avgDecodeToksPerS,
            (SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) * 100.0 / COUNT(*)) as successRate
        FROM inference_metrics
        WHERE timestamp >= :startTime
        GROUP BY date, modelType
        ORDER BY date DESC
    """)
    suspend fun getDailyStats(startTime: Long): List<DailyInferenceStat>

    @Query("""
        SELECT errorCode, COUNT(*) as count
        FROM inference_metrics
        WHERE success = 0 AND errorCode IS NOT NULL
        GROUP BY errorCode
        ORDER BY count DESC
    """)
    suspend fun getErrorCodeDistribution(): List<ErrorCodeDistribution>

    @Query("""
        SELECT acceleratorUsed, COUNT(*) as count, AVG(totalMs) as avgTotalMs
        FROM inference_metrics
        WHERE success = 1 AND modelType = :modelType
        GROUP BY acceleratorUsed
        ORDER BY count DESC
    """)
    suspend fun getAcceleratorPerformance(modelType: String): List<AcceleratorPerformanceStat>

    // ========== Cleanup Operations ==========

    @Query("DELETE FROM inference_metrics WHERE timestamp < :olderThan")
    suspend fun deleteOldInferences(olderThan: Long): Int

    @Query("DELETE FROM inference_metrics")
    suspend fun deleteAllInferences(): Int

    @Query("DELETE FROM version_metrics")
    suspend fun deleteAllVersionMetrics(): Int

    @Query("DELETE FROM device_metrics")
    suspend fun deleteAllDeviceMetrics(): Int

    @Query("""
        DELETE FROM inference_metrics
        WHERE id NOT IN (
            SELECT id FROM inference_metrics
            ORDER BY timestamp DESC
            LIMIT :keepCount
        )
    """)
    suspend fun keepOnlyRecentInferences(keepCount: Int): Int

    // ========== Count Queries ==========

    @Query("SELECT COUNT(*) FROM inference_metrics")
    suspend fun getInferenceCount(): Int

    @Query("SELECT COUNT(*) FROM inference_metrics WHERE modelType = :modelType")
    suspend fun getInferenceCountByModel(modelType: String): Int

    @Query("SELECT COUNT(*) FROM inference_metrics WHERE success = 1")
    suspend fun getSuccessfulInferenceCount(): Int

    @Query("SELECT COUNT(*) FROM inference_metrics WHERE success = 0")
    suspend fun getFailedInferenceCount(): Int

    // ========== Performance Monitoring ==========

    @Query("""
        SELECT *
        FROM inference_metrics
        WHERE modelType = :modelType
        AND success = 1
        ORDER BY totalMs DESC
        LIMIT :limit
    """)
    suspend fun getSlowestInferences(modelType: String, limit: Int = 10): List<InferenceMetricEntity>

    @Query("""
        SELECT *
        FROM inference_metrics
        WHERE modelType = :modelType
        AND success = 1
        ORDER BY totalMs ASC
        LIMIT :limit
    """)
    suspend fun getFastestInferences(modelType: String, limit: Int = 10): List<InferenceMetricEntity>

    @Query("""
        SELECT *
        FROM inference_metrics
        WHERE modelType = :modelType
        AND success = 1
        ORDER BY peakMemMb DESC
        LIMIT :limit
    """)
    suspend fun getHighestMemoryInferences(modelType: String, limit: Int = 10): List<InferenceMetricEntity>
}
