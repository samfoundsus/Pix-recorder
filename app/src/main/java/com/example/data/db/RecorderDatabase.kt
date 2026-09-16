package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.RecordingDao
import com.example.data.model.RecordingEntity

@Database(
    entities = [RecordingEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RecorderDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao

    companion object {
        @Volatile
        private var INSTANCE: RecorderDatabase? = null

        fun getDatabase(context: Context): RecorderDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RecorderDatabase::class.java,
                    "pixel_recorder.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
