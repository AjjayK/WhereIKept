package com.whereikept.app.utils

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Service for interacting with LLM (Local or Cloud).
 * Supports Ollama (local) and OpenAI-compatible APIs.
 */
class LlmService(
    private var baseUrl: String = "http://localhost:11434",  // Default Ollama URL
    private var model: String = "llama3.2",
    private var apiType: ApiType = ApiType.OLLAMA
) {
    
    enum class ApiType {
        OLLAMA,
        OPENAI_COMPATIBLE
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    
    data class ExtractedItem(
        val objectName: String,
        val location: String,
        val confidence: Float = 1.0f
    )
    
    /**
     * Extract object-location pairs from transcribed text.
     */
    suspend fun extractItemsFromText(transcription: String): List<ExtractedItem> = withContext(Dispatchers.IO) {
        val prompt = """
            |You are a helpful assistant that extracts information about where objects are stored.
            |From the following text, extract any mentions of objects and their locations.
            |
            |Text: "$transcription"
            |
            |Respond ONLY with a JSON array of objects, each with "object" and "location" fields.
            |If no objects/locations are mentioned, return an empty array [].
            |
            |Examples:
            |Input: "I'm putting my keys in the kitchen drawer"
            |Output: [{"object": "keys", "location": "kitchen drawer"}]
            |
            |Input: "The passport is in the bedroom closet, top shelf"
            |Output: [{"object": "passport", "location": "bedroom closet, top shelf"}]
            |
            |Input: "Hello, how are you?"
            |Output: []
            |
            |Your response (JSON array only, no other text):
        """.trimMargin()
        
        try {
            val response = callLlm(prompt)
            parseExtractedItems(response)
        } catch (e: Exception) {
            Log.e("LlmService", "Error extracting items: ${e.message}")
            // Fallback: try simple pattern matching
            extractItemsSimple(transcription)
        }
    }
    
    /**
     * Summarize objects visible in an image description.
     */
    suspend fun summarizeImageForObjects(imageDescription: String): String = withContext(Dispatchers.IO) {
        val prompt = """
            |You are analyzing an image description to identify objects and their locations.
            |
            |Image description: "$imageDescription"
            |
            |List the objects visible and where they appear to be located.
            |Be concise and focus on items that someone might want to remember the location of.
            |
            |Response:
        """.trimMargin()
        
        try {
            callLlm(prompt)
        } catch (e: Exception) {
            Log.e("LlmService", "Error summarizing image: ${e.message}")
            "Unable to process image description"
        }
    }
    
    /**
     * Answer a query about where an object is located based on context.
     */
    suspend fun answerQuery(query: String, context: List<ExtractedItem>): String = withContext(Dispatchers.IO) {
        if (context.isEmpty()) {
            return@withContext "I don't have any records of where that item might be. Try recording when you store items!"
        }
        
        val contextStr = context.joinToString("\n") { 
            "- ${it.objectName} is in ${it.location}" 
        }
        
        val prompt = """
            |You are a helpful assistant that helps people find their belongings.
            |Based on the recorded information below, answer the user's question.
            |
            |Recorded items and locations:
            |$contextStr
            |
            |User's question: "$query"
            |
            |Provide a helpful, friendly response. If the item isn't in the records, say so politely.
            |Keep the response concise and natural.
            |
            |Response:
        """.trimMargin()
        
        try {
            callLlm(prompt)
        } catch (e: Exception) {
            Log.e("LlmService", "Error answering query: ${e.message}")
            // Fallback: simple response based on context
            val item = context.firstOrNull()
            if (item != null) {
                "Based on your records, ${item.objectName} should be in ${item.location}."
            } else {
                "I couldn't find information about that item."
            }
        }
    }
    
    private suspend fun callLlm(prompt: String): String = withContext(Dispatchers.IO) {
        val requestBody = when (apiType) {
            ApiType.OLLAMA -> {
                gson.toJson(mapOf(
                    "model" to model,
                    "prompt" to prompt,
                    "stream" to false
                ))
            }
            ApiType.OPENAI_COMPATIBLE -> {
                gson.toJson(mapOf(
                    "model" to model,
                    "messages" to listOf(
                        mapOf("role" to "user", "content" to prompt)
                    ),
                    "temperature" to 0.7
                ))
            }
        }
        
        val endpoint = when (apiType) {
            ApiType.OLLAMA -> "$baseUrl/api/generate"
            ApiType.OPENAI_COMPATIBLE -> "$baseUrl/v1/chat/completions"
        }
        
        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody.toRequestBody(jsonMediaType))
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("LLM request failed: ${response.code}")
            }
            
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            
            when (apiType) {
                ApiType.OLLAMA -> {
                    val json = JsonParser.parseString(responseBody).asJsonObject
                    json.get("response")?.asString ?: ""
                }
                ApiType.OPENAI_COMPATIBLE -> {
                    val json = JsonParser.parseString(responseBody).asJsonObject
                    json.getAsJsonArray("choices")
                        ?.get(0)?.asJsonObject
                        ?.getAsJsonObject("message")
                        ?.get("content")?.asString ?: ""
                }
            }
        }
    }
    
    private fun parseExtractedItems(response: String): List<ExtractedItem> {
        return try {
            // Try to find JSON array in response
            val jsonStart = response.indexOf('[')
            val jsonEnd = response.lastIndexOf(']')
            if (jsonStart == -1 || jsonEnd == -1) return emptyList()
            
            val jsonStr = response.substring(jsonStart, jsonEnd + 1)
            val array = JsonParser.parseString(jsonStr).asJsonArray
            
            array.mapNotNull { element ->
                val obj = element.asJsonObject
                val objectName = obj.get("object")?.asString ?: return@mapNotNull null
                val location = obj.get("location")?.asString ?: return@mapNotNull null
                ExtractedItem(objectName, location)
            }
        } catch (e: Exception) {
            Log.e("LlmService", "Error parsing JSON: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Simple pattern-based extraction as fallback when LLM is unavailable.
     */
    private fun extractItemsSimple(text: String): List<ExtractedItem> {
        val patterns = listOf(
            Regex("""(?:put|placing|stored?|keep(?:ing)?|left)\s+(?:my\s+)?(\w+(?:\s+\w+)?)\s+(?:in|on|at|inside|under)\s+(?:the\s+)?(.+?)(?:\.|,|$)""", RegexOption.IGNORE_CASE),
            Regex("""(\w+(?:\s+\w+)?)\s+(?:is|are|goes?)\s+(?:in|on|at|inside|under)\s+(?:the\s+)?(.+?)(?:\.|,|$)""", RegexOption.IGNORE_CASE)
        )
        
        val results = mutableListOf<ExtractedItem>()
        for (pattern in patterns) {
            pattern.findAll(text).forEach { match ->
                val objectName = match.groupValues.getOrNull(1)?.trim() ?: return@forEach
                val location = match.groupValues.getOrNull(2)?.trim() ?: return@forEach
                if (objectName.isNotEmpty() && location.isNotEmpty()) {
                    results.add(ExtractedItem(objectName, location))
                }
            }
        }
        return results.distinctBy { it.objectName.lowercase() }
    }
    
    fun updateSettings(baseUrl: String, model: String, apiType: ApiType) {
        this.baseUrl = baseUrl
        this.model = model
        this.apiType = apiType
    }
    
    /**
     * Test connection to the LLM service.
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = callLlm("Say 'OK' if you can hear me.")
            response.isNotEmpty()
        } catch (e: Exception) {
            Log.e("LlmService", "Connection test failed: ${e.message}")
            false
        }
    }
}
