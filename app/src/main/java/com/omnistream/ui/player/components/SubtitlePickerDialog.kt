package com.omnistream.ui.player.components

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

/**
 * DialogFragment for selecting external subtitle files using Storage Access Framework.
 *
 * This dialog provides two options:
 * 1. Load Subtitle File - Opens file picker for SRT/VTT files
 * 2. Disable Subtitles - Clears any loaded subtitle
 *
 * Uses content:// URIs via SAF (Storage Access Framework) for reliable file access.
 * Pattern follows RESEARCH.md recommendations (Pitfall 3: prefer content:// over file://).
 *
 * @param onSubtitleSelected Callback invoked with selected subtitle URI or null to disable
 */
class SubtitlePickerDialog(
    private val onSubtitleSelected: (Uri?) -> Unit
) : DialogFragment() {

    /**
     * Activity result launcher for document picking.
     *
     * Registered in onCreate() lifecycle hook.
     * Filters to text MIME types to show only subtitle-compatible files.
     */
    private val pickSubtitle = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        // Invoke callback with selected URI (or null if user cancelled)
        onSubtitleSelected(uri)
        dismiss()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // pickSubtitle launcher is already registered via property initialization
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle("Subtitle Options")
            .setItems(
                arrayOf(
                    "Load Subtitle File",
                    "Disable Subtitles"
                )
            ) { dialog, which ->
                when (which) {
                    0 -> {
                        // Launch file picker with MIME type filters
                        // Accepts: text/plain (.srt), text/vtt (.vtt), application/x-subrip (.srt)
                        pickSubtitle.launch(
                            arrayOf(
                                "text/plain",           // Generic text files (includes .srt)
                                "text/vtt",             // WebVTT subtitle format
                                "application/x-subrip"  // SubRip subtitle format
                            )
                        )
                    }
                    1 -> {
                        // User wants to disable subtitles
                        onSubtitleSelected(null)
                        dismiss()
                    }
                }
            }
            .setNegativeButton("Cancel") { cancelDialog, _ ->
                cancelDialog.dismiss()
            }
            .create()
    }
}
