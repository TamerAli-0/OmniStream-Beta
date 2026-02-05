package com.omnistream.ui.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnistream.domain.model.Chapter

/**
 * Saikou-style chapter list bottom sheet
 * Beautiful, fast, with current chapter highlighted
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterListSheet(
    chapters: List<Chapter>,
    currentChapterId: String,
    onChapterSelected: (Chapter) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll to current chapter when opened
    LaunchedEffect(chapters, currentChapterId) {
        val currentIndex = chapters.indexOfFirst { it.id == currentChapterId }
        if (currentIndex >= 0) {
            listState.scrollToItem(currentIndex)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Chapters",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "${chapters.size} chapters",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            // Chapter list
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(chapters) { chapter ->
                    ChapterListItem(
                        chapter = chapter,
                        isCurrentChapter = chapter.id == currentChapterId,
                        onClick = {
                            onChapterSelected(chapter)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterListItem(
    chapter: Chapter,
    isCurrentChapter: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isCurrentChapter)
            MaterialTheme.colorScheme.primaryContainer
        else
            Color.Transparent,
        tonalElevation = if (isCurrentChapter) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chapter number badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isCurrentChapter)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    if (chapter.number % 1f == 0f)
                        chapter.number.toInt().toString()
                    else
                        chapter.number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrentChapter)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // Chapter title
            Text(
                chapter.title ?: "Chapter ${chapter.number}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrentChapter) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrentChapter)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            // Current indicator
            if (isCurrentChapter) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Currently reading",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
