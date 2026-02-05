package com.omnistream.ui.player.components

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.ExoPlayer
import com.omnistream.domain.models.VideoQuality

/**
 * Dialog fragment for video quality selection.
 *
 * Queries the current player's available video tracks to build a dynamic quality list,
 * then presents them in an AlertDialog. Supports both predefined quality presets
 * (AUTO, 480p, 720p, 1080p) and custom qualities extracted from available tracks.
 *
 * Usage:
 * ```
 * QualitySelectionDialog(player) { selectedQuality ->
 *     // Apply quality constraint via TrackSelectionParameters
 *     applyQualityConstraint(selectedQuality)
 * }.show(supportFragmentManager, "quality_dialog")
 * ```
 *
 * @param player ExoPlayer instance to query for available video tracks
 * @param onQualitySelected Callback invoked when user selects a quality option
 */
class QualitySelectionDialog(
    private val player: ExoPlayer,
    private val onQualitySelected: (VideoQuality) -> Unit
) : DialogFragment() {

    /**
     * Get available video qualities from player's current tracks.
     *
     * Queries player.currentTracks for video track groups, extracts format information
     * (height, width, bitrate) for each track, and converts to VideoQuality instances.
     *
     * Results are:
     * - Mapped to Custom VideoQuality instances with extracted metadata
     * - Deduplicated by height (distinctBy)
     * - Sorted highest to lowest quality (sortedByDescending)
     * - AUTO option added to list
     *
     * @return List of available qualities including AUTO and detected custom qualities
     */
    private fun getAvailableQualities(): List<VideoQuality> {
        val tracks = player.currentTracks

        return tracks.groups
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group ->
                (0 until group.length).mapNotNull { i ->
                    val format = group.getTrackFormat(i)

                    // Only include tracks with valid dimensions
                    if (format.height != Format.NO_VALUE &&
                        format.width != Format.NO_VALUE) {
                        VideoQuality.Custom(
                            height = format.height,
                            width = format.width,
                            bitrate = if (format.bitrate != Format.NO_VALUE)
                                format.bitrate else 0
                        )
                    } else {
                        null
                    }
                }
            }
            .distinctBy { it.maxHeight } // Remove duplicate heights
            .sortedByDescending { it.maxHeight } // Highest quality first
            .plus(VideoQuality.AUTO) // Add auto option to end
    }

    /**
     * Create the quality selection dialog.
     *
     * Builds an AlertDialog with available quality options as selectable items.
     * When a quality is selected, invokes the onQualitySelected callback and dismisses.
     *
     * @param savedInstanceState Not used
     * @return Configured AlertDialog ready to display
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val qualities = getAvailableQualities()
        val labels = qualities.map { it.label }.toTypedArray()

        return AlertDialog.Builder(requireContext())
            .setTitle("Select Quality")
            .setItems(labels) { dialog, which ->
                val selectedQuality = qualities[which]
                onQualitySelected(selectedQuality)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
    }
}
