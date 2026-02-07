package com.whereikept.app.utils

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.google.firebase.installations.FirebaseInstallations
import com.whereikept.app.data.DeviceMetricEntity
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Collects device hardware specifications and AI acceleration capabilities
 * One-time collection on first launch, updated periodically (every 30 days)
 */
class DeviceInfoCollector(private val context: Context) {

    /**
     * Collect all device metrics
     * This is an expensive operation - only call once per launch or when updating
     */
    suspend fun collectDeviceMetrics(): DeviceMetricEntity {
        return DeviceMetricEntity(
            deviceId = getFirebaseInstallationId(),
            timestamp = System.currentTimeMillis(),
            lastUpdated = System.currentTimeMillis(),

            // Device Info
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            brand = Build.BRAND,
            osVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,

            // CPU
            cpuModel = getCpuModel(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            cpuMaxFreqMhz = getCpuMaxFreq(),

            // GPU
            gpuVendor = getGpuVendor(),
            gpuModel = getGpuModel(),

            // Memory/Storage
            ramMb = getTotalRam(),
            storageTotalGb = getTotalStorage(),
            storageAvailableGb = getAvailableStorage(),

            // AI Acceleration
            hasVulkan = checkVulkanSupport(),
            vulkanVersion = getVulkanVersion(),
            hasNnapi = checkNnapiSupport(),
            nnapiVersion = getNnapiVersion(),
            hasGpuDelegate = checkGpuDelegateSupport(),
            hasHexagonDelegate = checkHexagonSupport(),

            // Runtime
            aiEdgeLitertVersion = getAiEdgeLitertVersion(),
            tfliteVersion = getTfliteVersion()
        )
    }

    /**
     * Get Firebase Installation ID for anonymous device identification
     */
    private suspend fun getFirebaseInstallationId(): String {
        return try {
            FirebaseInstallations.getInstance().id.await()
        } catch (e: Exception) {
            "unknown_device"
        }
    }

    /**
     * Read CPU model from /proc/cpuinfo
     */
    private fun getCpuModel(): String {
        return try {
            File("/proc/cpuinfo").readLines()
                .firstOrNull { it.startsWith("Hardware") || it.startsWith("Processor") }
                ?.substringAfter(":")?.trim()
                ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Get maximum CPU frequency across all cores
     */
    private fun getCpuMaxFreq(): Int {
        return try {
            val cores = Runtime.getRuntime().availableProcessors()
            (0 until cores).maxOfOrNull { cpu ->
                File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq")
                    .readText().trim().toIntOrNull() ?: 0
            }?.div(1000) ?: 0  // Convert kHz to MHz
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Get GPU vendor (requires OpenGL ES context - returns Unknown for now)
     */
    private fun getGpuVendor(): String {
        // Getting GPU vendor requires EGL context which we don't have here
        // This would need to be queried from a GLSurfaceView or similar
        return "Unknown"
    }

    /**
     * Get GPU model (requires OpenGL ES context - returns Unknown for now)
     */
    private fun getGpuModel(): String {
        // Getting GPU model requires EGL context which we don't have here
        // This would need to be queried from a GLSurfaceView or similar
        return "Unknown"
    }

    /**
     * Get total device RAM in MB
     */
    private fun getTotalRam(): Int {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            (memInfo.totalMem / 1024 / 1024).toInt()  // Convert to MB
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Get total storage in GB
     */
    private fun getTotalStorage(): Int {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            (totalBytes / 1024 / 1024 / 1024).toInt()  // Convert to GB
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Get available storage in GB
     */
    private fun getAvailableStorage(): Int {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            (availableBytes / 1024 / 1024 / 1024).toInt()  // Convert to GB
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Check if device supports Vulkan
     */
    private fun checkVulkanSupport(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        } else {
            false
        }
    }

    /**
     * Get Vulkan version (if available)
     */
    private fun getVulkanVersion(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && checkVulkanSupport()) {
            try {
                val pm = context.packageManager
                val vulkanVersion = pm.getSystemAvailableFeatures()
                    .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL }
                    ?.version
                vulkanVersion?.toString()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    /**
     * Check if device supports NNAPI (Neural Networks API)
     */
    private fun checkNnapiSupport(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P  // NNAPI introduced in API 27 (Android 8.1)
    }

    /**
     * Get NNAPI version
     */
    private fun getNnapiVersion(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            "1.0+"  // Basic NNAPI available
        } else {
            null
        }
    }

    /**
     * Check if TFLite GPU delegate is available
     * This is a heuristic - actual availability depends on TFLite library presence
     */
    private fun checkGpuDelegateSupport(): Boolean {
        return try {
            // Check if we have OpenGL ES 3.1+ (required for GPU delegate)
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val glVersion = activityManager.deviceConfigurationInfo.glEsVersion.toDoubleOrNull() ?: 0.0
            glVersion >= 3.1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if Hexagon delegate (Qualcomm DSP) is available
     * This is a heuristic based on CPU model
     */
    private fun checkHexagonSupport(): Boolean {
        val cpuModel = getCpuModel().lowercase()
        return cpuModel.contains("qualcomm") || cpuModel.contains("snapdragon")
    }

    /**
     * Get AI Edge LiteRT version
     * This should match the version in build.gradle
     */
    private fun getAiEdgeLitertVersion(): String {
        return "0.9.0-alpha01"  // Hardcoded to match build.gradle dependency
    }

    /**
     * Get TFLite version (if available)
     */
    private fun getTfliteVersion(): String? {
        return try {
            // Try to get TFLite version from class metadata
            // This may not work in all cases
            "2.0+"  // Placeholder - actual version detection is complex
        } catch (e: Exception) {
            null
        }
    }
}
