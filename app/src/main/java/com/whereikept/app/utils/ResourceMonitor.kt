package com.whereikept.app.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real-time resource monitoring during LLM inference
 * Tracks memory, CPU, thermal status, and battery drain
 * Samples every 100ms during inference
 */
class ResourceMonitor private constructor(private val context: Context) {

    private var startMemMb: Int = 0
    private var peakMemMb: Int = 0
    private var thermalStart: Int = 0
    private var batteryStart: Int = 0
    private val cpuSamples = mutableListOf<Float>()

    private var monitorJob: Job? = null

    /**
     * Resource statistics collected during inference
     */
    data class ResourceStats(
        val peakMemMb: Int,
        val thermalStatusStart: Int,
        val thermalStatusEnd: Int,
        val batteryLevelStart: Int,
        val batteryLevelEnd: Int,
        val cpuUsageAvg: Float
    )

    companion object {
        /**
         * Create a new ResourceMonitor instance
         */
        fun start(context: Context): ResourceMonitor {
            return ResourceMonitor(context)
        }
    }

    /**
     * Start monitoring resources
     * Call this before starting inference
     */
    fun startMonitoring() {
        startMemMb = getCurrentMemoryMb()
        peakMemMb = startMemMb
        thermalStart = getThermalStatus()
        batteryStart = getBatteryLevel()

        // Sample CPU/memory every 100ms during inference
        monitorJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                val currentMem = getCurrentMemoryMb()
                if (currentMem > peakMemMb) {
                    peakMemMb = currentMem
                }
                cpuSamples.add(getCpuUsage())
                delay(100)
            }
        }
    }

    /**
     * Stop monitoring and return collected stats
     * Call this after inference completes
     */
    fun stopMonitoring(): ResourceStats {
        monitorJob?.cancel()

        return ResourceStats(
            peakMemMb = peakMemMb,
            thermalStatusStart = thermalStart,
            thermalStatusEnd = getThermalStatus(),
            batteryLevelStart = batteryStart,
            batteryLevelEnd = getBatteryLevel(),
            cpuUsageAvg = if (cpuSamples.isNotEmpty()) cpuSamples.average().toFloat() else 0f
        )
    }

    /**
     * Get current app memory usage in MB
     */
    private fun getCurrentMemoryMb(): Int {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        return usedMemory.toInt()
    }

    /**
     * Get current thermal status (0-6 scale)
     * Requires Android Q (API 29) or higher
     */
    private fun getThermalStatus(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                powerManager?.currentThermalStatus ?: 0
            } catch (e: Exception) {
                0
            }
        } else {
            0  // THERMAL_STATUS_NONE
        }
    }

    /**
     * Get current battery level (0-100 percentage)
     */
    private fun getBatteryLevel(): Int {
        return try {
            val batteryIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

            if (level >= 0 && scale > 0) {
                (level.toFloat() / scale.toFloat() * 100).toInt()
            } else {
                -1
            }
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Get current CPU usage percentage
     * This is a simplified implementation - returns 0 for now
     * TODO: Implement proper CPU usage calculation from /proc/stat
     */
    private fun getCpuUsage(): Float {
        // Reading /proc/stat requires complex parsing and tracking between samples
        // For now, return 0 - can be enhanced later
        // Full implementation would track user/system/idle times between samples
        return 0f
    }
}
