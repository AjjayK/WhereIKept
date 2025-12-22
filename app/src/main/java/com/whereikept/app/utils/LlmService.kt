package com.whereikept.app.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.fold
import java.io.File

/**
 * LLM-based extraction service using LiteRT-LM with Gemma 3n E2B.
 * Extracts structured object and location information from transcribed audio.
 */
class LlmService(private val context: Context) {

    // Data models for JSON extraction
    data class ExtractedItem(
        @SerializedName("object") val objectName: String,
        val location: String,
        val nearby: String? = null,
        @SerializedName("time_hint") val timeHint: String? = null,
        val confidence: Float = 0.0f,
        val evidence: String
    )

    data class ExtractionResponse(
        val items: List<ExtractedItem>
    )

    private var engine: Engine? = null
    private var isInitialized = false
    private var currentBackend: Backend = Backend.GPU  // Track which backend is being used
    private val gson = Gson()

    companion object {
        private const val TAG = "LlmService"
        // Gemma 3n E2B - Optimized for mobile/edge devices (2025)
        private const val MODEL_DIR_NAME = "models"
        private val MODEL_NAMES = listOf(
            "gemma-3n-e2b-it-int4.litertlm",  // LiteRT LM format (preferred)
            "gemma-3n-e2b-it-int4.task",      // MediaPipe Task format
            "gemma-3n-e2b-it.litertlm",       // Alternative naming
            "gemma-3n-e2b-it.task",           // Alternative task naming
            "model.litertlm",                  // Generic LiteRT LM file
            "model.task",                      // Generic task file
            "model.bin",                       // Generic binary file
            "model.tflite"                     // Generic TFLite file
        )
        private val MODEL_EXTENSIONS = listOf(".litertlm", ".task", ".bin", ".tflite")

        // Default LLM configuration (from Google AI Edge Gallery)
        private const val DEFAULT_MAX_TOKEN = 1024
        private const val DEFAULT_TOPK = 64
        private const val DEFAULT_TOPP = 0.95
        private const val DEFAULT_TEMPERATURE = 1.0
    }

    /**
     * Initialize LiteRT-LM engine.
     * Should be called on a background thread.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "===========================================")
        Log.i(TAG, "Initializing LiteRT-LM Service")
        Log.i(TAG, "===========================================")

        try {
            val modelPath = findModelFile()
            if (modelPath == null) {
                val preferredPath = context.getExternalFilesDir(MODEL_DIR_NAME)?.absolutePath ?: "N/A"
                Log.w(TAG, "===========================================")
                Log.w(TAG, "Gemma 3n E2B model NOT FOUND on device storage!")
                Log.w(TAG, "===========================================")
                Log.w(TAG, "Please place the model file in app storage:")
                Log.w(TAG, "  Preferred location: $preferredPath")
                Log.w(TAG, "  Supported formats: .litertlm (recommended), .task, .bin, .tflite")
                Log.w(TAG, "  Recommended: gemma-3n-e2b-it-int4.litertlm")
                Log.w(TAG, "")
                Log.w(TAG, "Download from:")
                Log.w(TAG, "  Hugging Face: https://huggingface.co/google/gemma-3n-E2B-it-litert-lm")
                Log.w(TAG, "  Kaggle: https://www.kaggle.com/models/google/gemma-3n")
                Log.w(TAG, "")
                Log.w(TAG, "After downloading:")
                Log.w(TAG, "  1. Download the .litertlm or .task file")
                Log.w(TAG, "  2. Use 'adb push' to copy to: $preferredPath")
                Log.w(TAG, "     Example: adb push gemma-3n-e2b-it-int4.litertlm $preferredPath/")
                Log.w(TAG, "  3. Relaunch the app")
                Log.w(TAG, "===========================================")
                return@withContext false
            }

            Log.i(TAG, "Found model: ${modelPath.name}")
            Log.i(TAG, "Model path: ${modelPath.absolutePath}")
            Log.i(TAG, "Model size: ${modelPath.length() / 1024 / 1024} MB")

            // Try GPU first, fallback to CPU if it fails
            Log.i(TAG, "Configuring LiteRT-LM Engine...")

            for (backend in listOf(Backend.GPU, Backend.CPU)) {
                try {
                    val backendName = if (backend == Backend.GPU) "GPU" else "CPU"
                    Log.i(TAG, "Attempting initialization with $backendName backend...")

                    val engineConfig = EngineConfig(
                        modelPath = modelPath.absolutePath,
                        backend = backend,
                        maxNumTokens = DEFAULT_MAX_TOKEN,
                        cacheDir = context.cacheDir.absolutePath
                    )

                    Log.i(TAG, "Creating and initializing engine...")
                    val initStartTime = System.currentTimeMillis()

                    engine = Engine(engineConfig)
                    engine?.initialize()

                    val initDuration = System.currentTimeMillis() - initStartTime
                    isInitialized = true
                    currentBackend = backend  // Track successful backend

                    Log.i(TAG, "✓ LiteRT-LM Engine initialized successfully with $backendName")
                    Log.i(TAG, "  Initialization time: ${initDuration / 1000.0}s")
                    Log.i(TAG, "===========================================")

                    return@withContext true

                } catch (e: Exception) {
                    val backendName = if (backend == Backend.GPU) "GPU" else "CPU"
                    Log.w(TAG, "$backendName backend failed: ${e.message}")

                    if (backend == Backend.CPU) {
                        // If CPU also fails, this is a real error
                        Log.e(TAG, "Failed to initialize with both GPU and CPU backends")
                        Log.e(TAG, "Error: ${e.message}", e)
                        Log.e(TAG, "===========================================")
                        return@withContext false
                    }
                    // Otherwise, continue to try CPU
                }
            }

            return@withContext false

        } catch (e: Exception) {
            Log.e(TAG, "ERROR initializing LiteRT-LM: ${e.message}", e)
            Log.e(TAG, "===========================================")
            isInitialized = false
            return@withContext false
        }
    }

    /**
     * Extract structured item information from transcription.
     * Returns JSON-based extraction response.
     */
    suspend fun extractItemsFromTranscription(transcription: String): ExtractionResponse {
        return withContext(Dispatchers.IO) {
            Log.i(TAG, "===========================================")
            Log.i(TAG, "Extracting items from transcription")
            Log.i(TAG, "===========================================")
            Log.i(TAG, "Transcription: \"$transcription\"")
            Log.i(TAG, "LLM initialized: $isInitialized")

            if (!isInitialized || engine == null) {
                Log.w(TAG, "LLM not initialized, attempting initialization...")
                val success = initialize()
                if (!success) {
                    Log.e(TAG, "Failed to initialize LLM, returning empty response")
                    return@withContext ExtractionResponse(emptyList())
                }
            }

            // Try extraction, with automatic CPU fallback on OpenCL error
            var retryWithCpu = false

            try {
                return@withContext performExtraction(transcription)
            } catch (e: Exception) {
                Log.e(TAG, "ERROR during extraction: ${e.message}", e)
                Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")

                // Check if this is OpenCL error and we're on GPU backend
                val isOpenClError = e.message?.contains("OpenCL", ignoreCase = true) == true

                if (isOpenClError && currentBackend == Backend.GPU) {
                    Log.w(TAG, "===========================================")
                    Log.w(TAG, "OpenCL error detected with GPU backend")
                    Log.w(TAG, "Attempting to reinitialize with CPU backend...")
                    Log.w(TAG, "===========================================")

                    try {
                        // Release GPU engine
                        engine?.close()
                        engine = null
                        isInitialized = false

                        // Reinitialize with CPU backend
                        val modelPath = findModelFile()
                        if (modelPath != null) {
                            val engineConfig = EngineConfig(
                                modelPath = modelPath.absolutePath,
                                backend = Backend.CPU,
                                maxNumTokens = DEFAULT_MAX_TOKEN,
                                cacheDir = context.cacheDir.absolutePath
                            )

                            Log.i(TAG, "Reinitializing with CPU backend...")
                            engine = Engine(engineConfig)
                            engine?.initialize()
                            isInitialized = true
                            currentBackend = Backend.CPU

                            Log.i(TAG, "✓ Successfully reinitialized with CPU backend")
                            Log.i(TAG, "Retrying extraction with CPU backend...")

                            // Retry the extraction with CPU (no recursion, inline retry)
                            retryWithCpu = true
                        }
                    } catch (reinitError: Exception) {
                        Log.e(TAG, "Failed to reinitialize with CPU: ${reinitError.message}")
                        isInitialized = false
                    }
                }

                if (!retryWithCpu) {
                    Log.e(TAG, "===========================================")
                    return@withContext ExtractionResponse(emptyList())
                }
            }

            // Retry with CPU if fallback was successful
            if (retryWithCpu) {
                try {
                    return@withContext performExtraction(transcription)
                } catch (e: Exception) {
                    Log.e(TAG, "ERROR during CPU retry: ${e.message}", e)
                    Log.e(TAG, "===========================================")
                    return@withContext ExtractionResponse(emptyList())
                }
            }

            return@withContext ExtractionResponse(emptyList())
        }
    }

    /**
     * Perform the actual extraction logic.
     * Separated to avoid recursion issues.
     */
    private suspend fun performExtraction(transcription: String): ExtractionResponse {
        val prompt = buildExtractionPrompt(transcription)
        Log.i(TAG, "Generated prompt (${prompt.length} chars)")
        Log.d(TAG, "Full prompt:\n$prompt")

        Log.i(TAG, "Creating conversation...")

        val conversationConfig = ConversationConfig(
            systemMessage = Message.of("You are a JSON extraction assistant. Extract object and location information from text. Respond ONLY with valid JSON."),
            samplerConfig = SamplerConfig(
                topK = DEFAULT_TOPK,
                topP = DEFAULT_TOPP,
                temperature = DEFAULT_TEMPERATURE
            )
        )

        engine!!.createConversation(conversationConfig).use { conversation ->
            Log.i(TAG, "Conversation created successfully")
            Log.i(TAG, "Calling LiteRT-LM inference...")
            val startTime = System.currentTimeMillis()

            val userMessage = Message.of(prompt)

            // Collect streaming response into a single string
            val response = conversation.sendMessageAsync(userMessage)
                .catch { e ->
                    Log.e(TAG, "Error during inference: ${e.message}", e)
                    throw e
                }
                .fold(StringBuilder()) { acc, message ->
                    acc.append(message.toString())
                    acc
                }
                .toString()

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            Log.i(TAG, "-------------------------------------------")
            Log.i(TAG, "LLM Response received:")
            Log.i(TAG, "  Duration: $duration ms")
            Log.i(TAG, "  Response length: ${response.length} chars")
            Log.d(TAG, "  Raw response:\n$response")

            // Parse JSON response
            val extractionResponse = parseJsonResponse(response)

            Log.i(TAG, "Extracted ${extractionResponse.items.size} item(s)")
            extractionResponse.items.forEachIndexed { index, item ->
                Log.i(TAG, "  Item ${index + 1}: ${item.objectName} -> ${item.location} (confidence: ${item.confidence})")
            }
            Log.i(TAG, "===========================================")

            return extractionResponse
        }
    }

    /**
     * Build the prompt for LLM extraction.
     * Uses structured prompt engineering for consistent JSON output.
     */
    private fun buildExtractionPrompt(transcription: String): String {
        return """
Extract object and location information from the following text.

IMPORTANT: Respond ONLY with valid JSON. Do not include any explanatory text before or after the JSON.

Output format (strict JSON):
{
  "items": [
    {
      "object": "string",
      "location": "string",
      "nearby": "string or null",
      "time_hint": "string or null",
      "confidence": 0.0-1.0,
      "evidence": "string"
    }
  ]
}

Extraction Rules:
1. "object": The item being stored (e.g., "keys", "passport", "wallet")
2. "location": Where the item is stored (e.g., "kitchen drawer", "bedroom closet", "top shelf")
3. "nearby": Optional. Other objects or landmarks near the item (e.g., "next to the stapler", "beside the lamp")
4. "time_hint": Optional. Temporal information (e.g., "yesterday", "last night", "this morning")
5. "confidence": Float 0.0-1.0 based on clarity of information. High confidence (0.8-1.0) for explicit statements, medium (0.5-0.7) for implied, low (0.0-0.4) for unclear.
6. "evidence": The exact sentence or phrase from the text that supports this extraction

Examples:

Input: "I put my keys in the kitchen drawer next to the spoons yesterday"
Output:
{
  "items": [
    {
      "object": "keys",
      "location": "kitchen drawer",
      "nearby": "next to the spoons",
      "time_hint": "yesterday",
      "confidence": 0.95,
      "evidence": "I put my keys in the kitchen drawer next to the spoons yesterday"
    }
  ]
}

Input: "The passport is in the study desk and my wallet is on the table"
Output:
{
  "items": [
    {
      "object": "passport",
      "location": "study desk",
      "nearby": null,
      "time_hint": null,
      "confidence": 0.9,
      "evidence": "The passport is in the study desk"
    },
    {
      "object": "wallet",
      "location": "table",
      "nearby": null,
      "time_hint": null,
      "confidence": 0.85,
      "evidence": "my wallet is on the table"
    }
  ]
}

Now extract from this text:
"$transcription"

JSON response:
""".trimIndent()
    }

    /**
     * Parse JSON response from LLM, handling various formats and errors.
     */
    private fun parseJsonResponse(response: String): ExtractionResponse {
        try {
            // Clean the response - remove markdown code blocks if present
            var cleanedResponse = response.trim()

            // Remove ```json and ``` markers if present
            if (cleanedResponse.startsWith("```json")) {
                cleanedResponse = cleanedResponse.removePrefix("```json").removeSuffix("```").trim()
            } else if (cleanedResponse.startsWith("```")) {
                cleanedResponse = cleanedResponse.removePrefix("```").removeSuffix("```").trim()
            }

            // Extract JSON object if there's extra text
            val jsonStart = cleanedResponse.indexOf('{')
            val jsonEnd = cleanedResponse.lastIndexOf('}')

            if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                cleanedResponse = cleanedResponse.substring(jsonStart, jsonEnd + 1)
            }

            Log.d(TAG, "Cleaned JSON for parsing:\n$cleanedResponse")

            // Parse JSON
            val extraction = gson.fromJson(cleanedResponse, ExtractionResponse::class.java)

            // Validate and sanitize
            if (extraction?.items == null) {
                Log.w(TAG, "Parsed JSON has null items list")
                return ExtractionResponse(emptyList())
            }

            // Filter out invalid items (missing required fields)
            val validItems = extraction.items.filter { item ->
                item.objectName.isNotBlank() && item.location.isNotBlank()
            }

            if (validItems.size != extraction.items.size) {
                Log.w(TAG, "Filtered out ${extraction.items.size - validItems.size} invalid item(s)")
            }

            return ExtractionResponse(validItems)

        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "JSON parsing error: ${e.message}")
            Log.e(TAG, "Failed to parse response: $response")
            return ExtractionResponse(emptyList())
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error parsing JSON: ${e.message}", e)
            return ExtractionResponse(emptyList())
        }
    }

    private fun findModelFile(): File? {
        val candidateDirs = listOfNotNull(
            context.getExternalFilesDir(MODEL_DIR_NAME),  // Check external files/models/ first
            context.getExternalFilesDir(null),  // Then external files/
            context.filesDir  // Finally internal storage
        ).distinct()

        // First, check for and fix any .fixed files from previous bug
        candidateDirs.forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".task.fixed", ignoreCase = true) ||
                    file.name.endsWith(".bin.fixed", ignoreCase = true) ||
                    file.name.endsWith(".tflite.fixed", ignoreCase = true) ||
                    file.name.endsWith(".litertlm.fixed", ignoreCase = true)) {

                    val correctName = file.name.removeSuffix(".fixed")
                    val correctFile = File(file.parentFile, correctName)

                    Log.w(TAG, "Found orphaned .fixed file: ${file.name}")
                    Log.w(TAG, "Renaming to: $correctName")

                    try {
                        val renameSuccess = file.renameTo(correctFile)
                        if (!renameSuccess) {
                            // Fallback to copy if rename fails
                            file.copyTo(correctFile, overwrite = true)
                            file.delete()
                        }
                        Log.i(TAG, "✓ Successfully renamed .fixed file")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to rename .fixed file: ${e.message}")
                    }
                }
            }
        }

        val namedMatch = candidateDirs
            .asSequence()
            .flatMap { dir -> MODEL_NAMES.asSequence().map { File(dir, it) } }
            .firstOrNull { it.exists() && it.isFile }

        if (namedMatch != null) {
            return namedMatch
        }

        return candidateDirs
            .asSequence()
            .flatMap { dir -> dir.listFiles()?.asSequence() ?: emptySequence() }
            .firstOrNull { file ->
                file.isFile &&
                MODEL_EXTENSIONS.any { file.name.endsWith(it, ignoreCase = true) } &&
                !file.name.startsWith("ggml-", ignoreCase = true) // Exclude whisper models
            }
    }

    /**
     * Release LLM resources when done.
     */
    fun release() {
        try {
            engine?.close()
            engine = null
            isInitialized = false
            currentBackend = Backend.GPU  // Reset to default
            Log.i(TAG, "LiteRT-LM resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing LLM: ${e.message}", e)
        }
    }
}
