package com.omnistream.domain.models

/**
 * Video quality presets for player constraint-based quality selection.
 *
 * Uses sealed class instead of enum to support both predefined quality presets
 * (AUTO, SD_480P, HD_720P, FULL_HD_1080P) and dynamic Custom qualities extracted
 * from player tracks.
 *
 * The maxHeight property is used with TrackSelectionParameters.setMaxVideoSize()
 * for constraint-based quality control that persists across media items.
 */
sealed class VideoQuality(val maxHeight: Int, val label: String) {
    /**
     * Automatic quality selection - lets ExoPlayer choose best quality based on bandwidth.
     * Uses Int.MAX_VALUE to remove height constraint.
     */
    data object AUTO : VideoQuality(Int.MAX_VALUE, "Auto")

    /**
     * Standard definition - 480p quality preset.
     */
    data object SD_480P : VideoQuality(480, "480p")

    /**
     * High definition - 720p quality preset.
     */
    data object HD_720P : VideoQuality(720, "720p")

    /**
     * Full HD - 1080p quality preset.
     */
    data object FULL_HD_1080P : VideoQuality(1080, "1080p")

    /**
     * Custom quality extracted from player's available video tracks.
     * Used for dynamic quality options not covered by presets.
     *
     * @param height Video height in pixels
     * @param width Video width in pixels
     * @param bitrate Video bitrate in bits per second
     */
    data class Custom(
        val height: Int,
        val width: Int,
        val bitrate: Int
    ) : VideoQuality(height, "${height}p")
}
