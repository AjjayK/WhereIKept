package com.whereikept.app.utils

import com.whereikept.app.BuildConfig
import com.whereikept.app.data.VersionMetricEntity

/**
 * Collects version-level metrics for A/B testing and configuration tracking
 * Collected once per app version upgrade
 *
 * AUTOMATIC EXTRACTION: LLM parameters are read directly from LlmService,
 * so any changes to temperature/topK/topP are automatically tracked.
 */
object VersionMetricsCollector {

    // Whisper Configuration Constants (hardcoded for now)
    private const val WHISPER_MODEL_VARIANT = "whisper-tiny"

    // Prompt Version Tracking - INCREMENT THIS when you change prompts
    private const val PROMPT_VERSION = "v1.0"

    // Model variant - UPDATE THIS if you change the Gemma model
    private const val GEMMA_MODEL_VARIANT = "gemma-3n-e2b-it-int4"

    // System Prompts (for hashing to detect changes)
    private const val GEMMA_SYSTEM_PROMPT_VOICE =
        "You are a JSON extraction assistant. Extract object and location information from text. Respond ONLY with valid JSON."

    private const val GEMMA_SYSTEM_PROMPT_IMAGE =
        "You are a JSON extraction assistant. Analyze images and text to extract object and location information. Respond ONLY with valid JSON."

    private const val GEMMA_SYSTEM_PROMPT_OPTIMIZATION =
        "You are a JSON optimization assistant. Analyze the screenshot showing user-positioned tags on an image, combine with transcript context, and optimize the JSON. Respond ONLY with valid JSON."

    /**
     * Collect version metrics for current app version
     * Automatically extracts current LLM parameters from LlmService
     */
    fun collectVersionMetrics(): VersionMetricEntity {
        return VersionMetricEntity(
            versionCode = BuildConfig.VERSION_CODE,
            versionName = BuildConfig.VERSION_NAME,
            timestamp = System.currentTimeMillis(),
            configVariant = getConfigVariant(),

            // Gemma Parameters - automatically read from LlmService
            gemmaTemperature = LlmService.DEFAULT_TEMPERATURE.toFloat(),
            gemmaTopK = LlmService.DEFAULT_TOPK,
            gemmaTopP = LlmService.DEFAULT_TOPP.toFloat(),
            gemmaMaxTokens = LlmService.DEFAULT_MAX_TOKEN,
            gemmaPromptVersion = PROMPT_VERSION,
            gemmaSystemPromptHash = getSystemPromptHash(),
            gemmaModelVariant = GEMMA_MODEL_VARIANT,

            // Whisper Parameters
            whisperModelVariant = WHISPER_MODEL_VARIANT,
            whisperBeamSize = null,  // Not specified in current implementation
            whisperLanguage = null   // Auto-detect
        )
    }

    /**
     * Get config variant for A/B testing
     * Currently returns "default" for all users
     * Future: Can be extended to assign variants based on device ID hash
     */
    private fun getConfigVariant(): String {
        // Infrastructure prepared but not activated
        // All users get "default" variant
        return "default"
    }

    /**
     * Example for future A/B testing:
     * Assign users to variant_a or variant_b based on device ID hash
     */
    @Suppress("unused")
    private fun getConfigVariantForABTesting(deviceId: String): String {
        // Use hash to ensure same device always gets same variant
        return if (deviceId.hashCode() % 2 == 0) "variant_a" else "variant_b"
    }

    /**
     * Hash all system prompts to detect changes
     * If prompts change, this hash will change, helping track performance differences
     */
    private fun getSystemPromptHash(): String {
        val combinedPrompt = listOf(
            GEMMA_SYSTEM_PROMPT_VOICE,
            GEMMA_SYSTEM_PROMPT_IMAGE,
            GEMMA_SYSTEM_PROMPT_OPTIMIZATION
        ).joinToString("|")

        return combinedPrompt.hashCode().toString()
    }

    /**
     * Get current configuration as human-readable string (for debugging)
     */
    fun getConfigurationSummary(): String {
        return buildString {
            appendLine("=== LLM Configuration ===")
            appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Variant: ${getConfigVariant()}")
            appendLine()
            appendLine("Gemma Model: $GEMMA_MODEL_VARIANT")
            appendLine("  Temperature: ${LlmService.DEFAULT_TEMPERATURE}")
            appendLine("  Top-K: ${LlmService.DEFAULT_TOPK}")
            appendLine("  Top-P: ${LlmService.DEFAULT_TOPP}")
            appendLine("  Max Tokens: ${LlmService.DEFAULT_MAX_TOKEN}")
            appendLine("  Prompt Version: $PROMPT_VERSION")
            appendLine()
            appendLine("Whisper Model: $WHISPER_MODEL_VARIANT")
        }
    }
}
