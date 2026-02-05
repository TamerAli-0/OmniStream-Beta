package com.omnistream.ui.detail

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnistream.data.local.DownloadDao
import com.omnistream.data.local.DownloadEntity
import com.omnistream.data.local.FavoriteDao
import com.omnistream.data.local.FavoriteEntity
import com.omnistream.data.local.WatchHistoryDao
import com.omnistream.data.local.WatchHistoryEntity
import com.omnistream.data.repository.DownloadRepository
import com.omnistream.data.repository.WatchHistoryRepository
import com.omnistream.domain.model.Chapter
import com.omnistream.domain.model.Manga
import com.omnistream.source.SourceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MangaDetailViewModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val favoriteDao: FavoriteDao,
    private val watchHistoryRepository: WatchHistoryRepository,
    private val downloadRepository: DownloadRepository,
    private val downloadDao: DownloadDao,
    private val watchHistoryDao: WatchHistoryDao,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sourceId: String = savedStateHandle["sourceId"] ?: ""
    private val mangaId: String = java.net.URLDecoder.decode(savedStateHandle["mangaId"] ?: "", "UTF-8")

    private val _uiState = MutableStateFlow(MangaDetailUiState())
    val uiState: StateFlow<MangaDetailUiState> = _uiState.asStateFlow()

    init {
        loadMangaDetails()
        observeFavoriteStatus()
        loadReadingProgress()
        observeDownloadedChapters()
    }

    private fun loadReadingProgress() {
        viewModelScope.launch {
            val saved = watchHistoryRepository.getProgress(mangaId, sourceId)
            if (saved != null && !saved.isCompleted) {
                _uiState.value = _uiState.value.copy(
                    savedProgress = saved,
                    readUpToChapterNumber = saved.chapterIndex.toFloat()
                )
            }
        }
    }

    private fun observeDownloadedChapters() {
        viewModelScope.launch {
            downloadDao.getAllDownloads().collect { downloads ->
                val downloadedIds = downloads
                    .filter { it.sourceId == sourceId && it.contentId == mangaId && it.contentType == "manga" }
                    .associate { it.chapterId to it.status }
                _uiState.value = _uiState.value.copy(downloadedChapters = downloadedIds)
            }
        }
    }

    fun toggleChapterSort() {
        val current = _uiState.value
        val newAscending = !current.chaptersAscending
        val sorted = if (newAscending) current.chapters.sortedBy { it.number }
                     else current.chapters.sortedByDescending { it.number }
        _uiState.value = current.copy(chapters = sorted, chaptersAscending = newAscending)
    }

    private fun observeFavoriteStatus() {
        viewModelScope.launch {
            favoriteDao.isFavorite(sourceId, mangaId).collect { isFav ->
                _uiState.value = _uiState.value.copy(isFavorite = isFav)
            }
        }
    }

    private fun loadMangaDetails() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            android.util.Log.d("MangaDetailViewModel", "Loading manga: sourceId=$sourceId, mangaId=$mangaId")

            try {
                val source = sourceManager.getMangaSource(sourceId)
                    ?: throw Exception("Source not found: $sourceId")

                // Construct URL based on source type
                val mangaUrl = when (sourceId) {
                    "mangadex" -> "${source.baseUrl}/manga/$mangaId"
                    "asuracomic" -> "${source.baseUrl}/series/$mangaId"
                    else -> "${source.baseUrl}/manga/$mangaId"
                }

                // Create initial manga object with ID
                val initialManga = Manga(
                    id = mangaId,
                    sourceId = sourceId,
                    title = "",
                    url = mangaUrl
                )

                // Get full details
                val manga = source.getDetails(initialManga)
                android.util.Log.d("MangaDetailViewModel", "Loaded manga: ${manga.title}")

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    manga = manga
                )

                // Load chapters
                loadChapters(source, manga)

            } catch (e: Exception) {
                android.util.Log.e("MangaDetailViewModel", "Failed to load manga", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load manga"
                )
            }
        }
    }

    fun retryLoad() {
        loadMangaDetails()
    }

    private suspend fun loadChapters(source: com.omnistream.source.model.MangaSource, manga: Manga) {
        try {
            val chapters = source.getChapters(manga)
            _uiState.value = _uiState.value.copy(chapters = chapters)
        } catch (e: Exception) {
            android.util.Log.e("MangaDetailViewModel", "Failed to load chapters", e)
            _uiState.value = _uiState.value.copy(
                chaptersError = e.message ?: "Failed to load chapters"
            )
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val manga = _uiState.value.manga ?: return@launch
            if (_uiState.value.isFavorite) {
                favoriteDao.removeFavorite(sourceId, mangaId)
            } else {
                favoriteDao.addFavorite(
                    FavoriteEntity(
                        id = "$sourceId:$mangaId",
                        contentId = mangaId,
                        sourceId = sourceId,
                        contentType = "manga",
                        title = manga.title,
                        coverUrl = manga.coverUrl
                    )
                )
            }
        }
    }

    // --- Download methods ---

    private fun sanitizeForPath(input: String): String {
        return input.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(120)
    }

    fun downloadChapter(chapter: Chapter) {
        val manga = _uiState.value.manga ?: return
        val safeChapterId = sanitizeForPath(chapter.id)
        val downloadId = "manga_${sourceId}_${sanitizeForPath(mangaId)}_$safeChapterId"
        val filePath = "${context.filesDir}/downloads/manga/$sourceId/${sanitizeForPath(mangaId)}/$safeChapterId"

        val entity = DownloadEntity(
            id = downloadId,
            contentId = mangaId,
            sourceId = sourceId,
            contentType = "manga",
            title = "${manga.title} - Ch. ${chapter.number}",
            coverUrl = manga.coverUrl,
            chapterId = chapter.id,
            filePath = filePath,
            status = "pending"
        )

        _uiState.value = _uiState.value.copy(
            downloadingChapterIds = _uiState.value.downloadingChapterIds + chapter.id
        )

        viewModelScope.launch {
            try {
                downloadRepository.enqueueDownload(entity)
                Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("MangaDetailViewModel", "Failed to enqueue download", e)
                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun downloadSelectedChapters() {
        val chapters = _uiState.value.chapters
        val selectedIds = _uiState.value.selectedChapters
        val selected = chapters.filter { it.id in selectedIds }
        selected.forEach { downloadChapter(it) }
        clearSelection()
    }

    fun toggleSelectionMode() {
        val current = _uiState.value
        _uiState.value = current.copy(
            isSelectionMode = !current.isSelectionMode,
            selectedChapters = if (current.isSelectionMode) emptySet() else current.selectedChapters
        )
    }

    fun toggleChapterSelection(chapterId: String) {
        val current = _uiState.value.selectedChapters
        _uiState.value = _uiState.value.copy(
            selectedChapters = if (chapterId in current) current - chapterId else current + chapterId
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            isSelectionMode = false,
            selectedChapters = emptySet()
        )
    }
}

data class MangaDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val chaptersError: String? = null,
    val manga: Manga? = null,
    val chapters: List<Chapter> = emptyList(),
    val isFavorite: Boolean = false,
    val chaptersAscending: Boolean = true,
    val savedProgress: WatchHistoryEntity? = null,
    val readUpToChapterNumber: Float = -1f,
    val downloadedChapters: Map<String?, String> = emptyMap(),
    val selectedChapters: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val downloadingChapterIds: Set<String> = emptySet()
)
