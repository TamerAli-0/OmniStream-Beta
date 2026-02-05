package com.omnistream.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnistream.domain.model.Manga
import com.omnistream.domain.model.Video
import com.omnistream.source.SourceManager
import com.omnistream.source.model.MangaSource
import com.omnistream.source.model.VideoSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val sourceManager: SourceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    private var currentFilter: String? = null

    init {
        loadSources()
    }

    fun setFilter(filterType: String?) {
        android.util.Log.d("BrowseViewModel", "setFilter called with: $filterType (current: $currentFilter)")
        // Always reload when filter is set, even if same (to ensure fresh content)
        currentFilter = filterType
        // Clear old content immediately
        _uiState.value = _uiState.value.copy(
            sources = emptyList(),
            selectedSourceIndex = -1,
            mangaItems = emptyList(),
            videoItems = emptyList(),
            isLoading = true,
            error = null
        )
        loadSources()
    }

    private fun loadSources() {
        val mangaSources = sourceManager.getAllMangaSources()
        val videoSources = sourceManager.getAllVideoSources()

        val allSources = mutableListOf<SourceInfo>()

        android.util.Log.d("BrowseViewModel", "Loading sources with filter: $currentFilter")
        android.util.Log.d("BrowseViewModel", "Available manga sources: ${mangaSources.map { it.name }}")
        android.util.Log.d("BrowseViewModel", "Available video sources: ${videoSources.map { it.name }}")

        when (currentFilter) {
            "manga" -> {
                // Only manga sources
                android.util.Log.d("BrowseViewModel", "Filter: manga - adding only manga sources")
                mangaSources.forEach { source ->
                    allSources.add(SourceInfo(
                        id = source.id,
                        name = source.name,
                        type = SourceType.MANGA
                    ))
                }
            }
            "anime" -> {
                // Only anime video sources (check for anime keywords in name/id)
                android.util.Log.d("BrowseViewModel", "Filter: anime - checking video sources for anime")
                videoSources.forEach { source ->
                    val isAnime = isAnimeSource(source)
                    android.util.Log.d("BrowseViewModel", "  ${source.name}: isAnime=$isAnime")
                    if (isAnime) {
                        allSources.add(SourceInfo(
                            id = source.id,
                            name = source.name,
                            type = SourceType.VIDEO
                        ))
                    }
                }
            }
            "movies" -> {
                // Only movie/TV video sources (not anime)
                android.util.Log.d("BrowseViewModel", "Filter: movies - checking video sources for non-anime")
                videoSources.forEach { source ->
                    val isAnime = isAnimeSource(source)
                    android.util.Log.d("BrowseViewModel", "  ${source.name}: isAnime=$isAnime (will ${if (isAnime) "skip" else "add"})")
                    if (!isAnime) {
                        allSources.add(SourceInfo(
                            id = source.id,
                            name = source.name,
                            type = SourceType.VIDEO
                        ))
                    }
                }
            }
            else -> {
                // All sources (no filter)
                android.util.Log.d("BrowseViewModel", "Filter: none - adding all sources")
                mangaSources.forEach { source ->
                    allSources.add(SourceInfo(
                        id = source.id,
                        name = source.name,
                        type = SourceType.MANGA
                    ))
                }
                videoSources.forEach { source ->
                    allSources.add(SourceInfo(
                        id = source.id,
                        name = source.name,
                        type = SourceType.VIDEO
                    ))
                }
            }
        }

        android.util.Log.d("BrowseViewModel", "Final sources after filter: ${allSources.map { it.name }}")

        _uiState.value = _uiState.value.copy(
            sources = allSources,
            selectedSourceIndex = if (allSources.isNotEmpty()) 0 else -1
        )

        if (allSources.isNotEmpty()) {
            loadSourceContent(allSources[0])
        }
    }

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
        val isAnime = animeKeywords.any { keyword ->
            lowerName.contains(keyword) || lowerId.contains(keyword)
        }
        android.util.Log.d("BrowseViewModel", "isAnimeSource(${source.name}): name='$lowerName', id='$lowerId', result=$isAnime")
        return isAnime
    }

    fun selectSource(index: Int) {
        val sources = _uiState.value.sources
        if (index in sources.indices) {
            _uiState.value = _uiState.value.copy(selectedSourceIndex = index)
            loadSourceContent(sources[index])
        }
    }

    private fun loadSourceContent(sourceInfo: SourceInfo) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                mangaItems = emptyList(),
                videoItems = emptyList()
            )

            try {
                when (sourceInfo.type) {
                    SourceType.MANGA -> {
                        val source = sourceManager.getMangaSource(sourceInfo.id)
                            ?: throw Exception("Source not found")
                        val items = source.getPopular(1)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            mangaItems = items
                        )
                    }
                    SourceType.VIDEO -> {
                        val source = sourceManager.getVideoSource(sourceInfo.id)
                            ?: throw Exception("Source not found")
                        val homeSections = source.getHomePage()
                        val items = homeSections.flatMap { it.items }.take(30)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            videoItems = items
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("BrowseViewModel", "Failed to load ${sourceInfo.name}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "${sourceInfo.name}: ${e.message}"
                )
            }
        }
    }

    fun refresh() {
        val index = _uiState.value.selectedSourceIndex
        val sources = _uiState.value.sources
        if (index in sources.indices) {
            loadSourceContent(sources[index])
        }
    }
}

data class BrowseUiState(
    val sources: List<SourceInfo> = emptyList(),
    val selectedSourceIndex: Int = -1,
    val isLoading: Boolean = false,
    val error: String? = null,
    val mangaItems: List<Manga> = emptyList(),
    val videoItems: List<Video> = emptyList()
)

data class SourceInfo(
    val id: String,
    val name: String,
    val type: SourceType
)

enum class SourceType {
    MANGA, VIDEO
}
