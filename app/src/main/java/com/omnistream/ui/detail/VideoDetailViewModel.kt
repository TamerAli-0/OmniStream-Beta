package com.omnistream.ui.detail

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnistream.data.local.DownloadEntity
import com.omnistream.data.local.FavoriteDao
import com.omnistream.data.local.FavoriteEntity
import com.omnistream.data.repository.DownloadRepository
import com.omnistream.domain.model.Episode
import com.omnistream.domain.model.Video
import com.omnistream.source.SourceManager
import com.omnistream.source.model.VideoType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val favoriteDao: FavoriteDao,
    private val downloadRepository: DownloadRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sourceId: String = savedStateHandle["sourceId"] ?: ""
    private val videoId: String = java.net.URLDecoder.decode(savedStateHandle["videoId"] ?: "", "UTF-8")

    private val _uiState = MutableStateFlow(VideoDetailUiState())
    val uiState: StateFlow<VideoDetailUiState> = _uiState.asStateFlow()

    init {
        loadVideoDetails()
        observeFavoriteStatus()
    }

    private fun observeFavoriteStatus() {
        viewModelScope.launch {
            favoriteDao.isFavorite(sourceId, videoId).collect { isFav ->
                _uiState.value = _uiState.value.copy(isFavorite = isFav)
            }
        }
    }

    private fun loadVideoDetails() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            android.util.Log.d("VideoDetailViewModel", "Loading video: sourceId=$sourceId, videoId=$videoId")

            try {
                val source = sourceManager.getVideoSource(sourceId)
                    ?: throw Exception("Source not found: $sourceId")

                // Construct URL based on source type
                val videoUrl = when (sourceId) {
                    "gogoanime" -> "${source.baseUrl}/category/$videoId"
                    "vidsrc" -> "${source.baseUrl}/embed/${if (videoId.startsWith("movie")) "movie" else "tv"}/${videoId.substringAfter("-")}"
                    "animekai" -> "${source.baseUrl}/watch/$videoId"
                    "flickystream", "watchflix" -> {
                        // Handle prefixed video IDs (movie-12345 or tv-12345)
                        val typePrefix = if (videoId.startsWith("tv-")) "tv" else "movie"
                        val tmdbId = videoId.substringAfter("-", videoId)
                        "${source.baseUrl}/$typePrefix/$tmdbId"
                    }
                    "goojara" -> "${source.baseUrl}/$videoId"
                    else -> "${source.baseUrl}/$videoId"
                }

                // Determine video type from ID
                val videoType = when {
                    sourceId == "gogoanime" -> VideoType.ANIME
                    sourceId == "animekai" -> VideoType.ANIME
                    sourceId == "flickystream" -> if (videoId.contains("tv") || videoId.contains("series")) VideoType.TV_SERIES else VideoType.MOVIE
                    videoId.startsWith("movie-") -> VideoType.MOVIE
                    videoId.startsWith("tv-") -> VideoType.TV_SERIES
                    else -> VideoType.MOVIE
                }

                // Create initial video object with ID
                val initialVideo = Video(
                    id = videoId,
                    sourceId = sourceId,
                    title = "",
                    url = videoUrl,
                    type = videoType
                )

                // Get full details
                val video = source.getDetails(initialVideo)
                android.util.Log.d("VideoDetailViewModel", "Loaded video: ${video.title}")

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    video = video
                )

                // Load episodes
                loadEpisodes(source, video)

            } catch (e: Exception) {
                android.util.Log.e("VideoDetailViewModel", "Failed to load video", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load video"
                )
            }
        }
    }

    fun retryLoad() {
        loadVideoDetails()
    }

    private suspend fun loadEpisodes(source: com.omnistream.source.model.VideoSource, video: Video) {
        try {
            val episodes = source.getEpisodes(video)
            _uiState.value = _uiState.value.copy(episodes = episodes)
        } catch (e: Exception) {
            android.util.Log.e("VideoDetailViewModel", "Failed to load episodes", e)
            _uiState.value = _uiState.value.copy(
                episodesError = e.message ?: "Failed to load episodes"
            )
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val video = _uiState.value.video ?: return@launch
            if (_uiState.value.isFavorite) {
                favoriteDao.removeFavorite(sourceId, videoId)
            } else {
                favoriteDao.addFavorite(
                    FavoriteEntity(
                        id = "$sourceId:$videoId",
                        contentId = videoId,
                        sourceId = sourceId,
                        contentType = "video",
                        title = video.title,
                        coverUrl = video.posterUrl
                    )
                )
            }
        }
    }

    // --- Download methods ---

    private fun sanitizeForPath(input: String): String {
        return input.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(120)
    }

    fun downloadEpisode(episode: Episode) {
        val video = _uiState.value.video ?: return
        val safeEpisodeId = sanitizeForPath(episode.id)
        val downloadId = "video_${sourceId}_${sanitizeForPath(videoId)}_$safeEpisodeId"
        val filePath = "${context.filesDir}/downloads/video/$sourceId/${sanitizeForPath(videoId)}/$safeEpisodeId.mp4"

        val entity = DownloadEntity(
            id = downloadId,
            contentId = videoId,
            sourceId = sourceId,
            contentType = "video",
            title = "${video.title} - Ep. ${episode.number}",
            coverUrl = video.posterUrl,
            episodeId = episode.id,
            filePath = filePath,
            status = "pending"
        )

        _uiState.value = _uiState.value.copy(
            downloadingEpisodeIds = _uiState.value.downloadingEpisodeIds + episode.id
        )

        viewModelScope.launch {
            try {
                downloadRepository.enqueueDownload(entity)
                Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("VideoDetailViewModel", "Failed to enqueue download", e)
                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun downloadSelectedEpisodes() {
        val episodes = _uiState.value.episodes
        val selectedIds = _uiState.value.selectedEpisodes
        val selected = episodes.filter { it.id in selectedIds }
        selected.forEach { downloadEpisode(it) }
        clearSelection()
    }

    fun toggleSelectionMode() {
        val current = _uiState.value
        _uiState.value = current.copy(
            isSelectionMode = !current.isSelectionMode,
            selectedEpisodes = if (current.isSelectionMode) emptySet() else current.selectedEpisodes
        )
    }

    fun toggleEpisodeSelection(episodeId: String) {
        val current = _uiState.value.selectedEpisodes
        _uiState.value = _uiState.value.copy(
            selectedEpisodes = if (episodeId in current) current - episodeId else current + episodeId
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            isSelectionMode = false,
            selectedEpisodes = emptySet()
        )
    }
}

data class VideoDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val episodesError: String? = null,
    val video: Video? = null,
    val episodes: List<Episode> = emptyList(),
    val isFavorite: Boolean = false,
    val selectedEpisodes: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val downloadingEpisodeIds: Set<String> = emptySet()
)
