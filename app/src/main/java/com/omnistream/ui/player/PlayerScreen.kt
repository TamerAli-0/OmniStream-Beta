@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.omnistream.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavController
import com.omnistream.ui.player.components.PlayerControls
import com.omnistream.ui.player.components.PlayerGestureHandler
import com.omnistream.ui.player.components.ResizeMode
import com.omnistream.ui.player.components.VideoPlayer
import com.omnistream.ui.player.components.VideoPlayerState
import com.omnistream.ui.player.dialogs.ResizeModeSheet
import com.omnistream.ui.player.dialogs.SourceSelectionSheet
import com.omnistream.ui.player.dialogs.SpeedSelectionSheet
import com.omnistream.ui.player.dialogs.SubtitleSelectionSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    navController: NavController,
    sourceId: String,
    videoId: String,
    episodeId: String,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsState()

    // Player state
    var playerState by remember { mutableStateOf(VideoPlayerState()) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    // UI state
    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var hideControlsJob by remember { mutableStateOf<Job?>(null) }

    // Dialog state
    var showSourceSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showResizeSheet by remember { mutableStateOf(false) }

    // Auto-hide controls
    fun resetHideTimer() {
        hideControlsJob?.cancel()
        if (showControls && !isLocked && playerState.isPlaying) {
            hideControlsJob = scope.launch {
                delay(3000)
                showControls = false
            }
        }
    }

    // Toggle controls visibility
    fun toggleControls() {
        showControls = !showControls
        if (showControls) {
            resetHideTimer()
        }
    }

    // Enter fullscreen immersive mode
    DisposableEffect(Unit) {
        activity?.let { act ->
            // Force landscape
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

            // Keep screen on
            act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Immersive mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                act.window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                act.window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
            }
        }

        onDispose {
            // Save video progress before leaving
            exoPlayer?.let { player ->
                if (player.duration > 0) {
                    viewModel.saveVideoProgress(player.currentPosition, player.duration)
                }
            }

            activity?.let { act ->
                // Restore orientation
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

                // Remove keep screen on
                act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                // Restore system UI
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    act.window.insetsController?.show(WindowInsets.Type.systemBars())
                } else {
                    @Suppress("DEPRECATION")
                    act.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                }
            }
        }
    }

    // Reset hide timer when playing state changes
    LaunchedEffect(playerState.isPlaying) {
        if (playerState.isPlaying && showControls) {
            resetHideTimer()
        }
    }

    // Resume dialog
    if (uiState.showResumeDialog && uiState.savedPosition != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.dismissResumeDialog() },
            title = { androidx.compose.material3.Text("Resume Playback") },
            text = {
                val minutes = (uiState.savedPosition!! / 1000 / 60).toInt()
                val seconds = (uiState.savedPosition!! / 1000 % 60).toInt()
                androidx.compose.material3.Text("Resume from ${minutes}:${String.format("%02d", seconds)}?")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    exoPlayer?.seekTo(uiState.savedPosition!!)
                    viewModel.dismissResumeDialog()
                }) { androidx.compose.material3.Text("Resume") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.startFromBeginning()
                }) { androidx.compose.material3.Text("Start Over") }
            }
        )
    }

    // Seek to saved position after user clicks Resume
    LaunchedEffect(uiState.savedPosition, uiState.showResumeDialog, exoPlayer) {
        val pos = uiState.savedPosition
        if (pos != null && !uiState.showResumeDialog && exoPlayer != null) {
            exoPlayer?.seekTo(pos)
        }
    }

    // Back handler
    BackHandler {
        if (isLocked) {
            // Do nothing when locked
        } else if (showSourceSheet || showSpeedSheet || showSubtitleSheet || showResizeSheet) {
            showSourceSheet = false
            showSpeedSheet = false
            showSubtitleSheet = false
            showResizeSheet = false
        } else {
            exoPlayer?.let { player ->
                if (player.duration > 0) {
                    viewModel.saveVideoProgress(player.currentPosition, player.duration)
                }
            }
            navController.popBackStack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Show loading, error, or player based on state
        when {
            uiState.isLoading -> {
                // Loading state
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = androidx.compose.ui.graphics.Color.White
                        )
                        androidx.compose.material3.Text(
                            "Loading video...",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            uiState.error != null -> {
                // Error state
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        androidx.compose.material3.Text(
                            "Failed to load video",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                        )
                        androidx.compose.material3.Text(
                            uiState.error ?: "Unknown error",
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        androidx.compose.material3.TextButton(
                            onClick = { navController.popBackStack() }
                        ) {
                            androidx.compose.material3.Text(
                                "Go Back",
                                color = androidx.compose.ui.graphics.Color.White
                            )
                        }
                    }
                }
            }

            uiState.links.isEmpty() -> {
                // No links found
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        androidx.compose.material3.Text(
                            "No video sources found",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                        )
                        androidx.compose.material3.Text(
                            "This content may not be available from this source",
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        androidx.compose.material3.TextButton(
                            onClick = { navController.popBackStack() }
                        ) {
                            androidx.compose.material3.Text(
                                "Go Back",
                                color = androidx.compose.ui.graphics.Color.White
                            )
                        }
                    }
                }
            }

            else -> {
                // Video player
                VideoPlayer(
                    link = uiState.selectedLink,
                    state = playerState,
                    onStateChange = { newState ->
                        playerState = newState
                    },
                    onPlayerReady = { player ->
                        exoPlayer = player
                    }
                )

                // Gesture handler (always active, handles taps and swipes)
        PlayerGestureHandler(
            onToggleControls = { toggleControls() },
            onSeek = { position ->
                exoPlayer?.seekTo(position)
                playerState = playerState.copy(currentPosition = position)
            },
            onSeekDelta = { seconds ->
                exoPlayer?.let { player ->
                    val duration = player.duration.takeIf { it > 0 } ?: return@let
                    val newPosition = (player.currentPosition + seconds * 1000L)
                        .coerceIn(0, duration)
                    player.seekTo(newPosition)
                    playerState = playerState.copy(currentPosition = newPosition)
                }
            },
            currentPosition = playerState.currentPosition,
            duration = playerState.duration,
            isLocked = isLocked
        )

        // Player controls overlay
        PlayerControls(
            visible = showControls,
            title = uiState.episodeTitle ?: "Playing",
            isPlaying = playerState.isPlaying,
            isBuffering = playerState.isBuffering,
            currentPosition = playerState.currentPosition,
            duration = playerState.duration,
            bufferedPosition = playerState.bufferedPosition,
            isLocked = isLocked,
            subtitlesEnabled = playerState.selectedSubtitleIndex >= 0,
            playbackSpeed = playerState.playbackSpeed,
            onBack = { navController.popBackStack() },
            onPlayPause = {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        player.pause()
                        viewModel.saveVideoProgress(player.currentPosition, player.duration)
                    } else {
                        player.play()
                    }
                }
                resetHideTimer()
            },
            onSeek = { position ->
                exoPlayer?.seekTo(position)
                playerState = playerState.copy(currentPosition = position)
                resetHideTimer()
            },
            onSeekDelta = { seconds ->
                exoPlayer?.let { player ->
                    val duration = player.duration.takeIf { it > 0 } ?: return@let
                    val newPosition = (player.currentPosition + seconds * 1000L)
                        .coerceIn(0, duration)
                    player.seekTo(newPosition)
                    playerState = playerState.copy(currentPosition = newPosition)
                }
                resetHideTimer()
            },
            onToggleLock = {
                isLocked = !isLocked
                if (isLocked) {
                    showControls = false
                } else {
                    showControls = true
                    resetHideTimer()
                }
            },
            onSourceClick = {
                showSourceSheet = true
                hideControlsJob?.cancel()
            },
            onSubtitlesClick = {
                showSubtitleSheet = true
                hideControlsJob?.cancel()
            },
            onSpeedClick = {
                showSpeedSheet = true
                hideControlsJob?.cancel()
            },
            onResizeClick = {
                showResizeSheet = true
                hideControlsJob?.cancel()
            },
            onPipClick = {
                enterPipMode(activity)
            }
        )
            }
        }
    }

    // Source selection bottom sheet
    if (showSourceSheet) {
        SourceSelectionSheet(
            links = uiState.links,
            selectedLink = uiState.selectedLink,
            onSelect = { link ->
                viewModel.selectLink(link)
            },
            onDismiss = {
                showSourceSheet = false
                resetHideTimer()
            }
        )
    }

    // Speed selection bottom sheet
    if (showSpeedSheet) {
        SpeedSelectionSheet(
            currentSpeed = playerState.playbackSpeed,
            onSelect = { speed ->
                playerState = playerState.copy(playbackSpeed = speed)
                exoPlayer?.setPlaybackSpeed(speed)
            },
            onDismiss = {
                showSpeedSheet = false
                resetHideTimer()
            }
        )
    }

    // Subtitle selection bottom sheet
    // Always use ExoPlayer's actual track list so indices match for TrackSelectionOverride.
    // This includes both embedded tracks (from HLS) and external tracks (from MergingMediaSource).
    if (showSubtitleSheet) {
        SubtitleSelectionSheet(
            tracks = playerState.subtitleTracks,
            selectedIndex = playerState.selectedSubtitleIndex,
            onSelect = { index ->
                playerState = playerState.copy(selectedSubtitleIndex = index)
                exoPlayer?.let { player ->
                    val trackSelector = player.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector
                    trackSelector?.let { selector ->
                        if (index == -1) {
                            // Disable all text tracks
                            selector.setParameters(
                                selector.buildUponParameters()
                                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                                    .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
                            )
                            android.util.Log.d("PlayerScreen", "Subtitles disabled")
                        } else {
                            // Find the specific text track group by counting text groups
                            val tracks = player.currentTracks
                            var textTrackGroupIndex = 0
                            var targetGroup: androidx.media3.common.Tracks.Group? = null

                            for (group in tracks.groups) {
                                if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                                    if (textTrackGroupIndex == index) {
                                        targetGroup = group
                                        break
                                    }
                                    textTrackGroupIndex++
                                }
                            }

                            if (targetGroup != null) {
                                val override = androidx.media3.common.TrackSelectionOverride(
                                    targetGroup.mediaTrackGroup,
                                    listOf(0)
                                )
                                selector.setParameters(
                                    selector.buildUponParameters()
                                        .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                                        .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
                                        .addOverride(override)
                                )
                                android.util.Log.d("PlayerScreen", "Selected subtitle track $index: ${playerState.subtitleTracks.getOrNull(index)?.label}")
                            } else {
                                android.util.Log.w("PlayerScreen", "Could not find text track group for index $index (total text groups: $textTrackGroupIndex)")
                            }
                        }
                    }
                }
            },
            onDismiss = {
                showSubtitleSheet = false
                resetHideTimer()
            }
        )
    }

    // Resize mode bottom sheet
    if (showResizeSheet) {
        ResizeModeSheet(
            currentMode = playerState.resizeMode,
            onSelect = { mode ->
                playerState = playerState.copy(resizeMode = mode)
            },
            onDismiss = {
                showResizeSheet = false
                resetHideTimer()
            }
        )
    }
}

private fun enterPipMode(activity: Activity?) {
    activity ?: return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            activity.enterPictureInPictureMode(params)
        } catch (e: Exception) {
            android.util.Log.e("PlayerScreen", "Failed to enter PiP mode", e)
        }
    }
}
