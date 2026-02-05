@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.omnistream.ui.player.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.omnistream.domain.model.VideoLink
import kotlinx.coroutines.delay

/**
 * Aspect ratio modes for video display
 */
enum class ResizeMode(val value: Int, val label: String) {
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Fit"),
    FILL(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Fill"),
    ZOOM(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Zoom"),
    FIXED_WIDTH(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, "Fixed Width"),
    FIXED_HEIGHT(AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT, "Fixed Height")
}

/**
 * Playback speed options
 */
enum class PlaybackSpeed(val value: Float, val label: String) {
    SPEED_0_25(0.25f, "0.25x"),
    SPEED_0_5(0.5f, "0.5x"),
    SPEED_0_75(0.75f, "0.75x"),
    SPEED_1_0(1.0f, "1x"),
    SPEED_1_25(1.25f, "1.25x"),
    SPEED_1_5(1.5f, "1.5x"),
    SPEED_1_75(1.75f, "1.75x"),
    SPEED_2_0(2.0f, "2x")
}

/**
 * State holder for video player
 */
data class VideoPlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = true,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val playbackSpeed: Float = 1f,
    val resizeMode: ResizeMode = ResizeMode.FIT,
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val selectedSubtitleIndex: Int = -1,
    val selectedAudioIndex: Int = 0
)

data class SubtitleTrack(
    val index: Int,
    val language: String,
    val label: String
)

data class AudioTrack(
    val index: Int,
    val language: String,
    val label: String
)

/**
 * Core video player composable wrapping ExoPlayer
 */
@Composable
fun VideoPlayer(
    link: VideoLink?,
    modifier: Modifier = Modifier,
    state: VideoPlayerState = VideoPlayerState(),
    onStateChange: (VideoPlayerState) -> Unit = {},
    onPlayerReady: (ExoPlayer) -> Unit = {},
    showControls: Boolean = true
) {
    val context = LocalContext.current

    // Track selector for quality/subtitle/audio selection
    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters()
                .setPreferredTextLanguage("en")
            )
        }
    }

    // ExoPlayer instance
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build().apply {
                playWhenReady = true
            }
    }

    // PlayerView reference
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    // Update resize mode
    LaunchedEffect(state.resizeMode) {
        playerView?.resizeMode = state.resizeMode.value
    }

    // Update playback speed
    LaunchedEffect(state.playbackSpeed) {
        exoPlayer.setPlaybackSpeed(state.playbackSpeed)
    }

    // Keep updated reference to state for use in callbacks
    val currentState by androidx.compose.runtime.rememberUpdatedState(state)

    // Position update loop - always read isBuffering/isPlaying from player to avoid race conditions
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(500)
            val isCurrentlyBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING
            val isCurrentlyPlaying = exoPlayer.isPlaying
            val currentDuration = if (exoPlayer.duration != C.TIME_UNSET) exoPlayer.duration else 0L

            onStateChange(currentState.copy(
                currentPosition = exoPlayer.currentPosition,
                bufferedPosition = exoPlayer.bufferedPosition,
                isBuffering = isCurrentlyBuffering,
                isPlaying = isCurrentlyPlaying,
                duration = currentDuration
            ))
        }
    }

    // Player listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                android.util.Log.d("VideoPlayer", "Playback state: $playbackState, isBuffering: $isBuffering")
                onStateChange(currentState.copy(
                    isBuffering = isBuffering,
                    duration = if (exoPlayer.duration != C.TIME_UNSET) exoPlayer.duration else 0L
                ))
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                android.util.Log.d("VideoPlayer", "isPlaying changed: $isPlaying")
                onStateChange(currentState.copy(isPlaying = isPlaying))
            }

            override fun onTracksChanged(tracks: Tracks) {
                val subtitles = mutableListOf<SubtitleTrack>()
                val audios = mutableListOf<AudioTrack>()

                tracks.groups.forEachIndexed { groupIndex, group ->
                    for (trackIndex in 0 until group.length) {
                        val format = group.getTrackFormat(trackIndex)
                        when {
                            format.sampleMimeType?.startsWith("text/") == true ||
                            format.sampleMimeType?.contains("subtitle") == true -> {
                                subtitles.add(SubtitleTrack(
                                    index = groupIndex,
                                    language = format.language ?: "Unknown",
                                    label = format.label ?: format.language ?: "Subtitle ${subtitles.size + 1}"
                                ))
                            }
                            format.sampleMimeType?.startsWith("audio/") == true -> {
                                audios.add(AudioTrack(
                                    index = groupIndex,
                                    language = format.language ?: "Unknown",
                                    label = format.label ?: format.language ?: "Audio ${audios.size + 1}"
                                ))
                            }
                        }
                    }
                }

                onStateChange(currentState.copy(
                    subtitleTracks = subtitles,
                    audioTracks = audios
                ))
            }
        }

        exoPlayer.addListener(listener)
        onPlayerReady(exoPlayer)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Load media when link changes
    DisposableEffect(link) {
        link?.let {
            val isLocalFile = link.url.startsWith("file://")
            android.util.Log.d("VideoPlayer", "Loading URL: ${link.url} (offline=$isLocalFile)")

            val mediaItem = MediaItem.Builder()
                .setUri(link.url)
                .build()

            if (isLocalFile) {
                // Offline playback: use DefaultDataSource which handles file:// URIs
                val localDataSourceFactory = DefaultDataSource.Factory(context)
                val videoSource = ProgressiveMediaSource.Factory(localDataSourceFactory)
                    .createMediaSource(mediaItem)
                exoPlayer.setMediaSource(videoSource)
                android.util.Log.d("VideoPlayer", "Set local file media source")
            } else {
                // Online playback: use HTTP data source with headers
                val requestHeaders = buildMap {
                    put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    link.referer?.let { put("Referer", it) }
                    link.headers.forEach { (k, v) -> put(k, v) }
                }
                android.util.Log.d("VideoPlayer", "Request headers: $requestHeaders")

                val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(requestHeaders)
                    .setAllowCrossProtocolRedirects(true)

                // Create the video media source
                val videoSource = if (link.isM3u8 || link.url.contains(".m3u8")) {
                    HlsMediaSource.Factory(httpDataSourceFactory)
                        .createMediaSource(mediaItem)
                } else {
                    ProgressiveMediaSource.Factory(httpDataSourceFactory)
                        .createMediaSource(mediaItem)
                }

                // Create subtitle sources and merge them with the video source
                if (link.subtitles.isNotEmpty()) {
                    android.util.Log.d("VideoPlayer", "Creating ${link.subtitles.size} subtitle media sources")
                    val subtitleSources = link.subtitles.mapIndexed { index, subtitle ->
                        val mimeType = when {
                            subtitle.url.contains(".vtt", ignoreCase = true) -> "text/vtt"
                            subtitle.url.contains(".srt", ignoreCase = true) -> "application/x-subrip"
                            subtitle.url.contains(".ass", ignoreCase = true) || subtitle.url.contains(".ssa", ignoreCase = true) -> "text/x-ssa"
                            else -> "text/vtt"
                        }
                        if (index < 5) { // Only log first 5 to avoid spam
                            android.util.Log.d("VideoPlayer", "Subtitle [$index]: ${subtitle.label ?: subtitle.language} ($mimeType)")
                        }
                        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle.url))
                            .setMimeType(mimeType)
                            .setLanguage(subtitle.language)
                            .setLabel(subtitle.label ?: subtitle.language)
                            .setSelectionFlags(0) // Don't auto-select any subtitle
                            .build()
                        SingleSampleMediaSource.Factory(httpDataSourceFactory)
                            .createMediaSource(subtitleConfig, C.TIME_UNSET)
                    }

                    // Merge video + all subtitle sources
                    val mergedSource = MergingMediaSource(
                        videoSource,
                        *subtitleSources.toTypedArray()
                    )
                    exoPlayer.setMediaSource(mergedSource)
                    android.util.Log.d("VideoPlayer", "Set merged media source with ${subtitleSources.size} subtitle tracks")
                } else {
                    exoPlayer.setMediaSource(videoSource)
                }

                // Disable text tracks by default (user must select manually)
                trackSelector.setParameters(
                    trackSelector.buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                )
            }

            exoPlayer.prepare()
        }
        onDispose { }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false // We use custom controls
                    resizeMode = state.resizeMode.value
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER) // We handle buffering UI
                    // Ensure subtitles are visible
                    subtitleView?.apply {
                        setUserDefaultStyle()
                        setUserDefaultTextSize()
                    }
                    playerView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
