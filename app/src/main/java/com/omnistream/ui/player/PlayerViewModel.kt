package com.omnistream.ui.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.omnistream.data.local.DownloadDao
import com.omnistream.data.local.WatchHistoryEntity
import com.omnistream.data.preferences.PlayerPreferencesRepository
import com.omnistream.data.repository.WatchHistoryRepository
import com.omnistream.domain.model.Episode
import com.omnistream.domain.model.VideoLink
import com.omnistream.domain.models.VideoQuality
import com.omnistream.source.SourceManager
import com.omnistream.source.model.VideoType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val watchHistoryRepository: WatchHistoryRepository,
    private val downloadDao: DownloadDao,
    private val prefsRepository: PlayerPreferencesRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sourceId: String = savedStateHandle["sourceId"] ?: ""
    private val videoId: String = java.net.URLDecoder.decode(savedStateHandle["videoId"] ?: "", "UTF-8")
    private val episodeId: String = java.net.URLDecoder.decode(savedStateHandle["episodeId"] ?: "", "UTF-8")
    private val videoTitle: String = savedStateHandle["title"] ?: ""
    private val coverUrl: String? = savedStateHandle["coverUrl"]

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    // Quality preference Flow from DataStore
    val maxVideoHeight: StateFlow<Int> = prefsRepository.preferences
        .map { it.maxVideoHeight }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Int.MAX_VALUE
        )

    // Subtitle state for tracking loaded subtitle URI
    private val _currentSubtitleUri = MutableStateFlow<Uri?>(null)
    val currentSubtitleUri: StateFlow<Uri?> = _currentSubtitleUri

    init {
        loadVideoLinks()
        loadSavedProgress()
    }

    /**
     * Apply quality preference to player's track selection parameters.
     *
     * Call this from the Activity after player initialization.
     * The Flow collection will automatically apply quality constraints when preferences change.
     *
     * @param player ExoPlayer instance to apply constraints to
     */
    fun applyQualityPreferences(player: ExoPlayer) {
        viewModelScope.launch {
            maxVideoHeight.collect { height ->
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(Int.MAX_VALUE, height)
                    .build()
            }
        }
    }

    private fun loadVideoLinks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            android.util.Log.d("PlayerViewModel", "Loading video links: sourceId=$sourceId, videoId=$videoId, episodeId=$episodeId")

            try {
                // Check if episode is downloaded for offline playback (query by fields, not reconstructed ID)
                val downloadEntity = downloadDao.getByEpisode(sourceId, videoId, episodeId)

                if (downloadEntity != null && downloadEntity.status == "completed") {
                    // Check both the stored path and .ts variant (HLS downloads save as .ts)
                    val storedFile = File(downloadEntity.filePath)
                    val tsFile = if (storedFile.extension == "mp4") {
                        File(storedFile.parent, storedFile.nameWithoutExtension + ".ts")
                    } else null
                    val localFile = when {
                        storedFile.exists() && storedFile.length() > 1000 -> storedFile
                        tsFile?.exists() == true && tsFile.length() > 1000 -> tsFile
                        else -> null
                    }
                    if (localFile != null) {
                        val localLink = VideoLink(
                            url = "file://${localFile.absolutePath}",
                            quality = "Downloaded",
                            extractorName = "offline",
                            isM3u8 = false,
                            isDash = false
                        )
                        android.util.Log.d("PlayerViewModel", "Using offline video: ${localFile.absolutePath} (${localFile.length()} bytes)")

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            links = listOf(localLink),
                            episodeTitle = videoTitle.ifBlank { "Episode" },
                            selectedLink = localLink,
                            isOffline = true
                        )
                        return@launch
                    }
                }

                val source = sourceManager.getVideoSource(sourceId)
                    ?: throw Exception("Source not found: $sourceId")

                // Construct episode URL based on source type
                val episodeUrl = when (sourceId) {
                    "gogoanime" -> "${source.baseUrl}/$episodeId/"  // Trailing slash required
                    "vidsrc" -> episodeId // VidSrc episode ID contains the full embed URL path
                    else -> "${source.baseUrl}/episode/$episodeId"
                }
                android.util.Log.d("PlayerViewModel", "Constructed episode URL: $episodeUrl")

                // Extract season and episode numbers from episodeId (format: tv-12345_s1_e5)
                val seasonEpisodeMatch = Regex("""_s(\d+)_e(\d+)""").find(episodeId)
                val season = seasonEpisodeMatch?.groupValues?.get(1)?.toIntOrNull()
                val episodeNum = seasonEpisodeMatch?.groupValues?.get(2)?.toIntOrNull()
                    ?: extractEpisodeNumber(episodeId)

                // Create episode object
                val episode = Episode(
                    id = episodeId,
                    videoId = videoId,
                    sourceId = sourceId,
                    url = episodeUrl,
                    number = episodeNum,
                    season = season
                )

                // Get video links
                val links = source.getLinks(episode)
                android.util.Log.d("PlayerViewModel", "Loaded ${links.size} video links")

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    links = links,
                    episodeTitle = videoTitle.ifBlank { episode.title ?: "Episode ${episode.number}" },
                    // Auto-select first link
                    selectedLink = links.firstOrNull()
                )

            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Failed to load video links", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load video"
                )
            }
        }
    }

    private fun extractEpisodeNumber(episodeId: String): Int {
        return Regex("""(\d+)""").findAll(episodeId).lastOrNull()?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }

    fun selectLink(link: VideoLink) {
        _uiState.value = _uiState.value.copy(selectedLink = link)
    }

    private fun loadSavedProgress() {
        viewModelScope.launch {
            val saved = watchHistoryRepository.getProgress(videoId, sourceId)
            if (saved != null && !saved.isCompleted && saved.progressPosition > 0) {
                _uiState.value = _uiState.value.copy(
                    savedPosition = saved.progressPosition,
                    showResumeDialog = true
                )
            }
        }
    }

    fun dismissResumeDialog() {
        _uiState.value = _uiState.value.copy(showResumeDialog = false)
    }

    fun startFromBeginning() {
        _uiState.value = _uiState.value.copy(showResumeDialog = false, savedPosition = null)
    }

    fun saveVideoProgress(positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        val percentage = positionMs.toFloat() / durationMs
        val isCompleted = percentage > 0.90f
        viewModelScope.launch {
            watchHistoryRepository.upsert(
                WatchHistoryEntity(
                    id = "$sourceId:$videoId",
                    contentId = videoId,
                    sourceId = sourceId,
                    contentType = "video",
                    title = videoTitle.ifBlank { _uiState.value.episodeTitle ?: "Unknown" },
                    coverUrl = coverUrl,
                    episodeId = episodeId,
                    progressPosition = positionMs,
                    totalDuration = durationMs,
                    progressPercentage = percentage,
                    lastWatchedAt = System.currentTimeMillis(),
                    isCompleted = isCompleted
                )
            )
        }
    }

    /**
     * Set video quality preference.
     *
     * Saves the quality to DataStore, which triggers the maxVideoHeight Flow.
     * The applyQualityPreferences() collector will automatically apply the new constraint.
     *
     * @param quality VideoQuality preset (AUTO, SD_480P, HD_720P, FULL_HD_1080P, or Custom)
     */
    fun setQuality(quality: VideoQuality) {
        viewModelScope.launch {
            prefsRepository.setMaxVideoHeight(quality.maxHeight)
            // Flow collection in applyQualityPreferences() will apply it
        }
    }

    /**
     * Set current subtitle URI.
     *
     * Used to track which subtitle file is currently loaded.
     * Activity should observe this to reload media when subtitle changes.
     *
     * @param uri Subtitle file URI (SRT/VTT) or null to disable subtitles
     */
    fun setSubtitle(uri: Uri?) {
        _currentSubtitleUri.value = uri
    }
}

data class PlayerUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val links: List<VideoLink> = emptyList(),
    val selectedLink: VideoLink? = null,
    val episodeTitle: String? = null,
    val isOffline: Boolean = false,
    val savedPosition: Long? = null,
    val showResumeDialog: Boolean = false
)
