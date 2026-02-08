package com.whereikept.app.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
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
 * Tracks memory (JVM + native), thermal status, and battery drain
 * Samples every 100ms during inference
 */
class ResourceMonitor private constructor(private val context: Context) {

    private var peakHeapMb: Int = 0
    private var peakNativeMb: Int = 0
    private var peakTotalPssMb: Int = 0
    private var thermalStart: Int = 0
    private var batteryStart: Int = 0

    private var monitorJob: Job? = null

    /**
     * Resource statistics collected during inference
     */
    data class ResourceStats(
        val peakMemMb: Int,           // Total PSS (Proportional Set Size) - best overall memory metric
        val peakHeapMb: Int,          // JVM/Dalvik heap memory
        val peakNativeMb: Int,        // Native memory (where LLM weights live)
        val thermalStatusStart: Int,
        val thermalStatusEnd: Int,
        val batteryLevelStart: Int,
        val batteryLevelEnd: Int,
        val cpuUsageAvg: Float        // Reserved for future use
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
        // Initialize with current memory readings
        val initialMemory = getDetailedMemory()
        peakHeapMb = initialMemory.heapMb
        peakNativeMb = initialMemory.nativeMb
        peakTotalPssMb = initialMemory.totalPssMb
        thermalStart = getThermalStatus()
        batteryStart = getBatteryLevel()

        // Sample memory every 100ms during inference
        monitorJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                val currentMemory = getDetailedMemory()
                peakHeapMb = maxOf(peakHeapMb, currentMemory.heapMb)
                peakNativeMb = maxOf(peakNativeMb, currentMemory.nativeMb)
                peakTotalPssMb = maxOf(peakTotalPssMb, currentMemory.totalPssMb)
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
            peakMemMb = peakTotalPssMb,  // Use total PSS as the primary memory metric
            peakHeapMb = peakHeapMb,
            peakNativeMb = peakNativeMb,
            thermalStatusStart = thermalStart,
            thermalStatusEnd = getThermalStatus(),
            batteryLevelStart = batteryStart,
            batteryLevelEnd = getBatteryLevel(),
            cpuUsageAvg = 0f  // Reserved for future use
        )
    }

    /**
     * Detailed memory breakdown
     */
    private data class DetailedMemory(
        val heapMb: Int,      // JVM/Dalvik heap
        val nativeMb: Int,    // Native allocations (where LLM model weights live)
        val totalPssMb: Int   // Proportional Set Size - real memory footprint
    )

    /**
     * Get detailed memory usage using Debug.MemoryInfo
     * This captures JVM heap, native memory, and total PSS
     */
    private fun getDetailedMemory(): DetailedMemory {
        return try {
            val memoryInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memoryInfo)

            DetailedMemory(
                heapMb = memoryInfo.dalvikPrivateDirty / 1024,  // KB to MB
                nativeMb = memoryInfo.nativePrivateDirty / 1024,
                totalPssMb = memoryInfo.totalPss / 1024
            )
        } catch (e: Exception) {
            // Fallback to simple JVM heap measurement
            val runtime = Runtime.getRuntime()
            val heapMb = ((runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024).toInt()
            DetailedMemory(heapMb, 0, heapMb)
        }
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

}
