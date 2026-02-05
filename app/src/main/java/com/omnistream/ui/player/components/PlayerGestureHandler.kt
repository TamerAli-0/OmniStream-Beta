@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.omnistream.ui.player.components

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SEEK_SECONDS = 10
private const val DOUBLE_TAP_DELAY = 300L

/**
 * Gesture handler overlay for the video player
 */
@Composable
fun PlayerGestureHandler(
    modifier: Modifier = Modifier,
    onToggleControls: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekDelta: (Int) -> Unit, // Seek by seconds (+/-)
    currentPosition: Long,
    duration: Long,
    isLocked: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Double tap state
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var showSeekForward by remember { mutableStateOf(false) }
    var showSeekBackward by remember { mutableStateOf(false) }
    var seekAccumulator by remember { mutableIntStateOf(0) }
    var hideSeekJob by remember { mutableStateOf<Job?>(null) }

    // Swipe state
    var isSwiping by remember { mutableStateOf(false) }
    var swipeType by remember { mutableStateOf<SwipeType?>(null) }
    var swipeStartValue by remember { mutableFloatStateOf(0f) }
    var currentSwipeValue by remember { mutableFloatStateOf(0f) }

    // Brightness control
    var brightness by remember {
        mutableFloatStateOf(
            try {
                val activity = context as? Activity
                activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 }
                    ?: Settings.System.getInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS, 128
                    ) / 255f
            } catch (e: Exception) { 0.5f }
        )
    }

    // Volume control
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var volume by remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
        )
    }

    // Horizontal seek state
    var isHorizontalSeeking by remember { mutableStateOf(false) }
    var seekPreviewPosition by remember { mutableLongStateOf(0L) }
    var seekDragAccumulator by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isLocked) {
                if (isLocked) return@pointerInput

                detectTapGestures(
                    onTap = { offset ->
                        val currentTime = System.currentTimeMillis()
                        val isDoubleTap = currentTime - lastTapTime < DOUBLE_TAP_DELAY
                        lastTapTime = currentTime

                        if (isDoubleTap) {
                            // Determine tap side
                            val screenWidth = size.width
                            val isLeftSide = offset.x < screenWidth / 2

                            if (isLeftSide) {
                                // Rewind
                                seekAccumulator -= SEEK_SECONDS
                                showSeekBackward = true
                                showSeekForward = false
                            } else {
                                // Forward
                                seekAccumulator += SEEK_SECONDS
                                showSeekForward = true
                                showSeekBackward = false
                            }

                            onSeekDelta(if (isLeftSide) -SEEK_SECONDS else SEEK_SECONDS)

                            // Reset hide timer
                            hideSeekJob?.cancel()
                            hideSeekJob = scope.launch {
                                delay(800)
                                showSeekForward = false
                                showSeekBackward = false
                                seekAccumulator = 0
                            }
                        } else {
                            // Single tap - toggle controls (with delay to check for double tap)
                            scope.launch {
                                delay(DOUBLE_TAP_DELAY)
                                if (System.currentTimeMillis() - lastTapTime >= DOUBLE_TAP_DELAY) {
                                    onToggleControls()
                                }
                            }
                        }
                    }
                )
            }
            .pointerInput(isLocked, duration) {
                if (isLocked || duration <= 0) return@pointerInput

                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val screenWidth = size.width
                        val isLeftSide = offset.x < screenWidth / 2
                        swipeType = if (isLeftSide) SwipeType.BRIGHTNESS else SwipeType.VOLUME
                        swipeStartValue = if (isLeftSide) brightness else volume
                        currentSwipeValue = swipeStartValue
                        isSwiping = true
                    },
                    onDragEnd = {
                        isSwiping = false
                        swipeType = null
                    },
                    onDragCancel = {
                        isSwiping = false
                        swipeType = null
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val sensitivity = 0.005f
                        val delta = -dragAmount * sensitivity

                        when (swipeType) {
                            SwipeType.BRIGHTNESS -> {
                                brightness = (brightness + delta).coerceIn(0f, 1f)
                                currentSwipeValue = brightness
                                setBrightness(context, brightness)
                            }
                            SwipeType.VOLUME -> {
                                volume = (volume + delta).coerceIn(0f, 1f)
                                currentSwipeValue = volume
                                setVolume(audioManager, volume, maxVolume)
                            }
                            null -> {}
                        }
                    }
                )
            }
            .pointerInput(isLocked, duration) {
                if (isLocked || duration <= 0) return@pointerInput

                detectHorizontalDragGestures(
                    onDragStart = {
                        isHorizontalSeeking = true
                        seekPreviewPosition = currentPosition
                        seekDragAccumulator = 0f
                    },
                    onDragEnd = {
                        if (isHorizontalSeeking) {
                            onSeek(seekPreviewPosition)
                        }
                        isHorizontalSeeking = false
                    },
                    onDragCancel = {
                        isHorizontalSeeking = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        // Swipe across full screen = 2 minutes of seek
                        val seekPerPx = (120_000f / size.width)
                        seekDragAccumulator += dragAmount * seekPerPx
                        seekPreviewPosition = (currentPosition + seekDragAccumulator.toLong())
                            .coerceIn(0, duration)
                    }
                )
            }
    ) {
        // Double tap seek indicators
        DoubleTapSeekIndicator(
            visible = showSeekBackward,
            isForward = false,
            seconds = -seekAccumulator,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        DoubleTapSeekIndicator(
            visible = showSeekForward,
            isForward = true,
            seconds = seekAccumulator,
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        // Brightness/Volume indicator
        AnimatedVisibility(
            visible = isSwiping,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            SwipeIndicator(
                type = swipeType ?: SwipeType.VOLUME,
                value = currentSwipeValue
            )
        }

        // Horizontal seek preview
        AnimatedVisibility(
            visible = isHorizontalSeeking,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            SeekPreview(
                currentPosition = currentPosition,
                seekPosition = seekPreviewPosition,
                duration = duration
            )
        }
    }
}

private enum class SwipeType { BRIGHTNESS, VOLUME }

@Composable
private fun DoubleTapSeekIndicator(
    visible: Boolean,
    isForward: Boolean,
    seconds: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier
            .padding(horizontal = 48.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${if (seconds > 0) "+" else ""}${seconds}s",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SwipeIndicator(
    type: SwipeType,
    value: Float
) {
    val icon: ImageVector
    val label: String

    when (type) {
        SwipeType.BRIGHTNESS -> {
            icon = Icons.Default.BrightnessHigh
            label = "Brightness"
        }
        SwipeType.VOLUME -> {
            icon = Icons.Default.VolumeUp
            label = "Volume"
        }
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(100.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${(value * 100).toInt()}%",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SeekPreview(
    currentPosition: Long,
    seekPosition: Long,
    duration: Long
) {
    val delta = seekPosition - currentPosition
    val deltaSign = if (delta >= 0) "+" else ""

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formatTime(seekPosition),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$deltaSign${formatTime(delta, showSign = true)}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
    }
}

private fun formatTime(millis: Long, showSign: Boolean = false): String {
    val totalSeconds = kotlin.math.abs(millis / 1000)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return buildString {
        if (showSign && millis < 0) append("-")
        if (hours > 0) {
            append("$hours:")
            append(minutes.toString().padStart(2, '0'))
        } else {
            append(minutes)
        }
        append(":")
        append(seconds.toString().padStart(2, '0'))
    }
}

private fun setBrightness(context: Context, value: Float) {
    try {
        val activity = context as? Activity ?: return
        val layoutParams = activity.window.attributes
        layoutParams.screenBrightness = value
        activity.window.attributes = layoutParams
    } catch (e: Exception) {
        // Ignore brightness errors
    }
}

private fun setVolume(audioManager: AudioManager, value: Float, maxVolume: Int) {
    try {
        val volume = (value * maxVolume).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    } catch (e: Exception) {
        // Ignore volume errors
    }
}
