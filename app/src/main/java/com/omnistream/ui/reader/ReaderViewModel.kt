package com.omnistream.ui.reader

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnistream.data.local.DownloadDao
import com.omnistream.data.local.WatchHistoryEntity
import com.omnistream.data.repository.WatchHistoryRepository
import com.omnistream.domain.model.Chapter
import com.omnistream.domain.model.Manga
import com.omnistream.domain.model.Page
import com.omnistream.source.SourceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val watchHistoryRepository: WatchHistoryRepository,
    private val readChaptersRepository: com.omnistream.data.repository.ReadChaptersRepository,
    private val downloadDao: DownloadDao,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sourceId: String = savedStateHandle["sourceId"] ?: ""
    private val mangaId: String = java.net.URLDecoder.decode(savedStateHandle["mangaId"] ?: "", "UTF-8")
    private var currentChapterId: String = java.net.URLDecoder.decode(savedStateHandle["chapterId"] ?: "", "UTF-8")
    private val mangaTitle: String = savedStateHandle["title"] ?: ""
    private val coverUrl: String? = savedStateHandle["coverUrl"]

    private var chapterList: List<Chapter> = emptyList()
    private var currentChapterIndex: Int = -1
    private var autoSaveJob: Job? = null
    private var pendingRestoredPage: Int? = null

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        loadChapterListThenPages()
    }

    private fun loadChapterListThenPages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            android.util.Log.d("ReaderViewModel", "Loading chapter: sourceId=$sourceId, mangaId=$mangaId, chapterId=$currentChapterId")

            try {
                val source = sourceManager.getMangaSource(sourceId)
                    ?: throw Exception("Source not found: $sourceId")

                // Load chapter list for navigation
                if (chapterList.isEmpty()) {
                    try {
                        val mangaUrl = when (sourceId) {
                            "mangadex" -> "${source.baseUrl}/manga/$mangaId"
                            "asuracomic" -> "${source.baseUrl}/series/$mangaId"
                            else -> "${source.baseUrl}/manga/$mangaId"
                        }
                        val manga = Manga(
                            id = mangaId,
                            sourceId = sourceId,
                            title = "",
                            url = mangaUrl
                        )
                        chapterList = source.getChapters(manga).sortedBy { it.number }
                    } catch (e: Exception) {
                        android.util.Log.w("ReaderViewModel", "Could not load chapter list for navigation", e)
                    }
                }

                // Find current chapter index
                currentChapterIndex = chapterList.indexOfFirst { it.id == currentChapterId }

                // Update UI state with chapter list and manga title
                _uiState.value = _uiState.value.copy(
                    chapters = chapterList,
                    mangaTitle = mangaTitle.ifEmpty { null }
                )

                loadCurrentChapterPages(source)

                // After pages are loaded, check for saved progress to resume
                val savedProgress = watchHistoryRepository.getProgress(mangaId, sourceId)
                if (savedProgress != null && savedProgress.chapterId == currentChapterId) {
                    val pages = _uiState.value.pages
                    val savedPage = savedProgress.progressPosition.toInt()
                        .coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                    pendingRestoredPage = savedPage
                    _uiState.value = _uiState.value.copy(currentPage = savedPage, restoredPage = savedPage)
                }

            } catch (e: Exception) {
                android.util.Log.e("ReaderViewModel", "Failed to load chapter", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load chapter"
                )
            }
        }
    }

    private suspend fun loadCurrentChapterPages(source: com.omnistream.source.model.MangaSource) {
        // Check if chapter is downloaded for offline reading (query by fields, not reconstructed ID)
        val downloadEntity = downloadDao.getByChapter(sourceId, mangaId, currentChapterId)

        if (downloadEntity != null && downloadEntity.status == "completed") {
            // Use the filePath from the entity directly (it was set during download enqueue)
            val downloadDir = File(downloadEntity.filePath)
            android.util.Log.d("ReaderViewModel", "Checking offline dir: ${downloadDir.absolutePath} exists=${downloadDir.exists()}")

            if (downloadDir.exists()) {
                val localFiles = downloadDir.listFiles()
                    ?.filter { it.isFile && it.extension.lowercase() in listOf("jpg", "png", "webp", "gif", "jpeg") }
                    ?.sortedBy { it.name }

                android.util.Log.d("ReaderViewModel", "Found ${localFiles?.size ?: 0} local files")

                if (!localFiles.isNullOrEmpty()) {
                    val localPages = localFiles.mapIndexed { index, file ->
                        Page(index = index, imageUrl = file.toUri().toString())
                    }
                    android.util.Log.d("ReaderViewModel", "Loaded ${localPages.size} offline pages")

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        pages = localPages,
                        currentPage = 0,
                        chapterNumber = extractChapterNumber(currentChapterId),
                        referer = null,
                        isOffline = true,
                        hasPreviousChapter = currentChapterIndex > 0,
                        hasNextChapter = currentChapterIndex >= 0 && currentChapterIndex < chapterList.size - 1
                    )

                    startAutoSave()
                    return
                }
            }
            // If local files not found, fall through to network loading
            android.util.Log.w("ReaderViewModel", "Download marked complete but files not found at ${downloadDir.absolutePath}, falling back to network")
        }

        // Construct chapter URL based on source type
        val chapterUrl = when (sourceId) {
            "mangadex" -> "${source.baseUrl}/chapter/$currentChapterId"
            "asuracomic" -> "${source.baseUrl}/series/$mangaId/chapter/$currentChapterId"
            "manhuaplus" -> currentChapterId
            else -> "${source.baseUrl}/chapter/$currentChapterId"
        }

        val chapter = Chapter(
            id = currentChapterId,
            mangaId = mangaId,
            sourceId = sourceId,
            url = chapterUrl,
            number = extractChapterNumber(currentChapterId)
        )

        val pages = source.getPages(chapter)
        android.util.Log.d("ReaderViewModel", "Loaded ${pages.size} pages from network")

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            pages = pages,
            currentPage = 0,
            chapterNumber = chapter.number,
            referer = source.baseUrl,
            isOffline = false,
            hasPreviousChapter = currentChapterIndex > 0,
            hasNextChapter = currentChapterIndex >= 0 && currentChapterIndex < chapterList.size - 1
        )

        startAutoSave()
    }

    fun loadChapter(chapterId: String) {
        currentChapterId = chapterId
        currentChapterIndex = chapterList.indexOfFirst { it.id == chapterId }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, currentPage = 0, restoredPage = 0)
            try {
                val source = sourceManager.getMangaSource(sourceId)
                    ?: throw Exception("Source not found: $sourceId")
                loadCurrentChapterPages(source)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load chapter"
                )
            }
        }
    }

    fun goToPreviousChapter() {
        if (currentChapterIndex <= 0) return
        viewModelScope.launch { saveCurrentProgress() }
        currentChapterIndex--
        currentChapterId = chapterList[currentChapterIndex].id
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val source = sourceManager.getMangaSource(sourceId)
                    ?: throw Exception("Source not found: $sourceId")
                loadCurrentChapterPages(source)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load chapter"
                )
            }
        }
    }

    fun goToNextChapter() {
        if (currentChapterIndex < 0 || currentChapterIndex >= chapterList.size - 1) return
        viewModelScope.launch { saveCurrentProgress() }
        currentChapterIndex++
        currentChapterId = chapterList[currentChapterIndex].id
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val source = sourceManager.getMangaSource(sourceId)
                    ?: throw Exception("Source not found: $sourceId")
                loadCurrentChapterPages(source)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load chapter"
                )
            }
        }
    }

    private fun extractChapterNumber(chapterId: String): Float {
        return Regex("""(\d+(?:\.\d+)?)""").find(chapterId)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
    }

    fun setCurrentPage(page: Int) {
        _uiState.value = _uiState.value.copy(currentPage = page)
    }

    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(12_000) // 12 seconds
                saveCurrentProgress()
            }
        }
    }

    private suspend fun saveCurrentProgress() {
        val state = _uiState.value
        if (state.pages.isEmpty()) return

        // Get actual chapter number (not index)
        val currentChapterNumber = if (currentChapterIndex >= 0 && currentChapterIndex < chapterList.size) {
            chapterList[currentChapterIndex].number
        } else {
            state.chapterNumber
        }

        // Check if user reached the last page (mark as read)
        val isLastPage = state.currentPage >= state.pages.size - 1
        if (isLastPage) {
            // Mark chapter as read in Kotatsu-style tracking
            readChaptersRepository.markChapterAsRead(
                mangaId = mangaId,
                sourceId = sourceId,
                chapterId = currentChapterId,
                chapterNumber = currentChapterNumber,
                pagesRead = state.pages.size,
                totalPages = state.pages.size,
                syncToAniList = true
            )
        }

        watchHistoryRepository.upsert(
            WatchHistoryEntity(
                id = "$sourceId:$mangaId",
                contentId = mangaId,
                sourceId = sourceId,
                contentType = "manga",
                title = mangaTitle,
                coverUrl = coverUrl,
                chapterId = currentChapterId,
                chapterIndex = currentChapterNumber.toInt(), // Save chapter NUMBER, not index
                totalChapters = chapterList.size,
                progressPosition = state.currentPage.toLong(),
                totalDuration = state.pages.size.toLong(),
                progressPercentage = if (chapterList.isNotEmpty() && currentChapterIndex >= 0)
                    (currentChapterIndex + 1).toFloat() / chapterList.size
                else
                    (state.currentPage + 1).toFloat() / state.pages.size.coerceAtLeast(1),
                lastWatchedAt = System.currentTimeMillis(),
                isCompleted = chapterList.isNotEmpty() &&
                    currentChapterIndex == chapterList.size - 1 &&
                    state.currentPage >= state.pages.size - 1
            )
        )
    }

    fun saveOnExit() {
        // Called from DisposableEffect before ViewModel is cleared
        viewModelScope.launch { saveCurrentProgress() }
    }

    override fun onCleared() {
        super.onCleared()
        autoSaveJob?.cancel()
    }
}

data class ReaderUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val pages: List<Page> = emptyList(),
    val currentPage: Int = 0,
    val chapterNumber: Float = 0f,
    val referer: String? = null,
    val isOffline: Boolean = false,
    val hasPreviousChapter: Boolean = false,
    val hasNextChapter: Boolean = false,
    val restoredPage: Int? = null,
    val chapters: List<Chapter> = emptyList(),
    val mangaTitle: String? = null
)
