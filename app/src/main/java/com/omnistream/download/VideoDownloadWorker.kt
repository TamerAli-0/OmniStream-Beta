package com.omnistream.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.omnistream.data.local.DownloadDao
import com.omnistream.core.network.OmniHttpClient
import com.omnistream.domain.model.Episode
import com.omnistream.source.SourceManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class VideoDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val httpClient: OmniHttpClient,
    private val notificationHelper: DownloadNotificationHelper,
    private val sourceManager: SourceManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "VideoDownloadWorker"
        private const val BUFFER_SIZE = 8192
        private const val NOTIFICATION_THROTTLE_MS = 1000L
    }

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString("download_id") ?: return Result.failure()
        val sourceId = inputData.getString("source_id") ?: return Result.failure()
        val contentId = inputData.getString("content_id") ?: return Result.failure()
        val episodeId = inputData.getString("episode_id") ?: return Result.failure()
        val title = inputData.getString("title") ?: "Download"
        val filePath = inputData.getString("file_path") ?: return Result.failure()
        var videoUrl = inputData.getString("video_url") ?: ""
        var referer = inputData.getString("referer") ?: ""

        return try {
            // If no video URL provided, resolve it from the source
            if (videoUrl.isBlank()) {
                val resolved = resolveVideoUrl(sourceId, contentId, episodeId)
                if (resolved == null) {
                    Log.e(TAG, "Could not resolve video URL for $episodeId")
                    downloadDao.updateProgress(downloadId, 0f, "failed")
                    return Result.failure()
                }
                videoUrl = resolved.first
                if (referer.isBlank()) referer = resolved.second
            }

            // Promote to foreground with progress notification
            try {
                setForeground(createForegroundInfo(downloadId, title, 0f))
            } catch (e: Exception) {
                Log.w(TAG, "Could not start foreground: ${e.message}")
            }

            // Mark as downloading
            downloadDao.updateProgress(downloadId, 0f, "downloading")

            // Create output file and parent directories
            val outputFile = File(filePath)
            outputFile.parentFile?.mkdirs()

            val isHls = videoUrl.contains(".m3u8", ignoreCase = true)
            Log.d(TAG, "Downloading video: isHls=$isHls, url=${videoUrl.take(100)}...")

            if (isHls) {
                downloadHls(videoUrl, referer, outputFile, downloadId, title)
            } else {
                downloadDirect(videoUrl, referer, outputFile, downloadId, title)
            }

            // Update entity with final progress and file size
            val finalSize = outputFile.length()
            val entity = downloadDao.getById(downloadId)
            if (entity != null) {
                downloadDao.upsert(entity.copy(
                    progress = 1f,
                    status = "completed",
                    fileSize = finalSize
                ))
            } else {
                downloadDao.updateProgress(downloadId, 1f, "completed")
            }

            Log.d(TAG, "Download complete: $filePath ($finalSize bytes)")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $downloadId", e)
            downloadDao.updateProgress(downloadId, 0f, "failed")
            Result.failure()
        }
    }

    /**
     * Download an HLS stream by fetching the playlist, parsing segments, and concatenating them.
     */
    private suspend fun downloadHls(
        m3u8Url: String,
        referer: String,
        outputFile: File,
        downloadId: String,
        title: String
    ) {
        val refererHeader = referer.ifBlank { null }

        // Fetch the m3u8 playlist
        val playlistText = httpClient.get(m3u8Url, referer = refererHeader)
        Log.d(TAG, "M3U8 playlist length: ${playlistText.length}")

        val baseUrl = m3u8Url.substringBeforeLast("/") + "/"

        // Check if this is a master playlist (contains variant streams)
        val variantPlaylist = if (playlistText.contains("#EXT-X-STREAM-INF")) {
            // Pick the highest bandwidth variant
            val lines = playlistText.lines()
            var bestUrl: String? = null
            var bestBandwidth = -1L
            for (i in lines.indices) {
                if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                    val bw = Regex("""BANDWIDTH=(\d+)""").find(lines[i])
                        ?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val url = lines.getOrNull(i + 1)?.trim() ?: continue
                    if (bw > bestBandwidth) {
                        bestBandwidth = bw
                        bestUrl = url
                    }
                }
            }

            if (bestUrl != null) {
                val variantUrl = if (bestUrl.startsWith("http")) bestUrl else baseUrl + bestUrl
                Log.d(TAG, "Selected variant: bandwidth=$bestBandwidth, url=${variantUrl.take(80)}...")
                httpClient.get(variantUrl, referer = refererHeader)
            } else {
                playlistText
            }
        } else {
            playlistText
        }

        // Parse .ts segment URLs from the variant playlist
        val segments = variantPlaylist.lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                if (line.startsWith("http")) line
                else baseUrl + line.trim()
            }

        Log.d(TAG, "Found ${segments.size} segments to download")

        if (segments.isEmpty()) {
            throw Exception("No segments found in HLS playlist")
        }

        // Change extension to .ts since we're concatenating transport stream segments
        val tsFile = if (outputFile.extension == "mp4") {
            File(outputFile.parent, outputFile.nameWithoutExtension + ".ts")
        } else outputFile

        // Download and concatenate all segments
        tsFile.outputStream().use { output ->
            var lastNotificationUpdate = 0L
            for ((index, segmentUrl) in segments.withIndex()) {
                if (isStopped) {
                    val progress = index.toFloat() / segments.size
                    downloadDao.updateProgress(downloadId, progress, "paused")
                    Log.d(TAG, "HLS download paused at segment $index/${segments.size}")
                    throw Exception("Download paused")
                }

                val response = httpClient.getRaw(segmentUrl, referer = refererHeader)
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        Log.e(TAG, "Failed to download segment $index: HTTP ${resp.code}")
                        throw Exception("Failed to download segment $index: HTTP ${resp.code}")
                    }
                    resp.body?.byteStream()?.use { input ->
                        input.copyTo(output)
                    }
                }

                val progress = (index + 1).toFloat() / segments.size
                val now = System.currentTimeMillis()
                if (now - lastNotificationUpdate > NOTIFICATION_THROTTLE_MS || index == segments.size - 1) {
                    setProgress(workDataOf("progress" to progress))
                    downloadDao.updateProgress(downloadId, progress, "downloading")
                    try {
                        setForeground(createForegroundInfo(downloadId, title, progress))
                    } catch (_: Exception) { }
                    lastNotificationUpdate = now
                }
            }
        }

        // If we saved as .ts but the DB expects .mp4, rename
        if (tsFile != outputFile) {
            // Update the DB filePath to point to .ts file
            val entity = downloadDao.getById(downloadId)
            if (entity != null) {
                downloadDao.upsert(entity.copy(filePath = tsFile.absolutePath))
            }
        }

        Log.d(TAG, "HLS download complete: ${segments.size} segments, ${tsFile.length()} bytes")
    }

    /**
     * Download a direct video file (MP4, etc.) with resume support.
     */
    private suspend fun downloadDirect(
        videoUrl: String,
        referer: String,
        outputFile: File,
        downloadId: String,
        title: String
    ) {
        val existingSize = if (outputFile.exists()) outputFile.length() else 0L
        val headers = mutableMapOf<String, String>()
        if (existingSize > 0) {
            headers["Range"] = "bytes=$existingSize-"
            Log.d(TAG, "Resuming download from byte $existingSize")
        }

        val response = httpClient.getRaw(videoUrl, headers = headers, referer = referer.ifBlank { null })
        response.use { resp ->
            if (!resp.isSuccessful && resp.code != 206) {
                Log.e(TAG, "Failed to download video: HTTP ${resp.code}")
                downloadDao.updateProgress(downloadId, 0f, "failed")
                throw Exception("Failed to download video: HTTP ${resp.code}")
            }

            val contentLength = resp.body?.contentLength() ?: -1L
            val totalSize = if (contentLength > 0) contentLength + existingSize else -1L

            val inputStream = resp.body?.byteStream()
                ?: throw Exception("Empty response body")

            val outputStream = if (existingSize > 0) {
                java.io.FileOutputStream(outputFile, true)
            } else {
                outputFile.outputStream()
            }

            inputStream.use { input ->
                outputStream.use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var totalBytesWritten = existingSize
                    var lastNotificationUpdate = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped) {
                            val progress = if (totalSize > 0) {
                                totalBytesWritten.toFloat() / totalSize
                            } else 0f
                            downloadDao.updateProgress(downloadId, progress, "paused")
                            Log.d(TAG, "Download paused at $totalBytesWritten bytes")
                            throw Exception("Download paused")
                        }

                        output.write(buffer, 0, bytesRead)
                        totalBytesWritten += bytesRead

                        val progress = if (totalSize > 0) {
                            totalBytesWritten.toFloat() / totalSize
                        } else 0f

                        val now = System.currentTimeMillis()
                        if (now - lastNotificationUpdate > NOTIFICATION_THROTTLE_MS ||
                            (totalSize > 0 && totalBytesWritten >= totalSize)
                        ) {
                            setProgress(workDataOf("progress" to progress))
                            downloadDao.updateProgress(downloadId, progress, "downloading")
                            try {
                                setForeground(createForegroundInfo(downloadId, title, progress))
                            } catch (_: Exception) { }
                            lastNotificationUpdate = now
                        }
                    }
                }
            }
        }
    }

    /**
     * Resolve the video URL by fetching links from the source.
     * Returns pair of (videoUrl, referer) or null if not found.
     */
    private suspend fun resolveVideoUrl(
        sourceId: String,
        contentId: String,
        episodeId: String
    ): Pair<String, String>? {
        val source = sourceManager.getVideoSource(sourceId) ?: return null

        // Build episode URL same way as PlayerViewModel
        val episodeUrl = when (sourceId) {
            "gogoanime" -> "${source.baseUrl}/$episodeId/"
            "vidsrc" -> episodeId
            else -> "${source.baseUrl}/episode/$episodeId"
        }

        val seasonEpisodeMatch = Regex("""_s(\d+)_e(\d+)""").find(episodeId)
        val season = seasonEpisodeMatch?.groupValues?.get(1)?.toIntOrNull()
        val episodeNum = seasonEpisodeMatch?.groupValues?.get(2)?.toIntOrNull()
            ?: extractEpisodeNumber(episodeId)

        val episode = Episode(
            id = episodeId,
            videoId = contentId,
            sourceId = sourceId,
            url = episodeUrl,
            number = episodeNum,
            season = season
        )

        val links = source.getLinks(episode)
        if (links.isEmpty()) return null

        // Prefer direct MP4 links over HLS/DASH
        val bestLink = links.firstOrNull { !it.isM3u8 && !it.isDash }
            ?: links.firstOrNull()
            ?: return null

        return Pair(bestLink.url, bestLink.referer ?: source.baseUrl)
    }

    private fun extractEpisodeNumber(episodeId: String): Int {
        return Regex("""(\d+)""").findAll(episodeId).lastOrNull()
            ?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }

    private fun createForegroundInfo(downloadId: String, title: String, progress: Float): ForegroundInfo {
        val notification = notificationHelper.buildProgressNotification(title, progress, downloadId)
        val notificationId = downloadId.hashCode()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
}
