package com.omnistream.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnistream.data.local.SearchHistoryDao
import com.omnistream.data.local.SearchHistoryEntity
import com.omnistream.data.local.UserPreferences
import com.omnistream.domain.model.Manga
import com.omnistream.domain.model.Video
import com.omnistream.source.SourceManager
import com.omnistream.source.model.VideoType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val searchHistoryDao: SearchHistoryDao,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    // Track failed sources to skip for 10 minutes
    private val failedSourcesCache = mutableMapOf<String, Long>()

    val searchHistory: StateFlow<List<SearchHistoryEntity>> =
        searchHistoryDao.getRecentSearches(limit = 5)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val persistentContentTypeFilter: StateFlow<String> =
        userPreferences.searchContentTypeFilter
            .stateIn(viewModelScope, SharingStarted.Eagerly, "ALL")

    val filteredResults: StateFlow<List<SearchResult>> = _uiState
        .map { uiState ->
            var results = uiState.results

            // Apply content type filter
            results = when (uiState.selectedFilter) {
                SearchFilter.ALL -> results
                SearchFilter.MOVIES -> results.filterIsInstance<SearchResult.VideoResult>()
                    .filter { it.video.type == VideoType.MOVIE }
                SearchFilter.TV -> results.filterIsInstance<SearchResult.VideoResult>()
                    .filter { it.video.type == VideoType.TV_SERIES }
                SearchFilter.ANIME -> results.filterIsInstance<SearchResult.VideoResult>()
                    .filter { it.video.type == VideoType.ANIME }
                SearchFilter.MANGA -> results.filterIsInstance<SearchResult.MangaResult>()
            }

            // Apply genre filter (if any genres selected)
            if (uiState.selectedGenres.isNotEmpty()) {
                results = results.filter { result ->
                    val genres = when (result) {
                        is SearchResult.VideoResult -> result.video.genres ?: emptyList()
                        is SearchResult.MangaResult -> result.manga.genres ?: emptyList()
                    }
                    // Match if ANY selected genre is present in content's genres
                    genres.any { it in uiState.selectedGenres }
                }
            }

            // Apply year filter (if set)
            uiState.selectedYear?.let { selectedYear ->
                results = results.filter { result ->
                    val year = when (result) {
                        is SearchResult.VideoResult -> result.video.year
                        is SearchResult.MangaResult -> null  // Manga doesn't have year field
                    }
                    year == selectedYear
                }
            }

            results
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(400)
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        flowOf(SearchUiState())
                    } else {
                        flow {
                            emit(_uiState.value.copy(isLoading = true, error = null, query = query))
                            try {
                                val allResults = performSearch(query)

                                // Store ALL results in _uiState.value FIRST
                                _uiState.value = _uiState.value.copy(results = allResults)

                                // Emit state (filteredResults StateFlow will derive from _uiState.results)
                                emit(SearchUiState(
                                    isLoading = false,
                                    results = allResults,
                                    query = query,
                                    error = if (allResults.isEmpty()) {
                                        if (_searchQuery.value.isNotBlank()) {
                                            "No results found. Sources may be slow or unavailable."
                                        } else {
                                            null
                                        }
                                    } else {
                                        null
                                    },
                                    selectedFilter = _uiState.value.selectedFilter,
                                    selectedGenres = _uiState.value.selectedGenres,
                                    selectedYear = _uiState.value.selectedYear
                                ))
                                saveToHistory(query)  // Save after successful search
                            } catch (e: Exception) {
                                android.util.Log.e("SearchViewModel", "Search failed", e)
                                emit(_uiState.value.copy(
                                    isLoading = false,
                                    error = e.message ?: "Search failed"
                                ))
                            }
                        }
                    }
                }
                .collect { state -> _uiState.value = state }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    private suspend fun saveToHistory(query: String) {
        if (query.isNotBlank()) {
            searchHistoryDao.insert(SearchHistoryEntity(query = query.trim()))
        }
    }

    private suspend fun performSearch(query: String): List<SearchResult> = supervisorScope {
        val videoSources = sourceManager.getAllVideoSources()
        val mangaSources = sourceManager.getAllMangaSources()

        // Search all sources in parallel with timeout and failure caching
        val videoResults = videoSources
            .filter { source ->
                // Skip sources that failed in last 10 minutes
                val lastFailure = failedSourcesCache[source.name]
                lastFailure == null || (System.currentTimeMillis() - lastFailure) > 10 * 60 * 1000
            }
            .map { source ->
                async {
                    withTimeoutOrNull(8000L) {
                        try {
                            source.search(query, 1).map { video ->
                                SearchResult.VideoResult(video)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SearchViewModel", "Search failed for ${source.name}", e)
                            failedSourcesCache[source.name] = System.currentTimeMillis()
                            emptyList()
                        }
                    } ?: run {
                        android.util.Log.w("SearchViewModel", "Timeout for ${source.name} after 8000ms")
                        failedSourcesCache[source.name] = System.currentTimeMillis()
                        emptyList()
                    }
                }
            }

        val mangaResults = mangaSources
            .filter { source ->
                // Skip sources that failed in last 10 minutes
                val lastFailure = failedSourcesCache[source.name]
                lastFailure == null || (System.currentTimeMillis() - lastFailure) > 10 * 60 * 1000
            }
            .map { source ->
                async {
                    withTimeoutOrNull(8000L) {
                        try {
                            source.search(query, 1).map { manga ->
                                SearchResult.MangaResult(manga)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SearchViewModel", "Search failed for ${source.name}", e)
                            failedSourcesCache[source.name] = System.currentTimeMillis()
                            emptyList()
                        }
                    } ?: run {
                        android.util.Log.w("SearchViewModel", "Timeout for ${source.name} after 8000ms")
                        failedSourcesCache[source.name] = System.currentTimeMillis()
                        emptyList()
                    }
                }
            }

        val allVideoResults = videoResults.awaitAll().flatten()
        val allMangaResults = mangaResults.awaitAll().flatten()

        // Combine and dedupe by title (keep first occurrence)
        (allVideoResults + allMangaResults)
            .distinctBy { result ->
                when (result) {
                    is SearchResult.VideoResult -> "${result.video.title}-${result.video.sourceId}"
                    is SearchResult.MangaResult -> "${result.manga.title}-${result.manga.sourceId}"
                }
            }
    }

    fun setContentTypeFilter(filter: SearchFilter) {
        viewModelScope.launch {
            userPreferences.setSearchContentTypeFilter(filter.name)
        }
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
    }

    fun getFilteredResults(): List<SearchResult> {
        val results = _uiState.value.results
        return when (_uiState.value.selectedFilter) {
            SearchFilter.ALL -> results
            SearchFilter.MOVIES -> results.filterIsInstance<SearchResult.VideoResult>()
                .filter { it.video.type == VideoType.MOVIE }
            SearchFilter.TV -> results.filterIsInstance<SearchResult.VideoResult>()
                .filter { it.video.type == VideoType.TV_SERIES }
            SearchFilter.ANIME -> results.filterIsInstance<SearchResult.VideoResult>()
                .filter { it.video.type == VideoType.ANIME }
            SearchFilter.MANGA -> results.filterIsInstance<SearchResult.MangaResult>()
        }
    }

    fun deleteFromHistory(query: String) {
        viewModelScope.launch {
            searchHistoryDao.deleteByQuery(query)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            searchHistoryDao.clearAll()
        }
    }

    fun toggleGenreFilter(genre: String) {
        val currentGenres = _uiState.value.selectedGenres
        val newGenres = if (currentGenres.contains(genre)) {
            currentGenres - genre
        } else {
            currentGenres + genre
        }
        _uiState.value = _uiState.value.copy(selectedGenres = newGenres)
    }

    fun setYearFilter(year: Int?) {
        _uiState.value = _uiState.value.copy(selectedYear = year)
    }

    fun clearAllFilters() {
        _uiState.value = _uiState.value.copy(
            selectedFilter = SearchFilter.ALL,
            selectedGenres = emptySet(),
            selectedYear = null
        )
        viewModelScope.launch {
            userPreferences.setSearchContentTypeFilter("ALL")
        }
    }
}

data class SearchUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val selectedFilter: SearchFilter = SearchFilter.ALL,
    val selectedGenres: Set<String> = emptySet(),  // Multi-select, session-only
    val selectedYear: Int? = null  // Single-select, session-only (null = "Any year")
)

sealed class SearchResult {
    data class VideoResult(val video: Video) : SearchResult()
    data class MangaResult(val manga: Manga) : SearchResult()
}

enum class SearchFilter(val label: String) {
    ALL("All"),
    MOVIES("Movies"),
    TV("TV Shows"),
    ANIME("Anime"),
    MANGA("Manga")
}
