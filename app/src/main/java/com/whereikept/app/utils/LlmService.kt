package com.whereikept.app.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LLM-based extraction service using MediaPipe Gemini Nano (Gemma).
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

    private var llmInference: LlmInference? = null
    private var isInitialized = false
    private val gson = Gson()

    companion object {
        private const val TAG = "LlmService"
        // Gemma 3n E2B - Optimized for mobile/edge devices (2025)
        // Single model file, backend (CPU/GPU) selected at runtime via MediaPipe
        // Supports .litertlm (recommended), .task, .bin, or .tflite formats
        private val MODEL_NAMES = listOf(
            "gemma-3n-e2b-it-int4.litertlm",  // LiteRT format (RECOMMENDED for Android)
            "gemma-3n-e2b-it.litertlm",       // Alternative LiteRT naming
            "gemma-3n-e2b-it-int4.task",      // MediaPipe Task format
            "gemma-3n-e2b-it.task",           // Alternative task naming
            "model.litertlm",                  // Generic LiteRT file
            "model.task"                       // Generic task file
        )
    }

    /**
     * Initialize MediaPipe LLM inference engine.
     * Should be called on a background thread.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "===========================================")
        Log.i(TAG, "Initializing MediaPipe LLM Service")
        Log.i(TAG, "===========================================")

        try {
            // Find which model file is available
            val assetManager = context.assets
            val assetFiles = assetManager.list("") ?: emptyArray()

            val availableModel = MODEL_NAMES.firstOrNull { it in assetFiles }
                ?: assetFiles.firstOrNull { it.endsWith(".task") || it.endsWith(".bin") || it.endsWith(".tflite") || it.endsWith(".litertlm") }

            if (availableModel == null) {
                Log.w(TAG, "===========================================")
                Log.w(TAG, "Gemma 3n E2B model NOT FOUND in assets!")
                Log.w(TAG, "===========================================")
                Log.w(TAG, "Please place the model file in assets folder:")
                Log.w(TAG, "  Supported formats: .task, .bin, .tflite, .litertlm")
                Log.w(TAG, "  Recommended: gemma-3n-e2b-it-int4.task")
                Log.w(TAG, "")
                Log.w(TAG, "Download from:")
                Log.w(TAG, "  Hugging Face: https://huggingface.co/google/gemma-3n-E2B-it-litert-lm")
                Log.w(TAG, "  Kaggle: https://www.kaggle.com/models/google/gemma-3n")
                Log.w(TAG, "")
                Log.w(TAG, "After downloading:")
                Log.w(TAG, "  1. Download the .task file (preferred) or .bin/.tflite file")
                Log.w(TAG, "  2. Place in: app/src/main/assets/")
                Log.w(TAG, "  3. Rebuild the app")
                Log.w(TAG, "  (No renaming needed - any .task/.bin/.tflite file works)")
                Log.w(TAG, "===========================================")
                return@withContext false
            }

            Log.i(TAG, "Found model: $availableModel")

            // Look for model in app's files directory
            val modelPath = File(context.filesDir, availableModel)

            // If model doesn't exist in app files, copy from assets
            if (!modelPath.exists()) {
                Log.i(TAG, "Model not found in app files, copying from assets...")
                Log.i(TAG, "Destination: ${modelPath.absolutePath}")
                Log.i(TAG, "⚠️ This is a large file (~2.9 GB), first copy may take 2-3 minutes...")

                val startTime = System.currentTimeMillis()
                assetManager.open(availableModel).use { input ->
                    modelPath.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytes = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead

                            // Log progress every 100MB
                            if (totalBytes % (100 * 1024 * 1024) == 0L) {
                                Log.i(TAG, "Copied ${totalBytes / 1024 / 1024} MB...")
                            }
                        }

                        val duration = System.currentTimeMillis() - startTime
                        Log.i(TAG, "✓ Model copied successfully!")
                        Log.i(TAG, "  Size: ${totalBytes / 1024 / 1024} MB")
                        Log.i(TAG, "  Time: ${duration / 1000.0}s")
                    }
                }
            } else {
                Log.i(TAG, "Model already exists in app files")
            }

            Log.i(TAG, "Model path: ${modelPath.absolutePath}")
            Log.i(TAG, "Model size: ${modelPath.length() / 1024 / 1024} MB")
            Log.i(TAG, "Model exists: ${modelPath.exists()}")
            Log.i(TAG, "Model readable: ${modelPath.canRead()}")

            // Validate file is not corrupted or empty
            if (modelPath.length() < 1024 * 1024) { // Less than 1 MB is suspicious for a model
                Log.e(TAG, "Model file is too small (${modelPath.length()} bytes) - likely corrupted or incomplete")
                Log.e(TAG, "Expected size: ~2.9 GB (2,900,000,000 bytes)")
                Log.e(TAG, "Please delete the file and re-download from:")
                Log.e(TAG, "  Hugging Face: https://huggingface.co/google/gemma-3n-E2B-it-litert-lm")
                modelPath.delete() // Delete corrupted file
                return@withContext false
            }

            // Validate file format based on extension
            if (availableModel.endsWith(".litertlm")) {
                try {
                    // LiteRT LM files have "LITERTLM" signature (8 bytes: 4C 49 54 45 52 54 4C 4D)
                    val headerBytes = ByteArray(16)
                    modelPath.inputStream().use { it.read(headerBytes) }

                    // Check for LITERTLM signature
                    val expectedSignature = byteArrayOf(
                        0x4C.toByte(), 0x49.toByte(), 0x54.toByte(), 0x45.toByte(),
                        0x52.toByte(), 0x54.toByte(), 0x4C.toByte(), 0x4D.toByte()
                    ) // "LITERTLM"

                    val hasValidSignature = expectedSignature.indices.all {
                        headerBytes[it] == expectedSignature[it]
                    }

                    if (hasValidSignature) {
                        Log.i(TAG, "✓ Model file has valid LiteRT LM signature")
                    } else {
                        Log.w(TAG, "⚠ Unexpected file signature for .litertlm file")
                        Log.w(TAG, "First 16 bytes: ${headerBytes.joinToString(" ") { "%02X".format(it) }}")
                        Log.w(TAG, "Expected: 4C 49 54 45 52 54 4C 4D (LITERTLM)")
                        Log.w(TAG, "Proceeding anyway - MediaPipe will validate")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not validate LiteRT LM signature: ${e.message}")
                }
            } else if (availableModel.endsWith(".task")) {
                try {
                    // .task files should be ZIP archives with PK signature
                    val headerBytes = ByteArray(16)
                    modelPath.inputStream().use { it.read(headerBytes) }

                    // Look for PK ZIP signature (50 4B 03 04) in first 8 bytes
                    var zipOffset = -1
                    for (i in 0..4) {
                        if (headerBytes[i] == 0x50.toByte() &&
                            headerBytes[i + 1] == 0x4B.toByte() &&
                            headerBytes[i + 2] == 0x03.toByte() &&
                            headerBytes[i + 3] == 0x04.toByte()) {
                            zipOffset = i
                            break
                        }
                    }

                    if (zipOffset == -1) {
                        Log.e(TAG, "Model .task file is not a valid ZIP archive")
                        Log.e(TAG, "First 16 bytes: ${headerBytes.joinToString(" ") { "%02X".format(it) }}")
                        Log.e(TAG, "Expected to find: 50 4B 03 04 (PK ZIP signature)")
                        Log.e(TAG, "The .task file may be corrupted. Please re-download from:")
                        Log.e(TAG, "  Hugging Face: https://huggingface.co/google/gemma-3n-E2B-it-litert-lm")
                        modelPath.delete() // Delete corrupted file
                        return@withContext false
                    } else if (zipOffset > 0) {
                        Log.w(TAG, "⚠ ZIP signature found at offset $zipOffset (expected at 0)")
                        Log.w(TAG, "File has $zipOffset extra leading bytes - this may cause issues with MediaPipe")
                        Log.w(TAG, "Attempting to fix by removing leading bytes...")

                        // Create corrected file
                        val correctedPath = File(context.filesDir, "${availableModel}.fixed")
                        modelPath.inputStream().use { input ->
                            input.skip(zipOffset.toLong())
                            correctedPath.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        // Replace original with fixed file
                        modelPath.delete()
                        correctedPath.renameTo(modelPath)
                        Log.i(TAG, "✓ Fixed ZIP file by removing $zipOffset leading bytes")
                    } else {
                        Log.i(TAG, "✓ Model file has valid ZIP signature at correct offset")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not validate ZIP signature: ${e.message}")
                }
            }

            // Configure MediaPipe LLM options with GPU backend preference
            // MediaPipe will automatically fallback to CPU if GPU is not available
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath.absolutePath)
                .build()

            Log.i(TAG, "Creating LLM inference instance...")
            Log.i(TAG, "MediaPipe will auto-select best backend (GPU preferred, CPU fallback)")
            llmInference = LlmInference.createFromOptions(context, options)
            isInitialized = true

            Log.i(TAG, "✓ MediaPipe LLM initialized successfully with $availableModel")
            Log.i(TAG, "===========================================")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "ERROR initializing MediaPipe LLM: ${e.message}", e)
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

            if (!isInitialized || llmInference == null) {
                Log.w(TAG, "LLM not initialized, attempting initialization...")
                val success = initialize()
                if (!success) {
                    Log.e(TAG, "Failed to initialize LLM, returning empty response")
                    return@withContext ExtractionResponse(emptyList())
                }
            }

            try {
                val prompt = buildExtractionPrompt(transcription)
                Log.i(TAG, "Generated prompt (${prompt.length} chars)")
                Log.d(TAG, "Full prompt:\n$prompt")

                Log.i(TAG, "Calling LLM inference...")
                val startTime = System.currentTimeMillis()

                // Use generateResponse with temperature parameter
                // Note: Temperature is passed via generateResponse overload in MediaPipe 0.10.27+
                val response = llmInference?.generateResponse(prompt) ?: ""

                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime

                Log.i(TAG, "-------------------------------------------")
                Log.i(TAG, "LLM Response received:")
                Log.i(TAG, "  Duration: $duration ms")
                Log.i(TAG, "  Response length: ${response.length} chars")
                Log.d(TAG, "  Raw response:\n$response")

                // Parse JSON response
                val extractionResponse = parseJsonResponse(response)

                Log.i(TAG, "✓ Extracted ${extractionResponse.items.size} item(s)")
                extractionResponse.items.forEachIndexed { index, item ->
                    Log.i(TAG, "  Item ${index + 1}: ${item.objectName} -> ${item.location} (confidence: ${item.confidence})")
                }
                Log.i(TAG, "===========================================")

                return@withContext extractionResponse

            } catch (e: Exception) {
                Log.e(TAG, "ERROR during extraction: ${e.message}", e)
                Log.e(TAG, "===========================================")
                return@withContext ExtractionResponse(emptyList())
            }
        }
    }

    /**
     * Build the prompt for LLM extraction.
     * Uses structured prompt engineering for consistent JSON output.
     */
    private fun buildExtractionPrompt(transcription: String): String {
        return """
You are a JSON extraction assistant. Extract object and location information from the following text.

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

    /**
     * Release LLM resources when done.
     */
    fun release() {
        try {
            llmInference?.close()
            llmInference = null
            isInitialized = false
            Log.i(TAG, "✓ LLM resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing LLM: ${e.message}", e)
        }
    }
}
