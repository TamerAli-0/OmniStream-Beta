package com.omnistream.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadChaptersDao {
    /**
     * Mark a chapter as read
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markAsRead(chapter: ReadChaptersEntity)

    /**
     * Mark multiple chapters as read (batch operation)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markMultipleAsRead(chapters: List<ReadChaptersEntity>)

    /**
     * Remove read status from a chapter
     */
    @Delete
    suspend fun unmarkAsRead(chapter: ReadChaptersEntity)

    /**
     * Get all read chapters for a manga (as Flow for reactive updates)
     */
    @Query("SELECT * FROM read_chapters WHERE manga_id = :mangaId AND source_id = :sourceId ORDER BY chapter_number ASC")
    fun getReadChaptersFlow(mangaId: String, sourceId: String): Flow<List<ReadChaptersEntity>>

    /**
     * Get all read chapters for a manga (one-time query)
     */
    @Query("SELECT * FROM read_chapters WHERE manga_id = :mangaId AND source_id = :sourceId ORDER BY chapter_number ASC")
    suspend fun getReadChapters(mangaId: String, sourceId: String): List<ReadChaptersEntity>

    /**
     * Check if a specific chapter is read
     */
    @Query("SELECT EXISTS(SELECT 1 FROM read_chapters WHERE id = :chapterId LIMIT 1)")
    suspend fun isChapterRead(chapterId: String): Boolean

    /**
     * Get the highest chapter number read for a manga (for AniList sync)
     */
    @Query("SELECT MAX(chapter_number) FROM read_chapters WHERE manga_id = :mangaId AND source_id = :sourceId AND is_completed = 1")
    suspend fun getHighestReadChapter(mangaId: String, sourceId: String): Float?

    /**
     * Get total chapters read count for a manga
     */
    @Query("SELECT COUNT(*) FROM read_chapters WHERE manga_id = :mangaId AND source_id = :sourceId")
    suspend fun getReadChaptersCount(mangaId: String, sourceId: String): Int

    /**
     * Delete all read chapters for a manga (for reset/cleanup)
     */
    @Query("DELETE FROM read_chapters WHERE manga_id = :mangaId AND source_id = :sourceId")
    suspend fun clearReadChapters(mangaId: String, sourceId: String)

    /**
     * Get all read chapter IDs for quick lookup
     */
    @Query("SELECT chapter_id FROM read_chapters WHERE manga_id = :mangaId AND source_id = :sourceId")
    suspend fun getReadChapterIds(mangaId: String, sourceId: String): List<String>
}
