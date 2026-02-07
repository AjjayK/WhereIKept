package com.whereikept.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ItemEntity::class,
        ItemFts::class,
        RecordingEntity::class,
        ImageEntity::class,
        VersionMetricEntity::class,
        DeviceMetricEntity::class,
        InferenceMetricEntity::class
    ],
    version = 5,  // Added analytics entities for LLM performance tracking
    exportSchema = false
)
abstract class WhereIKeptDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao
    abstract fun recordingDao(): RecordingDao
    abstract fun imageDao(): ImageDao
    abstract fun metricsDao(): MetricsDao
    
    companion object {
        @Volatile
        private var INSTANCE: WhereIKeptDatabase? = null
        
        fun getDatabase(context: Context): WhereIKeptDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WhereIKeptDatabase::class.java,
                    "where_i_kept_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
