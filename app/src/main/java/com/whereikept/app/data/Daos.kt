package com.whereikept.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ItemEntity>)
    
    @Update
    suspend fun updateItem(item: ItemEntity)
    
    @Delete
    suspend fun deleteItem(item: ItemEntity)
    
    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteItemById(id: Long)
    
    @Query("SELECT * FROM items ORDER BY timestamp DESC")
    fun getAllItems(): Flow<List<ItemEntity>>
    
    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getItemById(id: Long): ItemEntity?
    
    /**
     * Full-text search for items matching the query.
     * Searches through objectName, location, and description.
     */
    @Query("""
        SELECT items.* FROM items
        JOIN ItemFts ON items.rowid = ItemFts.rowid
        WHERE ItemFts MATCH :query
        ORDER BY items.timestamp DESC
    """)
    suspend fun searchItems(query: String): List<ItemEntity>
    
    /**
     * Simple LIKE search as fallback
     */
    @Query("""
        SELECT * FROM items 
        WHERE objectName LIKE '%' || :query || '%' 
        OR location LIKE '%' || :query || '%'
        OR description LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    suspend fun searchItemsSimple(query: String): List<ItemEntity>
    
    /**
     * Get items by object name (exact or partial match)
     */
    @Query("SELECT * FROM items WHERE objectName LIKE '%' || :objectName || '%' ORDER BY timestamp DESC")
    suspend fun getItemsByObject(objectName: String): List<ItemEntity>
    
    @Query("SELECT COUNT(*) FROM items")
    suspend fun getItemCount(): Int
    
    @Query("DELETE FROM items")
    suspend fun deleteAllItems()
}

@Dao
interface RecordingDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity): Long
    
    @Update
    suspend fun updateRecording(recording: RecordingEntity)
    
    @Delete
    suspend fun deleteRecording(recording: RecordingEntity)
    
    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>
    
    @Query("SELECT * FROM recordings WHERE isProcessed = 0")
    suspend fun getUnprocessedRecordings(): List<RecordingEntity>
    
    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getRecordingById(id: Long): RecordingEntity?
}

@Dao
interface ImageDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: ImageEntity): Long
    
    @Update
    suspend fun updateImage(image: ImageEntity)
    
    @Delete
    suspend fun deleteImage(image: ImageEntity)
    
    @Query("SELECT * FROM images ORDER BY timestamp DESC")
    fun getAllImages(): Flow<List<ImageEntity>>
    
    @Query("SELECT * FROM images WHERE isProcessed = 0")
    suspend fun getUnprocessedImages(): List<ImageEntity>
    
    @Query("SELECT * FROM images WHERE id = :id")
    suspend fun getImageById(id: Long): ImageEntity?
}
