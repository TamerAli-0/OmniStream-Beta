@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.omnistream.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Custom player controls overlay
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControls(
    visible: Boolean,
    title: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    isLocked: Boolean,
    subtitlesEnabled: Boolean,
    playbackSpeed: Float,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekDelta: (Int) -> Unit,
    onToggleLock: () -> Unit,
    onSourceClick: () -> Unit,
    onSubtitlesClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onResizeClick: () -> Unit,
    onPipClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onNextEpisode: (() -> Unit)? = null
) {
    // Gradient backgrounds
    val topGradient = Brush.verticalGradient(
        colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
    )
    val bottomGradient = Brush.verticalGradient(
        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Lock button always visible when locked
        if (isLocked) {
            LockedOverlay(onUnlock = onToggleLock)
            return@Box
        }

        // Buffering indicator
        if (isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top bar
                TopControlBar(
                    title = title,
                    onBack = onBack,
                    onPipClick = onPipClick,
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .background(topGradient)
                )

                // Center controls
                CenterControls(
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    onPlayPause = onPlayPause,
                    onSeekBackward = { onSeekDelta(-10) },
                    onSeekForward = { onSeekDelta(10) },
                    modifier = Modifier.align(Alignment.Center)
                )

                // Bottom bar
                BottomControlBar(
                    currentPosition = currentPosition,
                    duration = duration,
                    bufferedPosition = bufferedPosition,
                    subtitlesEnabled = subtitlesEnabled,
                    playbackSpeed = playbackSpeed,
                    isLocked = isLocked,
                    onSeek = onSeek,
                    onToggleLock = onToggleLock,
                    onSourceClick = onSourceClick,
                    onSubtitlesClick = onSubtitlesClick,
                    onSpeedClick = onSpeedClick,
                    onResizeClick = onResizeClick,
                    onNextEpisode = onNextEpisode,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(bottomGradient)
                )
            }
        }
    }
}

@Composable
private fun TopControlBar(
    title: String,
    onBack: () -> Unit,
    onPipClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }

        Row {
            IconButton(onClick = onPipClick) {
                Icon(
                    Icons.Default.PictureInPicture,
                    contentDescription = "Picture in Picture",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun CenterControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rewind button
        PlayerIconButton(
            icon = Icons.Default.Replay10,
            contentDescription = "Rewind 10 seconds",
            onClick = onSeekBackward,
            size = 48.dp
        )

        // Play/Pause button
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPlayPause
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!isBuffering) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Forward button
        PlayerIconButton(
            icon = Icons.Default.Forward10,
            contentDescription = "Forward 10 seconds",
            onClick = onSeekForward,
            size = 48.dp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomControlBar(
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    subtitlesEnabled: Boolean,
    playbackSpeed: Float,
    isLocked: Boolean,
    onSeek: (Long) -> Unit,
    onToggleLock: () -> Unit,
    onSourceClick: () -> Unit,
    onSubtitlesClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onResizeClick: () -> Unit,
    onNextEpisode: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Seek bar
        SeekBar(
            currentPosition = currentPosition,
            duration = duration,
            bufferedPosition = bufferedPosition,
            onSeek = onSeek
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Time display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(currentPosition),
                color = Color.White,
                fontSize = 12.sp
            )
            Text(
                text = formatDuration(duration),
                color = Color.White,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Lock button
                SmallIconButton(
                    icon = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (isLocked) "Unlock" else "Lock",
                    onClick = onToggleLock
                )

                // Subtitles button
                SmallIconButton(
                    icon = if (subtitlesEnabled) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionOff,
                    contentDescription = "Subtitles",
                    onClick = onSubtitlesClick
                )
            }

            // Center controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speed button with label
                SpeedButton(
                    speed = playbackSpeed,
                    onClick = onSpeedClick
                )

                // Quality/Source button
                SmallIconButton(
                    icon = Icons.Default.HighQuality,
                    contentDescription = "Quality",
                    onClick = onSourceClick
                )

                // Resize button
                SmallIconButton(
                    icon = Icons.Default.AspectRatio,
                    contentDescription = "Resize",
                    onClick = onResizeClick
                )
            }

            // Right side controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Next episode button
                if (onNextEpisode != null) {
                    SmallIconButton(
                        icon = Icons.Default.SkipNext,
                        contentDescription = "Next Episode",
                        onClick = onNextEpisode
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    onSeek: (Long) -> Unit
) {
    val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
    val bufferedProgress = if (duration > 0) bufferedPosition.toFloat() / duration else 0f

    Box(modifier = Modifier.fillMaxWidth()) {
        // Buffered progress (background track)
        Slider(
            value = bufferedProgress,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            colors = SliderDefaults.colors(
                disabledThumbColor = Color.Transparent,
                disabledActiveTrackColor = Color.White.copy(alpha = 0.3f),
                disabledInactiveTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )

        // Current progress (foreground)
        Slider(
            value = progress,
            onValueChange = { value ->
                onSeek((value * duration).toLong())
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF6C63FF),
                activeTrackColor = Color(0xFF6C63FF),
                inactiveTrackColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(size * 0.7f)
        )
    }
}

@Composable
private fun SmallIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun SpeedButton(
    speed: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "${speed}x",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LockedOverlay(
    onUnlock: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Lock button in center
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onUnlock)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = "Tap to unlock",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
