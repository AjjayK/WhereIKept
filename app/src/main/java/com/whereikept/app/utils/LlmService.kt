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
        private const val MODEL_NAME = "gemma-2b-it-gpu-int4.bin"  // Adjust based on your model
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
            // Look for model in app's files directory
            val modelPath = File(context.filesDir, MODEL_NAME)

            // If model doesn't exist in app files, copy from assets
            if (!modelPath.exists()) {
                Log.i(TAG, "Model not found in app files, checking assets...")

                try {
                    // Check if model exists in assets
                    val assetManager = context.assets
                    val assetFiles = assetManager.list("") ?: emptyArray()

                    if (MODEL_NAME in assetFiles) {
                        Log.i(TAG, "Found model in assets, copying to app files...")
                        Log.i(TAG, "Destination: ${modelPath.absolutePath}")
                        Log.i(TAG, "⚠️ This is a large file (~1.5 GB), first copy may take 1-2 minutes...")

                        val startTime = System.currentTimeMillis()
                        assetManager.open(MODEL_NAME).use { input ->
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
                        Log.w(TAG, "===========================================")
                        Log.w(TAG, "Gemma model NOT FOUND in assets!")
                        Log.w(TAG, "===========================================")
                        Log.w(TAG, "Please place the model file in assets folder:")
                        Log.w(TAG, "  Path: app/src/main/assets/$MODEL_NAME")
                        Log.w(TAG, "")
                        Log.w(TAG, "Download from:")
                        Log.w(TAG, "  https://www.kaggle.com/models/google/gemma/tfLite/gemma-2b-it-gpu-int4")
                        Log.w(TAG, "")
                        Log.w(TAG, "After downloading:")
                        Log.w(TAG, "  1. Extract the .bin file")
                        Log.w(TAG, "  2. Rename to: $MODEL_NAME")
                        Log.w(TAG, "  3. Place in: app/src/main/assets/")
                        Log.w(TAG, "  4. Rebuild the app")
                        Log.w(TAG, "===========================================")
                        return@withContext false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "ERROR copying model from assets: ${e.message}", e)
                    return@withContext false
                }
            } else {
                Log.i(TAG, "Model already exists in app files")
            }

            Log.i(TAG, "Model path: ${modelPath.absolutePath}")
            Log.i(TAG, "Model size: ${modelPath.length() / 1024 / 1024} MB")

            // Configure MediaPipe LLM options
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath.absolutePath)
                .setTemperature(0.3f)  // Lower temperature for more consistent JSON
                .setRandomSeed(0)
                .build()

            Log.i(TAG, "Creating LLM inference instance...")
            llmInference = LlmInference.createFromOptions(context, options)
            isInitialized = true

            Log.i(TAG, "✓ MediaPipe LLM initialized successfully")
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
