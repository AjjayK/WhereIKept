package com.whereikept.app.data

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Represents an item with its location stored by the user.
 * For example: "keys" -> "kitchen drawer"
 */
@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val objectName: String,           // What the user stored (e.g., "keys", "passport")
    val location: String,             // Where it was stored (e.g., "kitchen drawer", "bedroom closet")
    val description: String = "",     // Additional context
    val nearby: String? = null,       // Nearby objects (e.g., "next to the stapler")
    val timeHint: String? = null,     // Temporal information (e.g., "yesterday night", "last week")
    val confidence: Float? = null,    // LLM extraction confidence (0.0 to 1.0)
    val evidence: String? = null,     // Raw sentence that led to extraction
    val imagePath: String? = null,    // Path to associated image if any
    val timestamp: Long = System.currentTimeMillis(),
    val sourceType: String = "voice"  // "voice" or "image" or "manual"
)

/**
 * FTS4 virtual table for full-text search on items.
 * This enables fast searching through object names, locations, and context.
 */
@Entity
@Fts4(contentEntity = ItemEntity::class)
data class ItemFts(
    val objectName: String,
    val location: String,
    val description: String,
    val nearby: String,
    val timeHint: String,
    val evidence: String
)

/**
 * Represents a voice recording session
 */
@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transcription: String,        // Full transcription of the recording
    val processedText: String = "",   // LLM processed text
    val filePath: String? = null,     // Path to audio file if saved
    val timestamp: Long = System.currentTimeMillis(),
    val isProcessed: Boolean = false
)

/**
 * Represents an image capture session
 */
@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val imagePath: String,
    val summary: String = "",         // LLM generated summary of objects in image
    val timestamp: Long = System.currentTimeMillis(),
    val isProcessed: Boolean = false
)
