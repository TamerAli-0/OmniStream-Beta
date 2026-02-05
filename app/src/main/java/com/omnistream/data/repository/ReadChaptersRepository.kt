package com.omnistream.data.repository

import com.omnistream.data.anilist.AniListAuthManager
import com.omnistream.data.anilist.AniListSyncManager
import com.omnistream.data.local.ReadChaptersDao
import com.omnistream.data.local.ReadChaptersEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadChaptersRepository @Inject constructor(
    private val readChaptersDao: ReadChaptersDao,
    private val aniListAuthManager: AniListAuthManager,
    private val aniListSyncManager: AniListSyncManager
) {

    /**
     * Mark a chapter as read and optionally sync with AniList
     */
    suspend fun markChapterAsRead(
        mangaId: String,
        sourceId: String,
        chapterId: String,
        chapterNumber: Float,
        pagesRead: Int = 0,
        totalPages: Int = 0,
        syncToAniList: Boolean = true
    ) {
        val entity = ReadChaptersEntity(
            id = "$sourceId:$mangaId:$chapterId",
            mangaId = mangaId,
            sourceId = sourceId,
            chapterId = chapterId,
            chapterNumber = chapterNumber,
            readAt = System.currentTimeMillis(),
            pagesRead = pagesRead,
            totalPages = totalPages,
            isCompleted = pagesRead >= totalPages || totalPages == 0
        )

        readChaptersDao.markAsRead(entity)

        // Sync to AniList if enabled and user is logged in
        if (syncToAniList && aniListAuthManager.isLoggedIn()) {
            syncMangaProgressToAniList(mangaId, sourceId)
        }
    }

    /**
     * Mark multiple chapters as read at once
     */
    suspend fun markMultipleAsRead(
        mangaId: String,
        sourceId: String,
        chapters: List<Pair<String, Float>>, // List of (chapterId, chapterNumber)
        syncToAniList: Boolean = true
    ) {
        val entities = chapters.map { (chapterId, chapterNumber) ->
            ReadChaptersEntity(
                id = "$sourceId:$mangaId:$chapterId",
                mangaId = mangaId,
                sourceId = sourceId,
                chapterId = chapterId,
                chapterNumber = chapterNumber,
                readAt = System.currentTimeMillis()
            )
        }

        readChaptersDao.markMultipleAsRead(entities)

        if (syncToAniList && aniListAuthManager.isLoggedIn()) {
            syncMangaProgressToAniList(mangaId, sourceId)
        }
    }

    /**
     * Remove read status from a chapter
     */
    suspend fun unmarkAsRead(
        mangaId: String,
        sourceId: String,
        chapterId: String,
        chapterNumber: Float
    ) {
        val entity = ReadChaptersEntity(
            id = "$sourceId:$mangaId:$chapterId",
            mangaId = mangaId,
            sourceId = sourceId,
            chapterId = chapterId,
            chapterNumber = chapterNumber
        )
        readChaptersDao.unmarkAsRead(entity)
    }

    /**
     * Get all read chapters for a manga as Flow (reactive)
     */
    fun getReadChaptersFlow(mangaId: String, sourceId: String): Flow<List<ReadChaptersEntity>> {
        return readChaptersDao.getReadChaptersFlow(mangaId, sourceId)
    }

    /**
     * Get all read chapters for a manga (one-time)
     */
    suspend fun getReadChapters(mangaId: String, sourceId: String): List<ReadChaptersEntity> {
        return readChaptersDao.getReadChapters(mangaId, sourceId)
    }

    /**
     * Check if a specific chapter is read
     */
    suspend fun isChapterRead(mangaId: String, sourceId: String, chapterId: String): Boolean {
        val id = "$sourceId:$mangaId:$chapterId"
        return readChaptersDao.isChapterRead(id)
    }

    /**
     * Get set of read chapter IDs for quick lookup
     */
    suspend fun getReadChapterIdsSet(mangaId: String, sourceId: String): Set<String> {
        return readChaptersDao.getReadChapterIds(mangaId, sourceId).toSet()
    }

    /**
     * Get the highest chapter number that's been read
     */
    suspend fun getHighestReadChapter(mangaId: String, sourceId: String): Float? {
        return readChaptersDao.getHighestReadChapter(mangaId, sourceId)
    }

    /**
     * Get count of read chapters
     */
    suspend fun getReadChaptersCount(mangaId: String, sourceId: String): Int {
        return readChaptersDao.getReadChaptersCount(mangaId, sourceId)
    }

    /**
     * Clear all read chapters for a manga
     */
    suspend fun clearReadChapters(mangaId: String, sourceId: String) {
        readChaptersDao.clearReadChapters(mangaId, sourceId)
    }

    /**
     * Sync manga reading progress to AniList
     * Updates the chapter count on AniList based on highest chapter read
     */
    private suspend fun syncMangaProgressToAniList(mangaId: String, sourceId: String) {
        try {
            val highestChapter = getHighestReadChapter(mangaId, sourceId) ?: return

            // Get AniList manga ID from title/search (would need implementation)
            // For now, we'll just log it
            android.util.Log.d(
                "ReadChaptersRepository",
                "Would sync to AniList: manga=$mangaId, chapters read=${highestChapter.toInt()}"
            )

            // TODO: Implement actual AniList mutation
            // aniListManager.updateMangaProgress(aniListId, highestChapter.toInt())
        } catch (e: Exception) {
            android.util.Log.e("ReadChaptersRepository", "Failed to sync to AniList", e)
        }
    }
}
