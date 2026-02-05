package com.omnistream.data.anilist

import com.omnistream.data.repository.WatchHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AniListSyncManager @Inject constructor(
    private val aniListApi: AniListApi,
    private val authManager: AniListAuthManager,
    private val watchHistoryRepository: WatchHistoryRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun syncMangaProgress(
        mangaTitle: String,
        chapterNumber: Int,
        totalChapters: Int
    ) {
        if (!authManager.isLoggedIn()) return

        scope.launch {
            try {
                // Search for manga on AniList
                val results = aniListApi.searchManga(mangaTitle)
                val manga = results.firstOrNull() ?: return@launch

                // Determine status
                val status = when {
                    chapterNumber >= totalChapters -> AniListStatus.COMPLETED
                    chapterNumber > 0 -> AniListStatus.CURRENT
                    else -> AniListStatus.PLANNING
                }

                // Update progress
                aniListApi.updateMangaProgress(
                    mangaId = manga.id,
                    progress = chapterNumber,
                    status = status
                )

                android.util.Log.d("AniListSync", "Synced manga: ${manga.title}, progress: $chapterNumber")
            } catch (e: Exception) {
                android.util.Log.e("AniListSync", "Failed to sync manga", e)
            }
        }
    }

    fun syncAnimeProgress(
        animeTitle: String,
        episodeNumber: Int,
        totalEpisodes: Int
    ) {
        if (!authManager.isLoggedIn()) return

        scope.launch {
            try {
                // Search for anime on AniList
                val results = aniListApi.searchAnime(animeTitle)
                val anime = results.firstOrNull() ?: return@launch

                // Determine status
                val status = when {
                    episodeNumber >= totalEpisodes -> AniListStatus.COMPLETED
                    episodeNumber > 0 -> AniListStatus.CURRENT
                    else -> AniListStatus.PLANNING
                }

                // Update progress
                aniListApi.updateAnimeProgress(
                    animeId = anime.id,
                    progress = episodeNumber,
                    status = status
                )

                android.util.Log.d("AniListSync", "Synced anime: ${anime.title}, progress: $episodeNumber")
            } catch (e: Exception) {
                android.util.Log.e("AniListSync", "Failed to sync anime", e)
            }
        }
    }
}
