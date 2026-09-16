package com.example.data.repository

import com.example.data.dao.RecordingDao
import com.example.data.model.RecordingEntity
import kotlinx.coroutines.flow.Flow

class RecordingRepository(private val dao: RecordingDao) {

    val allRecordings: Flow<List<RecordingEntity>> = dao.getAllRecordings()

    fun getRecordingById(id: Long): Flow<RecordingEntity?> = dao.getRecordingById(id)

    fun searchRecordings(query: String): Flow<List<RecordingEntity>> = dao.searchRecordings(query)

    fun getRecordingsByTag(tag: String): Flow<List<RecordingEntity>> = dao.getRecordingsByTag(tag)

    fun getFavoriteRecordings(): Flow<List<RecordingEntity>> = dao.getFavoriteRecordings()

    suspend fun insertRecording(recording: RecordingEntity): Long = dao.insert(recording)

    suspend fun updateRecording(recording: RecordingEntity) = dao.update(recording)

    suspend fun deleteRecording(id: Long) = dao.deleteById(id)

    suspend fun toggleFavorite(recording: RecordingEntity) {
        dao.update(recording.copy(isFavorite = !recording.isFavorite))
    }
}
