package com.whereikept.app.data

/**
 * Daily aggregated inference stats by date and model type.
 */
data class DailyInferenceStat(
    val date: String,
    val modelType: String,
    val inferenceCount: Int,
    val avgTotalMs: Double?,
    val avgDecodeToksPerS: Double?,
    val successRate: Double
)

/**
 * Error code counts for failed inferences.
 */
data class ErrorCodeDistribution(
    val errorCode: String?,
    val count: Int
)

/**
 * Aggregate performance grouped by accelerator used.
 */
data class AcceleratorPerformanceStat(
    val acceleratorUsed: String?,
    val count: Int,
    val avgTotalMs: Double?
)
