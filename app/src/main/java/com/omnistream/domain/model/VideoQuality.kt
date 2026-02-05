package com.omnistream.domain.model

/**
 * Represents video quality options for playback.
 *
 * Uses constraint-based quality selection via TrackSelectionParameters.setMaxVideoSize()
 * for persistent quality control across media items.
 *
 * @property maxHeight Maximum video height in pixels (Int.MAX_VALUE for Auto)
 * @property label User-facing display label
 */
sealed class VideoQuality(val maxHeight: Int, val label: String) {
    /**
     * Automatic quality selection - allows player to select best quality based on network.
     */
    data object AUTO : VideoQuality(Int.MAX_VALUE, "Auto")

    /**
     * Standard Definition 480p - suitable for slower connections.
     */
    data object SD_480P : VideoQuality(480, "480p")

    /**
     * High Definition 720p - balanced quality and bandwidth.
     */
    data object HD_720P : VideoQuality(720, "720p")

    /**
     * Full HD 1080p - highest quality for fast connections.
     */
    data object FULL_HD_1080P : VideoQuality(1080, "1080p")

    /**
     * Custom quality extracted from player track formats.
     * Used for dynamic quality options detected from video source.
     *
     * @property height Video height in pixels
     * @property width Video width in pixels
     * @property bitrate Video bitrate in bits per second
     */
    data class Custom(
        val height: Int,
        val width: Int,
        val bitrate: Int
    ) : VideoQuality(height, "${height}p")
}
