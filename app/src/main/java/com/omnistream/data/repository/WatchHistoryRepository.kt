package com.omnistream.data.repository

import com.omnistream.data.local.WatchHistoryDao
import com.omnistream.data.local.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class WatchHistoryRepository @Inject constructor(
    private val watchHistoryDao: WatchHistoryDao
) {
    fun getContinueWatching(): Flow<List<WatchHistoryEntity>> =
        watchHistoryDao.getContinueWatching()

    fun getContinueReading(): Flow<List<WatchHistoryEntity>> =
        watchHistoryDao.getContinueReading()

    suspend fun getProgress(contentId: String, sourceId: String): WatchHistoryEntity? =
        watchHistoryDao.getProgress(contentId, sourceId)

    suspend fun upsert(entity: WatchHistoryEntity) =
        watchHistoryDao.upsert(entity)

    suspend fun delete(id: String) =
        watchHistoryDao.delete(id)
}
