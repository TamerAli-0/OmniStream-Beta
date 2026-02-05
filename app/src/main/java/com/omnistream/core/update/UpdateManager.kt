package com.omnistream.core.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages APK download and installation
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _downloadProgress = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadProgress: StateFlow<DownloadState> = _downloadProgress

    private var downloadId: Long? = null

    /**
     * Download APK from URL and install
     */
    fun downloadAndInstall(apkUrl: String, version: String) {
        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("OmniStream Update")
            setDescription("Downloading version $version")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "OmniStream-$version.apk"
            )
            setMimeType("application/vnd.android.package-archive")
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)

        _downloadProgress.value = DownloadState.Downloading(0)

        // Start monitoring download progress
        startProgressMonitoring(downloadManager)

        // Register receiver to listen for download completion
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(downloadManager, id)
                    context.unregisterReceiver(this)
                }
            }
        }

        context.registerReceiver(
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    /**
     * Monitor download progress and update state
     */
    private fun startProgressMonitoring(downloadManager: DownloadManager) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                val id = downloadId ?: return

                val query = DownloadManager.Query().setFilterById(id)
                val cursor = downloadManager.query(query)

                if (cursor.moveToFirst()) {
                    val bytesDownloaded = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    )
                    val bytesTotal = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )
                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )

                    if (bytesTotal > 0) {
                        val progress = ((bytesDownloaded * 100) / bytesTotal).toInt()
                        _downloadProgress.value = DownloadState.Downloading(progress)
                    }

                    cursor.close()

                    // Keep monitoring if still downloading
                    if (status == DownloadManager.STATUS_RUNNING) {
                        handler.postDelayed(this, 100) // Update every 100ms
                    }
                } else {
                    cursor.close()
                }
            }
        }
        handler.post(runnable)
    }

    /**
     * Install downloaded APK
     */
    private fun installApk(downloadManager: DownloadManager, downloadId: Long) {
        val uri = downloadManager.getUriForDownloadedFile(downloadId)

        if (uri == null) {
            _downloadProgress.value = DownloadState.Error("Download failed")
            return
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(intent)
            _downloadProgress.value = DownloadState.ReadyToInstall
        } catch (e: Exception) {
            _downloadProgress.value = DownloadState.Error(e.message ?: "Installation failed")
        }
    }

    /**
     * Reset download state
     */
    fun resetState() {
        _downloadProgress.value = DownloadState.Idle
        downloadId = null
    }
}

/**
 * Download state
 */
sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    object ReadyToInstall : DownloadState()
    data class Error(val message: String) : DownloadState()
}
