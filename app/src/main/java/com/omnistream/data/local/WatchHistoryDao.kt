package com.omnistream.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    @Query("SELECT * FROM watch_history WHERE contentType = 'video' AND is_completed = 0 ORDER BY last_watched_at DESC LIMIT :limit")
    fun getContinueWatching(limit: Int = 10): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE contentType = 'manga' AND is_completed = 0 ORDER BY last_watched_at DESC LIMIT :limit")
    fun getContinueReading(limit: Int = 10): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE contentId = :contentId AND sourceId = :sourceId")
    suspend fun getProgress(contentId: String, sourceId: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}
