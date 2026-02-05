package com.omnistream.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.omnistream.domain.model.Chapter
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaDetailScreen(
    navController: NavController,
    sourceId: String,
    mangaId: String,
    viewModel: MangaDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0a0a0a))
    ) {
        TopAppBar(
            title = {
                if (uiState.isSelectionMode) {
                    Text("${uiState.selectedChapters.size} selected")
                } else {
                    Text(uiState.manga?.title ?: "Loading...")
                }
            },
            navigationIcon = {
                if (uiState.isSelectionMode) {
                    IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(Icons.Default.Close, "Cancel selection")
                    }
                } else {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            },
            actions = {
                if (uiState.isSelectionMode) {
                    // No extra actions needed; batch download button is floating
                } else {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            if (uiState.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite"
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            uiState.error ?: "Error loading manga",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.retryLoad() }) {
                            Text("Retry")
                        }
                    }
                }
            }

            uiState.manga != null -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = if (uiState.isSelectionMode && uiState.selectedChapters.isNotEmpty()) 80.dp else 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header with cover and info
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Cover image
                                Surface(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .aspectRatio(0.7f)
                                        .clip(RoundedCornerShape(12.dp)),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    uiState.manga?.coverUrl?.let { url ->
                                        AsyncImage(
                                            model = url,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                // Info
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = uiState.manga?.title ?: "",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )

                                    uiState.manga?.author?.let { author ->
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Author: $author",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }

                                    uiState.manga?.status?.let { status ->
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Status: ${status.name}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }

                                    if (uiState.manga?.genres?.isNotEmpty() == true) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = uiState.manga?.genres?.joinToString(", ") ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        // Description
                        uiState.manga?.description?.let { description ->
                            item {
                                Text(
                                    text = "Description",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }

                        // Continue Reading button
                        uiState.savedProgress?.let { progress ->
                            item {
                                Button(
                                    onClick = {
                                        val encodedMangaId = URLEncoder.encode(mangaId, "UTF-8")
                                        val encodedChapterId = URLEncoder.encode(progress.chapterId ?: "", "UTF-8")
                                        val encodedTitle = URLEncoder.encode(uiState.manga?.title ?: "", "UTF-8")
                                        val encodedCover = URLEncoder.encode(uiState.manga?.coverUrl ?: "", "UTF-8")
                                        navController.navigate("reader/$sourceId/$encodedMangaId/$encodedChapterId/$encodedTitle/$encodedCover")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Continue Reading · Ch. ${progress.chapterIndex + 1}")
                                }
                            }
                        }

                        // Chapters header with sort toggle
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Chapters (${uiState.chapters.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { viewModel.toggleChapterSort() }) {
                                    Icon(
                                        if (uiState.chaptersAscending) Icons.Default.KeyboardArrowDown
                                        else Icons.Default.KeyboardArrowUp,
                                        contentDescription = if (uiState.chaptersAscending) "Sort Descending" else "Sort Ascending"
                                    )
                                }
                            }
                        }

                        // Chapter list
                        items(uiState.chapters, key = { it.id }) { chapter ->
                            val isRead = uiState.readUpToChapterNumber >= 0f && chapter.number <= uiState.readUpToChapterNumber
                            val downloadStatus = uiState.downloadedChapters[chapter.id]

                            ChapterItem(
                                chapter = chapter,
                                isSelectionMode = uiState.isSelectionMode,
                                isSelected = chapter.id in uiState.selectedChapters,
                                isDownloading = chapter.id in uiState.downloadingChapterIds,
                                isRead = isRead,
                                downloadStatus = downloadStatus,
                                onClick = {
                                    if (uiState.isSelectionMode) {
                                        viewModel.toggleChapterSelection(chapter.id)
                                    } else {
                                        val encodedMangaId = URLEncoder.encode(mangaId, "UTF-8")
                                        val encodedChapterId = URLEncoder.encode(chapter.id, "UTF-8")
                                        val encodedTitle = URLEncoder.encode(uiState.manga?.title ?: "", "UTF-8")
                                        val encodedCover = URLEncoder.encode(uiState.manga?.coverUrl ?: "", "UTF-8")
                                        navController.navigate("reader/$sourceId/$encodedMangaId/$encodedChapterId/$encodedTitle/$encodedCover")
                                    }
                                },
                                onLongClick = {
                                    if (!uiState.isSelectionMode) {
                                        viewModel.toggleSelectionMode()
                                        viewModel.toggleChapterSelection(chapter.id)
                                    }
                                },
                                onDownloadClick = {
                                    viewModel.downloadChapter(chapter)
                                }
                            )
                        }
                    }

                    // Floating batch download button - stays visible while scrolling
                    androidx.compose.animation.AnimatedVisibility(
                        visible = uiState.isSelectionMode && uiState.selectedChapters.isNotEmpty(),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shadowElevation = 8.dp,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Button(
                                onClick = { viewModel.downloadSelectedChapters() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary
                                )
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download Selected (${uiState.selectedChapters.size})")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterItem(
    chapter: Chapter,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isDownloading: Boolean,
    isRead: Boolean,
    downloadStatus: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    val textAlpha = if (isRead) 0.4f else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = Color.Black.copy(alpha = 0.4f)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> Color(0xFF2a2a2a)
                isRead -> Color(0xFF1a1a1a).copy(alpha = 0.5f)
                else -> Color(0xFF1a1a1a)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox in selection mode
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                val chapterNumStr = if (chapter.number % 1f == 0f) chapter.number.toInt().toString() else chapter.number.toString()
                val chapterTitle = chapter.title?.let { title ->
                    // Don't repeat "Chapter X" if title is just that
                    if (title.equals("Chapter ${chapterNumStr}", ignoreCase = true) ||
                        title.equals("Chapter ${chapter.number}", ignoreCase = true) ||
                        title.equals("Ch. ${chapterNumStr}", ignoreCase = true)
                    ) null else title
                }
                Text(
                    text = "Chapter $chapterNumStr" + (chapterTitle?.let { ": $it" } ?: ""),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha)
                )

                chapter.scanlator?.let { scanlator ->
                    Text(
                        text = scanlator,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f * textAlpha)
                    )
                }
            }

            chapter.uploadDate?.let { date ->
                Text(
                    text = formatDate(date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f * textAlpha),
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            // Download status icon (not in selection mode)
            if (!isSelectionMode) {
                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier.size(36.dp),
                    enabled = downloadStatus == null && !isDownloading
                ) {
                    when {
                        downloadStatus == "completed" -> Icon(
                            Icons.Default.DownloadDone,
                            contentDescription = "Downloaded",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        isDownloading || downloadStatus == "downloading" || downloadStatus == "pending" -> Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Queued",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        else -> Icon(
                            Icons.Outlined.Download,
                            contentDescription = "Download",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha)
                        )
                    }
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val days = diff / (1000 * 60 * 60 * 24)

    return when {
        days < 1 -> "Today"
        days < 2 -> "Yesterday"
        days < 7 -> "$days days ago"
        days < 30 -> "${days / 7} weeks ago"
        days < 365 -> "${days / 30} months ago"
        else -> "${days / 365} years ago"
    }
}
