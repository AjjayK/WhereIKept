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
    val objectAttribute: String? = null,  // Object attributes (e.g., "red color", "large size", "metal")
    val locationParent: String? = null,   // High-level location (e.g., "home", "office", "farm")
    val imagePath: String? = null,    // Path to original captured image
    val taggedImagePath: String? = null,  // Path to image with overlaid tags (screenshot from review)
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
    val objectAttribute: String,
    val locationParent: String
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
