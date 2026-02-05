@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.omnistream.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.omnistream.data.local.SearchHistoryEntity
import com.omnistream.domain.model.Manga
import com.omnistream.domain.model.Video
import com.omnistream.source.model.VideoType
import java.net.URLEncoder

@Composable
private fun SectionHeader(title: String, count: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun SectionedResultsList(
    results: List<SearchResult>,
    uiState: SearchUiState,
    onVideoClick: (SearchResult.VideoResult) -> Unit,
    onMangaClick: (SearchResult.MangaResult) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Group by content type (fixed order: Movies -> Anime -> Manga)
        val movies = results.filterIsInstance<SearchResult.VideoResult>()
            .filter { it.video.type == VideoType.MOVIE }
        val anime = results.filterIsInstance<SearchResult.VideoResult>()
            .filter { it.video.type == VideoType.ANIME }
        val manga = results.filterIsInstance<SearchResult.MangaResult>()

        // Movies section (first)
        if (movies.isNotEmpty()) {
            stickyHeader("movies_header") {
                SectionHeader("Movies", movies.size)
            }
            items(movies, key = { it.video.id }) { result ->
                VideoResultCard(
                    video = result.video,
                    onClick = { onVideoClick(result) }
                )
            }
        }

        // Anime section (second)
        if (anime.isNotEmpty()) {
            item { Spacer(Modifier.height(8.dp)) }
            stickyHeader("anime_header") {
                SectionHeader("Anime", anime.size)
            }
            items(anime, key = { it.video.id }) { result ->
                VideoResultCard(
                    video = result.video,
                    onClick = { onVideoClick(result) }
                )
            }
        }

        // Manga section (third)
        if (manga.isNotEmpty()) {
            item { Spacer(Modifier.height(8.dp)) }
            stickyHeader("manga_header") {
                SectionHeader("Manga", manga.size)
            }
            items(manga, key = { it.manga.id }) { result ->
                MangaResultCard(
                    manga = result.manga,
                    onClick = { onMangaClick(result) }
                )
            }
        }

        // Empty state with filter suggestion
        if (results.isEmpty() && uiState.query.isNotBlank() && !uiState.isLoading) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        uiState.error ?: "No results found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Suggest loosening filters if any active
                    if (uiState.selectedFilter != SearchFilter.ALL ||
                        uiState.selectedGenres.isNotEmpty() ||
                        uiState.selectedYear != null
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Try loosening your filters",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onClearFilters) {
                            Text("Clear all filters")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GenreFilterRow(
    availableGenres: List<String>,
    selectedGenres: Set<String>,
    onToggleGenre: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (availableGenres.isNotEmpty()) {
        Column(modifier = modifier) {
            Text(
                "Genres",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableGenres) { genre ->
                    FilterChip(
                        selected = genre in selectedGenres,
                        onClick = { onToggleGenre(genre) },
                        label = { Text(genre) }
                    )
                }
            }
        }
    }
}

@Composable
fun YearFilterRow(
    availableYears: List<Int>,
    selectedYear: Int?,
    onSetYear: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (availableYears.isNotEmpty()) {
        Column(modifier = modifier) {
            Text(
                "Release Year",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableYears) { year ->
                    FilterChip(
                        selected = selectedYear == year,
                        onClick = {
                            onSetYear(if (selectedYear == year) null else year)
                        },
                        label = { Text(year.toString()) }
                    )
                }
            }
        }
    }
}

@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val filteredResults by viewModel.filteredResults.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0a0a0a))
    ) {
        // Search Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF121212),
                            Color(0xFF0a0a0a)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Text(
                text = "Search",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Search Input with History Dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.search(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            // Show dropdown when focused, query empty, and has history
                            isDropdownExpanded = focusState.isFocused &&
                                                  searchQuery.isEmpty() &&
                                                  searchHistory.isNotEmpty()
                        },
                    placeholder = {
                        Text(
                            "Search movies, TV shows, anime, manga...",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                viewModel.search("")
                            }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { focusManager.clearFocus() }
                    )
                )

                SearchHistoryDropdown(
                    searchHistory = searchHistory,
                    isExpanded = isDropdownExpanded,
                    onDismiss = { isDropdownExpanded = false },
                    onHistoryItemClick = { query ->
                        searchQuery = query
                        viewModel.search(query)
                        isDropdownExpanded = false
                        focusManager.clearFocus()
                    },
                    onDeleteClick = { query ->
                        viewModel.deleteFromHistory(query)
                        // Dropdown stays open for multi-delete
                    },
                    onClearAll = {
                        viewModel.clearAllHistory()
                        isDropdownExpanded = false
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Content Type Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SearchFilter.entries) { filter ->
                    FilterChip(
                        selected = uiState.selectedFilter == filter,
                        onClick = { viewModel.setContentTypeFilter(filter) },
                        label = {
                            Text(
                                filter.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            // Extract available genres from current filtered results
            val availableGenres = remember(filteredResults) {
                filteredResults.flatMap { result ->
                    when (result) {
                        is SearchResult.VideoResult -> result.video.genres
                        is SearchResult.MangaResult -> result.manga.genres
                    }
                }.distinct().sorted().take(10)  // Limit to top 10 genres for UI space
            }

            // Extract available years from current filtered results
            val availableYears = remember(filteredResults) {
                filteredResults.mapNotNull { result ->
                    when (result) {
                        is SearchResult.VideoResult -> result.video.year
                        is SearchResult.MangaResult -> null
                    }
                }.distinct().sortedDescending().take(10)  // Last 10 years
            }

            // Show genre/year filters only when search has results
            if (uiState.query.isNotBlank() && filteredResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                GenreFilterRow(
                    availableGenres = availableGenres,
                    selectedGenres = uiState.selectedGenres,
                    onToggleGenre = { viewModel.toggleGenreFilter(it) }
                )

                YearFilterRow(
                    availableYears = availableYears,
                    selectedYear = uiState.selectedYear,
                    onSetYear = { viewModel.setYearFilter(it) }
                )
            }

            // Clear filters button (if any non-ALL filters active)
            if (uiState.selectedFilter != SearchFilter.ALL ||
                uiState.selectedGenres.isNotEmpty() ||
                uiState.selectedYear != null
            ) {
                TextButton(
                    onClick = { viewModel.clearAllFilters() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.FilterAltOff, "Clear filters")
                    Spacer(Modifier.width(4.dp))
                    Text("Clear all filters")
                }
            }
        }

        // Content
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    // Loading
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Searching...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                searchQuery.isEmpty() -> {
                    // Empty state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                            Text(
                                "Find your next watch",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                "Search across all sources",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                uiState.error != null && filteredResults.isEmpty() -> {
                    // Error / No results
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "No results found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                "Try a different search term",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                else -> {
                    // Results
                    SectionedResultsList(
                        results = filteredResults,
                        uiState = uiState,
                        onVideoClick = { result ->
                            val encodedId = URLEncoder.encode(result.video.id, "UTF-8")
                            navController.navigate("video/${result.video.sourceId}/$encodedId")
                        },
                        onMangaClick = { result ->
                            val encodedId = URLEncoder.encode(result.manga.id, "UTF-8")
                            navController.navigate("manga/${result.manga.sourceId}/$encodedId")
                        },
                        onClearFilters = { viewModel.clearAllFilters() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoResultCard(
    video: Video,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Poster
            Surface(
                modifier = Modifier
                    .width(90.dp)
                    .aspectRatio(0.67f)
                    .clip(RoundedCornerShape(12.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                if (video.posterUrl != null) {
                    AsyncImage(
                        model = video.posterUrl,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            video.title.take(2).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Type Badge
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (video.type) {
                            VideoType.MOVIE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            VideoType.TV_SERIES -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                            VideoType.ANIME -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (video.type) {
                                    VideoType.TV_SERIES -> Icons.Default.Tv
                                    else -> Icons.Default.Movie
                                },
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = when (video.type) {
                                    VideoType.MOVIE -> MaterialTheme.colorScheme.primary
                                    VideoType.TV_SERIES -> MaterialTheme.colorScheme.secondary
                                    VideoType.ANIME -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Text(
                                text = when (video.type) {
                                    VideoType.MOVIE -> "Movie"
                                    VideoType.TV_SERIES -> "TV Show"
                                    VideoType.ANIME -> "Anime"
                                    else -> "Video"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when (video.type) {
                                    VideoType.MOVIE -> MaterialTheme.colorScheme.primary
                                    VideoType.TV_SERIES -> MaterialTheme.colorScheme.secondary
                                    VideoType.ANIME -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    video.year?.let { year ->
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                // Title
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Rating & Source
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    video.rating?.let { rating ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "★",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFFFB800)
                            )
                            Text(
                                String.format("%.1f", rating),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Text(
                        text = video.sourceId.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                // Description preview
                video.description?.let { desc ->
                    if (desc.isNotBlank()) {
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaResultCard(
    manga: Manga,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cover
            Surface(
                modifier = Modifier
                    .width(90.dp)
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(12.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                if (manga.coverUrl != null) {
                    AsyncImage(
                        model = manga.coverUrl,
                        contentDescription = manga.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            manga.title.take(2).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Type Badge
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "Manga",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    manga.status.name.takeIf { it != "UNKNOWN" }?.let { status ->
                        Text(
                            text = status.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                // Title
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Author & Source
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    manga.author?.let { author ->
                        Text(
                            text = author,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    Text(
                        text = manga.sourceId.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                // Description preview
                manga.description?.let { desc ->
                    if (desc.isNotBlank()) {
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchHistoryDropdown(
    searchHistory: List<SearchHistoryEntity>,
    isExpanded: Boolean,
    onDismiss: () -> Unit,
    onHistoryItemClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    DropdownMenu(
        expanded = isExpanded,
        onDismissRequest = onDismiss,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        searchHistory.forEach { historyItem ->
            DropdownMenuItem(
                text = {
                    Text(
                        historyItem.query,
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                onClick = { onHistoryItemClick(historyItem.query) },
                leadingIcon = {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { onDeleteClick(historyItem.query) }
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }

        // Clear all history option
        if (searchHistory.isNotEmpty()) {
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        "Clear all history",
                        color = MaterialTheme.colorScheme.error
                    )
                },
                onClick = onClearAll,
                leadingIcon = {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }
}
