package com.omnistream.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.omnistream.data.local.DownloadEntity
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    navController: NavController,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsState()

    var deleteTarget by remember { mutableStateOf<DownloadEntity?>(null) }

    // Delete confirmation dialog
    deleteTarget?.let { download ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Download?") },
            text = {
                Text("This will remove the download and delete all downloaded files.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDownload(download)
                        deleteTarget = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    "Downloads",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        if (downloads.isEmpty()) {
            EmptyDownloadsState()
        } else {
            val active = downloads.filter { it.status == "downloading" || it.status == "pending" }
            val paused = downloads.filter { it.status == "paused" }
            val completed = downloads.filter { it.status == "completed" }
            val failed = downloads.filter { it.status == "failed" }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (active.isNotEmpty()) {
                    item {
                        SectionHeader("Active")
                    }
                    items(active, key = { it.id }) { download ->
                        DownloadItemCard(
                            download = download,
                            onPause = { viewModel.pauseDownload(download.id) },
                            onResume = { },
                            onRetry = { },
                            onDelete = { deleteTarget = download },
                            onItemClick = { navigateToContent(navController, download) }
                        )
                    }
                }

                if (paused.isNotEmpty()) {
                    item {
                        SectionHeader("Paused")
                    }
                    items(paused, key = { it.id }) { download ->
                        DownloadItemCard(
                            download = download,
                            onPause = { },
                            onResume = { viewModel.resumeDownload(download.id) },
                            onRetry = { },
                            onDelete = { deleteTarget = download },
                            onItemClick = { navigateToContent(navController, download) }
                        )
                    }
                }

                if (completed.isNotEmpty()) {
                    item {
                        SectionHeader("Completed")
                    }
                    items(completed, key = { it.id }) { download ->
                        DownloadItemCard(
                            download = download,
                            onPause = { },
                            onResume = { },
                            onRetry = { },
                            onDelete = { deleteTarget = download },
                            onItemClick = { navigateToContent(navController, download) }
                        )
                    }
                }

                if (failed.isNotEmpty()) {
                    item {
                        SectionHeader("Failed")
                    }
                    items(failed, key = { it.id }) { download ->
                        DownloadItemCard(
                            download = download,
                            onPause = { },
                            onResume = { },
                            onRetry = { viewModel.retryDownload(download) },
                            onDelete = { deleteTarget = download },
                            onItemClick = { navigateToContent(navController, download) }
                        )
                    }
                }
            }
        }
    }
}

private fun navigateToContent(navController: NavController, download: DownloadEntity) {
    if (download.status != "completed") return
    val encodedContentId = URLEncoder.encode(download.contentId, "UTF-8")
    val encodedTitle = URLEncoder.encode(download.title, "UTF-8")
    val encodedCover = URLEncoder.encode(download.coverUrl ?: "", "UTF-8")

    when (download.contentType) {
        "manga" -> {
            val chapterId = URLEncoder.encode(download.chapterId ?: "", "UTF-8")
            navController.navigate(
                "reader/${download.sourceId}/$encodedContentId/$chapterId/$encodedTitle/$encodedCover"
            )
        }
        "video" -> {
            val episodeId = URLEncoder.encode(download.episodeId ?: "", "UTF-8")
            navController.navigate(
                "player/${download.sourceId}/$encodedContentId/$episodeId/$encodedTitle/$encodedCover"
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun DownloadItemCard(
    download: DownloadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onItemClick: () -> Unit
) {
    Card(
        onClick = onItemClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover thumbnail
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (download.coverUrl != null) {
                    AsyncImage(
                        model = download.coverUrl,
                        contentDescription = download.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = if (download.contentType == "manga")
                            Icons.AutoMirrored.Filled.MenuBook else Icons.Default.VideoFile,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title + status + progress
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                StatusText(download.status)

                if (download.status == "downloading" || download.status == "paused") {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { download.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = if (download.status == "paused")
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    if (download.status == "downloading") {
                        Text(
                            text = "${(download.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Action buttons
            Row {
                when (download.status) {
                    "downloading", "pending" -> {
                        IconButton(onClick = onPause) {
                            Icon(
                                Icons.Default.Pause,
                                contentDescription = "Pause",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                    "paused" -> {
                        IconButton(onClick = onResume) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Resume",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    "failed" -> {
                        IconButton(onClick = onRetry) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusText(status: String) {
    val (text, color) = when (status) {
        "downloading" -> "Downloading..." to MaterialTheme.colorScheme.primary
        "pending" -> "Pending" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        "paused" -> "Paused" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        "completed" -> "Completed" to MaterialTheme.colorScheme.tertiary
        "failed" -> "Failed" to MaterialTheme.colorScheme.error
        else -> status to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color
    )
}

@Composable
private fun EmptyDownloadsState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "No Downloads",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                "Downloaded content will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}
