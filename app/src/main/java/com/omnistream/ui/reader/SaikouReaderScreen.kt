package com.omnistream.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.omnistream.domain.model.Page
import com.omnistream.ui.reader.components.*
import kotlinx.coroutines.launch

/**
 * Completely redesigned manga reader with Saikou's beautiful UI
 * - Multiple reading modes (Vertical, LTR, RTL, Dual Page)
 * - Beautiful controls with smooth animations
 * - Chapter list bottom sheet
 * - Settings menu
 * - Page seekbar
 * - Saikou pink/violet styling
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SaikouReaderScreen(
    navController: NavController,
    sourceId: String,
    mangaId: String,
    chapterId: String,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // UI state
    var showControls by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showReadingModeSelector by remember { mutableStateOf(false) }

    // Reading preferences
    var readingMode by remember { mutableStateOf(ReadingMode.VERTICAL_CONTINUOUS) }
    var keepScreenOn by remember { mutableStateOf(true) }
    var showPageNumber by remember { mutableStateOf(true) }

    // Pager state for horizontal modes
    val pagerState = rememberPagerState(pageCount = { uiState.pages.size })

    // Vertical scroll state
    val verticalListState = rememberLazyListState()

    // Sync page tracking
    LaunchedEffect(pagerState.currentPage) {
        if (readingMode != ReadingMode.VERTICAL_CONTINUOUS) {
            viewModel.setCurrentPage(pagerState.currentPage)
        }
    }

    LaunchedEffect(verticalListState.firstVisibleItemIndex) {
        if (readingMode == ReadingMode.VERTICAL_CONTINUOUS) {
            viewModel.setCurrentPage(verticalListState.firstVisibleItemIndex)
        }
    }

    // Restore saved page
    LaunchedEffect(uiState.restoredPage, readingMode) {
        uiState.restoredPage?.let { page ->
            if (page > 0) {
                if (readingMode == ReadingMode.VERTICAL_CONTINUOUS) {
                    verticalListState.scrollToItem(page)
                } else {
                    pagerState.scrollToPage(page)
                }
            }
        }
    }

    // Save progress on exit
    DisposableEffect(Unit) {
        onDispose { viewModel.saveOnExit() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls }
                )
            }
    ) {
        when {
            uiState.isLoading -> {
                LoadingState()
            }

            uiState.error != null -> {
                ErrorState(error = uiState.error ?: "Unknown error")
            }

            else -> {
                // Main reader content based on mode
                when (readingMode) {
                    ReadingMode.WEBTOON,
                    ReadingMode.VERTICAL_CONTINUOUS -> {
                        VerticalReader(
                            pages = uiState.pages,
                            baseUrl = uiState.referer,
                            isOffline = uiState.isOffline,
                            listState = verticalListState
                        )
                    }

                    ReadingMode.PAGED_VERTICAL -> {
                        VerticalReader(
                            pages = uiState.pages,
                            baseUrl = uiState.referer,
                            isOffline = uiState.isOffline,
                            listState = verticalListState
                        )
                    }

                    ReadingMode.HORIZONTAL_LTR -> {
                        HorizontalReader(
                            pages = uiState.pages,
                            baseUrl = uiState.referer,
                            isOffline = uiState.isOffline,
                            pagerState = pagerState,
                            reverseLayout = false
                        )
                    }

                    ReadingMode.HORIZONTAL_RTL -> {
                        HorizontalReader(
                            pages = uiState.pages,
                            baseUrl = uiState.referer,
                            isOffline = uiState.isOffline,
                            pagerState = pagerState,
                            reverseLayout = true
                        )
                    }

                    ReadingMode.DUAL_PAGE -> {
                        DualPageReader(
                            pages = uiState.pages,
                            baseUrl = uiState.referer,
                            isOffline = uiState.isOffline,
                            pagerState = pagerState
                        )
                    }

                    ReadingMode.FIT_WIDTH,
                    ReadingMode.FIT_HEIGHT -> {
                        HorizontalReader(
                            pages = uiState.pages,
                            baseUrl = uiState.referer,
                            isOffline = uiState.isOffline,
                            pagerState = pagerState,
                            reverseLayout = false
                        )
                    }
                }

                // Page number overlay (floating)
                if (showPageNumber && !showControls && uiState.pages.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Text(
                            "${uiState.currentPage + 1} / ${uiState.pages.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Saikou-style controls overlay
        SaikouReaderControls(
            visible = showControls,
            title = uiState.mangaTitle ?: "",
            chapterNumber = uiState.chapterNumber,
            currentPage = uiState.currentPage,
            totalPages = uiState.pages.size,
            readingMode = readingMode,
            hasPreviousChapter = uiState.hasPreviousChapter,
            hasNextChapter = uiState.hasNextChapter,
            onClose = { navController.popBackStack() },
            onPreviousChapter = { viewModel.goToPreviousChapter() },
            onNextChapter = { viewModel.goToNextChapter() },
            onPageSeek = { page ->
                scope.launch {
                    if (readingMode == ReadingMode.VERTICAL_CONTINUOUS) {
                        verticalListState.animateScrollToItem(page)
                    } else {
                        pagerState.animateScrollToPage(page)
                    }
                }
            },
            onShowChapterList = { showChapterList = true },
            onShowSettings = { showSettings = true }
        )
    }

    // Chapter list bottom sheet
    if (showChapterList && uiState.chapters.isNotEmpty()) {
        ChapterListSheet(
            chapters = uiState.chapters,
            currentChapterId = chapterId,
            onChapterSelected = { chapter ->
                viewModel.loadChapter(chapter.id)
            },
            onDismiss = { showChapterList = false }
        )
    }

    // Settings bottom sheet
    if (showSettings) {
        ReaderSettingsSheet(
            readingMode = readingMode,
            keepScreenOn = keepScreenOn,
            showPageNumber = showPageNumber,
            onReadingModeClick = { showReadingModeSelector = true },
            onKeepScreenOnToggle = { keepScreenOn = it },
            onShowPageNumberToggle = { showPageNumber = it },
            onDismiss = { showSettings = false }
        )
    }

    // Reading mode selector dialog
    if (showReadingModeSelector) {
        ReadingModeSelector(
            currentMode = readingMode,
            onModeSelected = { readingMode = it },
            onDismiss = { showReadingModeSelector = false }
        )
    }
}

@Composable
private fun VerticalReader(
    pages: List<Page>,
    baseUrl: String?,
    isOffline: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(pages) { page ->
            ReaderPageImage(page = page, baseUrl = baseUrl, isOffline = isOffline)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HorizontalReader(
    pages: List<Page>,
    baseUrl: String?,
    isOffline: Boolean,
    pagerState: androidx.compose.foundation.pager.PagerState,
    reverseLayout: Boolean
) {
    HorizontalPager(
        state = pagerState,
        reverseLayout = reverseLayout,
        modifier = Modifier.fillMaxSize()
    ) { pageIndex ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            ReaderPageImage(
                page = pages[pageIndex],
                baseUrl = baseUrl,
                isOffline = isOffline,
                fitWidth = false
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DualPageReader(
    pages: List<Page>,
    baseUrl: String?,
    isOffline: Boolean,
    pagerState: androidx.compose.foundation.pager.PagerState
) {
    // TODO: Implement dual page layout
    // For now, fall back to horizontal
    HorizontalReader(pages, baseUrl, isOffline, pagerState, false)
}

@Composable
private fun ReaderPageImage(
    page: Page,
    baseUrl: String?,
    isOffline: Boolean,
    fitWidth: Boolean = true
) {
    val context = LocalContext.current

    val imageRequest = ImageRequest.Builder(context)
        .data(page.imageUrl)
        .addHeader("Referer", page.referer ?: baseUrl ?: "")
        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        .crossfade(true)
        .allowHardware(!isOffline)
        .build()

    SubcomposeAsyncImage(
        model = imageRequest,
        contentDescription = "Page ${page.index + 1}",
        contentScale = if (fitWidth) ContentScale.FillWidth else ContentScale.Fit,
        modifier = if (fitWidth) Modifier.fillMaxWidth() else Modifier.fillMaxSize(),
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Failed to load page ${page.index + 1}",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                "Loading chapter...",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ErrorState(error: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
