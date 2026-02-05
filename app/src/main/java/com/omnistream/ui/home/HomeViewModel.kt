package com.omnistream.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnistream.data.local.WatchHistoryEntity
import com.omnistream.data.repository.WatchHistoryRepository
import com.omnistream.domain.model.Manga
import com.omnistream.domain.model.Video
import com.omnistream.source.SourceManager
import com.omnistream.source.SourceStatus
import com.omnistream.source.model.MangaSource
import com.omnistream.source.model.VideoSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val watchHistoryRepository: WatchHistoryRepository,
    private val authManager: com.omnistream.data.anilist.AniListAuthManager,
    private val anilistApi: com.omnistream.data.anilist.AniListApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Cache for source health status
    private val sourceHealth = mutableMapOf<String, SourceHealth>()

    fun getUsername(): String? = authManager.getUsername()

    fun refreshAniListStats() {
        android.util.Log.d("HomeViewModel", "Manual refresh of AniList stats triggered")
        loadAniListStats()
    }

    init {
        // Observe continue rows (reactive, auto-updates when progress changes)
        viewModelScope.launch {
            watchHistoryRepository.getContinueWatching().collect { items ->
                _uiState.value = _uiState.value.copy(continueWatching = items)
            }
        }
        viewModelScope.launch {
            watchHistoryRepository.getContinueReading().collect { items ->
                _uiState.value = _uiState.value.copy(continueReading = items)
            }
        }
        loadHomeContent()
        loadAniListStats()

        // Periodically check for AniList login and refresh stats
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5000) // Check every 5 seconds
                if (authManager.isLoggedIn() && _uiState.value.anilistStats == null) {
                    android.util.Log.d("HomeViewModel", "Detected new AniList login, refreshing stats...")
                    loadAniListStats()
                }
            }
        }
    }

    private fun loadAniListStats() {
        android.util.Log.d("HomeViewModel", "loadAniListStats called")
        android.util.Log.d("HomeViewModel", "isLoggedIn: ${authManager.isLoggedIn()}")

        if (!authManager.isLoggedIn()) {
            android.util.Log.d("HomeViewModel", "Not logged in to AniList, skipping stats")
            return
        }

        viewModelScope.launch {
            try {
                android.util.Log.d("HomeViewModel", "Fetching AniList user and statistics...")

                // Fetch user info and statistics from AniList
                val anilistUser = anilistApi.getCurrentUser()
                android.util.Log.d("HomeViewModel", "AniList user: ${anilistUser?.name}")

                val anilistStatistics = anilistApi.getUserStatistics()
                android.util.Log.d("HomeViewModel", "AniList stats - Chapters: ${anilistStatistics?.chaptersRead}, Episodes: ${anilistStatistics?.episodesWatched}")

                // Use actual AniList statistics if available
                if (anilistStatistics != null && anilistUser != null) {
                    android.util.Log.d("HomeViewModel", "Using AniList statistics")
                    val stats = AniListStats(
                        episodesWatched = anilistStatistics.episodesWatched,
                        chaptersRead = anilistStatistics.chaptersRead,
                        user = anilistUser
                    )
                    _uiState.value = _uiState.value.copy(anilistStats = stats)
                    android.util.Log.d("HomeViewModel", "Stats updated - Chapters: ${stats.chaptersRead}, Episodes: ${stats.episodesWatched}")
                } else {
                    android.util.Log.d("HomeViewModel", "API returned null, could not load stats")
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Failed to load AniList stats", e)
            }
        }
    }

    fun deleteFromHistory(id: String) {
        viewModelScope.launch {
            watchHistoryRepository.delete(id)
        }
    }

    fun loadHomeContent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            android.util.Log.d("HomeViewModel", "Starting to load home content with source prioritization")

            try {
                // First, test all sources to determine which are working
                testSourceHealth()

                // Load content from sources, prioritized by health/speed
                val mangaDeferred = async { loadMangaContent() }
                val videoDeferred = async { loadVideoContent() }

                val mangaSections = mangaDeferred.await()
                val videoSections = videoDeferred.await()

                android.util.Log.d("HomeViewModel", "Loaded ${mangaSections.size} manga sections, ${videoSections.size} video sections")

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    mangaSections = mangaSections,
                    videoSections = videoSections
                )
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Failed to load home content", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load content"
                )
            }
        }
    }

    /**
     * Test health of all sources with quick connectivity checks.
     * This helps prioritize working sources and avoid slow/broken ones.
     */
    private suspend fun testSourceHealth() {
        android.util.Log.d("HomeViewModel", "Testing source health...")

        val videoSources = sourceManager.getAllVideoSources()
        val mangaSources = sourceManager.getAllMangaSources()

        // Test video sources in parallel with timeout
        val videoHealthTests = videoSources.map { source ->
            viewModelScope.async {
                val startTime = System.currentTimeMillis()
                try {
                    // Quick ping test with 5 second timeout
                    val isWorking = withTimeoutOrNull(5000L) {
                        source.ping()
                    } ?: false

                    val latency = System.currentTimeMillis() - startTime
                    val health = SourceHealth(
                        sourceId = source.id,
                        isWorking = isWorking,
                        latency = latency,
                        status = when {
                            !isWorking -> SourceStatus.BROKEN
                            latency > 3000 -> SourceStatus.SLOW
                            latency > 1500 -> SourceStatus.NORMAL
                            else -> SourceStatus.FAST
                        }
                    )
                    android.util.Log.d("HomeViewModel", "Source ${source.name}: ${health.status} (${latency}ms)")
                    health
                } catch (e: Exception) {
                    android.util.Log.e("HomeViewModel", "Source ${source.name} health check failed", e)
                    SourceHealth(source.id, false, Long.MAX_VALUE, SourceStatus.BROKEN)
                }
            }
        }

        // Test manga sources in parallel with timeout
        val mangaHealthTests = mangaSources.map { source ->
            viewModelScope.async {
                val startTime = System.currentTimeMillis()
                try {
                    val isWorking = withTimeoutOrNull(5000L) {
                        source.ping()
                    } ?: false

                    val latency = System.currentTimeMillis() - startTime
                    val health = SourceHealth(
                        sourceId = source.id,
                        isWorking = isWorking,
                        latency = latency,
                        status = when {
                            !isWorking -> SourceStatus.BROKEN
                            latency > 3000 -> SourceStatus.SLOW
                            latency > 1500 -> SourceStatus.NORMAL
                            else -> SourceStatus.FAST
                        }
                    )
                    android.util.Log.d("HomeViewModel", "Source ${source.name}: ${health.status} (${latency}ms)")
                    health
                } catch (e: Exception) {
                    android.util.Log.e("HomeViewModel", "Source ${source.name} health check failed", e)
                    SourceHealth(source.id, false, Long.MAX_VALUE, SourceStatus.BROKEN)
                }
            }
        }

        // Wait for all health checks
        val allHealthResults = (videoHealthTests + mangaHealthTests).awaitAll()
        allHealthResults.forEach { health ->
            sourceHealth[health.sourceId] = health
        }

        android.util.Log.d("HomeViewModel", "Source health testing complete. Working sources: ${sourceHealth.values.count { it.isWorking }}")
    }

    /**
     * Get video sources sorted by health (working and fast sources first)
     */
    private fun getVideoSourcesSortedByHealth(): List<VideoSource> {
        return sourceManager.getAllVideoSources().sortedWith(
            compareBy(
                // Working sources first
                { sourceHealth[it.id]?.isWorking != true },
                // Then by status (FAST < NORMAL < SLOW < BROKEN)
                { sourceHealth[it.id]?.status?.ordinal ?: Int.MAX_VALUE },
                // Then by latency
                { sourceHealth[it.id]?.latency ?: Long.MAX_VALUE }
            )
        )
    }

    /**
     * Get manga sources sorted by health
     */
    private fun getMangaSourcesSortedByHealth(): List<MangaSource> {
        return sourceManager.getAllMangaSources().sortedWith(
            compareBy(
                { sourceHealth[it.id]?.isWorking != true },
                { sourceHealth[it.id]?.status?.ordinal ?: Int.MAX_VALUE },
                { sourceHealth[it.id]?.latency ?: Long.MAX_VALUE }
            )
        )
    }

    private suspend fun loadMangaContent(): List<MangaSection> {
        val sections = mutableListOf<MangaSection>()
        val errors = mutableListOf<String>()

        // Get sources sorted by health (working/fast sources first)
        val sortedSources = getMangaSourcesSortedByHealth()
        android.util.Log.d("HomeViewModel", "Loading manga from ${sortedSources.size} sources (sorted by health)")

        sortedSources.forEach { source ->
            val health = sourceHealth[source.id]

            // Skip broken sources entirely
            if (health?.status == SourceStatus.BROKEN) {
                android.util.Log.d("HomeViewModel", "Skipping broken source: ${source.name}")
                return@forEach
            }

            android.util.Log.d("HomeViewModel", "Loading manga source: ${source.name} (${health?.status})")
            try {
                // Use timeout for slow sources
                val timeout = if (health?.status == SourceStatus.SLOW) 15000L else 10000L

                val popular = withTimeoutOrNull(timeout) {
                    source.getPopular(1).take(10)
                } ?: emptyList()

                android.util.Log.d("HomeViewModel", "${source.name} popular: ${popular.size} items")
                if (popular.isNotEmpty()) {
                    sections.add(MangaSection(
                        title = "${source.name} - Popular",
                        items = popular,
                        sourceId = source.id,
                        sourceStatus = health?.status ?: SourceStatus.NORMAL
                    ))
                }

                val latest = withTimeoutOrNull(timeout) {
                    source.getLatest(1).take(10)
                } ?: emptyList()

                android.util.Log.d("HomeViewModel", "${source.name} latest: ${latest.size} items")
                if (latest.isNotEmpty()) {
                    sections.add(MangaSection(
                        title = "${source.name} - Latest",
                        items = latest,
                        sourceId = source.id,
                        sourceStatus = health?.status ?: SourceStatus.NORMAL
                    ))
                }
            } catch (e: Exception) {
                errors.add("${source.name}: ${e.message}")
                android.util.Log.e("HomeViewModel", "Failed to load ${source.name}: ${e.message}", e)
                // Mark source as broken for future reference
                sourceHealth[source.id] = SourceHealth(source.id, false, Long.MAX_VALUE, SourceStatus.BROKEN)
            }
        }

        if (sections.isEmpty() && errors.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Manga sources failed:\n${errors.joinToString("\n")}")
        }

        return sections
    }

    /**
     * Check if a video source is anime-specific based on name/ID keywords
     */
    private fun isAnimeSource(source: VideoSource): Boolean {
        val lowerName = source.name.lowercase()
        val lowerId = source.id.lowercase()
        val animeKeywords = listOf(
            "anime",
            "gogo",
            "animekai",
            "aniwatch",
            "crunchyroll",
            "9anime",
            "anilist"
        )
        return animeKeywords.any { keyword ->
            lowerName.contains(keyword) || lowerId.contains(keyword)
        }
    }

    /**
     * Check if a source ID is anime-specific (for filtering continue watching)
     */
    fun isAnimeSourceById(sourceId: String?): Boolean {
        if (sourceId == null) return false
        val lowerId = sourceId.lowercase()
        val animeKeywords = listOf(
            "anime",
            "gogo",
            "animekai",
            "aniwatch",
            "crunchyroll",
            "9anime",
            "anilist"
        )
        return animeKeywords.any { keyword -> lowerId.contains(keyword) }
    }

    private suspend fun loadVideoContent(): List<VideoSection> {
        val sections = mutableListOf<VideoSection>()
        val errors = mutableListOf<String>()

        // Get sources sorted by health (working/fast sources first)
        val sortedSources = getVideoSourcesSortedByHealth()
        android.util.Log.d("HomeViewModel", "Loading video from ${sortedSources.size} sources (sorted by health)")

        sortedSources.forEach { source ->
            val health = sourceHealth[source.id]

            // Skip broken sources entirely
            if (health?.status == SourceStatus.BROKEN) {
                android.util.Log.d("HomeViewModel", "Skipping broken source: ${source.name}")
                return@forEach
            }

            android.util.Log.d("HomeViewModel", "Loading video source: ${source.name} (${health?.status})")
            try {
                // Use timeout - shorter for slow sources to not block UI
                val timeout = if (health?.status == SourceStatus.SLOW) 15000L else 10000L

                val homeSections = withTimeoutOrNull(timeout) {
                    source.getHomePage()
                } ?: emptyList()

                android.util.Log.d("HomeViewModel", "${source.name}: ${homeSections.size} sections loaded")

                // Check if this is an anime source
                val isAnimeSource = isAnimeSource(source)

                homeSections.forEach { section ->
                    android.util.Log.d("HomeViewModel", "${source.name} - ${section.name}: ${section.items.size} items")
                    sections.add(VideoSection(
                        title = "${source.name} - ${section.name}",
                        items = section.items.take(10),
                        sourceId = source.id,
                        sourceStatus = health?.status ?: SourceStatus.NORMAL,
                        isAnime = isAnimeSource
                    ))
                }
            } catch (e: Exception) {
                errors.add("${source.name}: ${e.message}")
                android.util.Log.e("HomeViewModel", "Failed to load ${source.name}: ${e.message}", e)
                // Mark source as broken for future reference
                sourceHealth[source.id] = SourceHealth(source.id, false, Long.MAX_VALUE, SourceStatus.BROKEN)
            }
        }

        if (sections.isEmpty() && errors.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Video sources failed:\n${errors.joinToString("\n")}")
        }

        return sections
    }

    fun refresh() {
        // Clear cached health on refresh to re-test
        sourceHealth.clear()
        loadHomeContent()
    }
}

/**
 * Health status for a source
 */
data class SourceHealth(
    val sourceId: String,
    val isWorking: Boolean,
    val latency: Long,
    val status: SourceStatus
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val mangaSections: List<MangaSection> = emptyList(),
    val videoSections: List<VideoSection> = emptyList(),
    val continueWatching: List<WatchHistoryEntity> = emptyList(),
    val continueReading: List<WatchHistoryEntity> = emptyList(),
    val favoriteAnime: List<WatchHistoryEntity> = emptyList(),
    val favoriteManga: List<WatchHistoryEntity> = emptyList(),
    val trendingAnime: List<WatchHistoryEntity> = emptyList(),
    val trendingManga: List<WatchHistoryEntity> = emptyList(),
    val anilistStats: AniListStats? = null
)

data class AniListStats(
    val episodesWatched: Int = 0,
    val chaptersRead: Int = 0,
    val animeCount: Int = 0,
    val mangaCount: Int = 0,
    val user: com.omnistream.data.anilist.AniListUser? = null
)

data class MangaSection(
    val title: String,
    val items: List<Manga>,
    val sourceId: String,
    val sourceStatus: SourceStatus = SourceStatus.NORMAL
)

data class VideoSection(
    val title: String,
    val items: List<Video>,
    val sourceId: String,
    val sourceStatus: SourceStatus = SourceStatus.NORMAL,
    val isAnime: Boolean = false  // True if source is anime-specific
)
