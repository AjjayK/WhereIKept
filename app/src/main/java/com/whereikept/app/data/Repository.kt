package com.whereikept.app.data

import kotlinx.coroutines.flow.Flow

class WhereIKeptRepository(
    private val itemDao: ItemDao,
    private val recordingDao: RecordingDao,
    private val imageDao: ImageDao
) {
    // Items
    val allItems: Flow<List<ItemEntity>> = itemDao.getAllItems()
    
    suspend fun insertItem(item: ItemEntity): Long = itemDao.insertItem(item)
    
    suspend fun insertItems(items: List<ItemEntity>) = itemDao.insertItems(items)
    
    suspend fun updateItem(item: ItemEntity) = itemDao.updateItem(item)
    
    suspend fun deleteItem(item: ItemEntity) = itemDao.deleteItem(item)
    
    suspend fun deleteItemById(id: Long) = itemDao.deleteItemById(id)
    
    suspend fun getItemById(id: Long): ItemEntity? = itemDao.getItemById(id)
    
    suspend fun searchItems(query: String): List<ItemEntity> {
        // Try FTS search first, fall back to simple search
        return try {
            val ftsQuery = query.split(" ").joinToString(" OR ") { "$it*" }
            itemDao.searchItems(ftsQuery).ifEmpty {
                itemDao.searchItemsSimple(query)
            }
        } catch (e: Exception) {
            itemDao.searchItemsSimple(query)
        }
    }
    
    suspend fun getItemsByObject(objectName: String): List<ItemEntity> = 
        itemDao.getItemsByObject(objectName)
    
    suspend fun getItemCount(): Int = itemDao.getItemCount()
    
    suspend fun deleteAllItems() = itemDao.deleteAllItems()
    
    // Recordings
    val allRecordings: Flow<List<RecordingEntity>> = recordingDao.getAllRecordings()
    
    suspend fun insertRecording(recording: RecordingEntity): Long = 
        recordingDao.insertRecording(recording)
    
    suspend fun updateRecording(recording: RecordingEntity) = 
        recordingDao.updateRecording(recording)
    
    suspend fun deleteRecording(recording: RecordingEntity) = 
        recordingDao.deleteRecording(recording)
    
    suspend fun getUnprocessedRecordings(): List<RecordingEntity> = 
        recordingDao.getUnprocessedRecordings()
    
    suspend fun getRecordingById(id: Long): RecordingEntity? = 
        recordingDao.getRecordingById(id)
    
    // Images
    val allImages: Flow<List<ImageEntity>> = imageDao.getAllImages()
    
    suspend fun insertImage(image: ImageEntity): Long = imageDao.insertImage(image)
    
    suspend fun updateImage(image: ImageEntity) = imageDao.updateImage(image)
    
    suspend fun deleteImage(image: ImageEntity) = imageDao.deleteImage(image)
    
    suspend fun getUnprocessedImages(): List<ImageEntity> = imageDao.getUnprocessedImages()
    
    suspend fun getImageById(id: Long): ImageEntity? = imageDao.getImageById(id)
}
