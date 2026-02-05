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
import com.omnistream.domain.model.Chapter
import com.omnistream.domain.model.Manga
import com.omnistream.source.SourceManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class MangaDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val httpClient: OmniHttpClient,
    private val sourceManager: SourceManager,
    private val notificationHelper: DownloadNotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "MangaDownloadWorker"
    }

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString("download_id") ?: return Result.failure()
        val sourceId = inputData.getString("source_id") ?: return Result.failure()
        val contentId = inputData.getString("content_id") ?: return Result.failure()
        val chapterId = inputData.getString("chapter_id") ?: return Result.failure()
        val title = inputData.getString("title") ?: "Download"
        val filePath = inputData.getString("file_path") ?: return Result.failure()

        return try {
            // Promote to foreground with progress notification
            try {
                setForeground(createForegroundInfo(downloadId, title, 0f))
            } catch (e: Exception) {
                Log.w(TAG, "Could not start foreground: ${e.message}")
            }

            // Mark as downloading
            downloadDao.updateProgress(downloadId, 0f, "downloading")

            // Get manga source
            val source = sourceManager.getMangaSource(sourceId)
            if (source == null) {
                Log.e(TAG, "Source not found: $sourceId")
                downloadDao.updateProgress(downloadId, 0f, "failed")
                return Result.failure()
            }

            // Build chapter URL using same logic as ReaderViewModel
            val chapterUrl = when (sourceId) {
                "mangadex" -> "${source.baseUrl}/chapter/$chapterId"
                "asuracomic" -> "${source.baseUrl}/series/$contentId/chapter/$chapterId"
                "manhuaplus" -> chapterId // manhuaplus uses full URL as chapter ID
                else -> "${source.baseUrl}/chapter/$chapterId"
            }

            // Create Chapter object and fetch pages
            val chapter = Chapter(
                id = chapterId,
                mangaId = contentId,
                sourceId = sourceId,
                url = chapterUrl,
                number = extractChapterNumber(chapterId)
            )

            val pages = source.getPages(chapter)
            if (pages.isEmpty()) {
                Log.e(TAG, "No pages found for chapter: $chapterId")
                downloadDao.updateProgress(downloadId, 0f, "failed")
                return Result.failure()
            }

            Log.d(TAG, "Downloading ${pages.size} pages for chapter $chapterId")

            // Create download directory
            val downloadDir = File(filePath)
            downloadDir.mkdirs()

            // Download each page
            for ((index, page) in pages.withIndex()) {
                // Check cancellation
                if (isStopped) {
                    val currentProgress = index.toFloat() / pages.size
                    downloadDao.updateProgress(downloadId, currentProgress, "paused")
                    Log.d(TAG, "Download paused at page $index/${pages.size}")
                    return Result.failure()
                }

                // Determine file extension from URL
                val extension = page.imageUrl.substringAfterLast(".")
                    .substringBefore("?")
                    .takeIf { it.length in 2..4 && it.all { c -> c.isLetterOrDigit() } }
                    ?: "jpg"
                val pageFile = File(downloadDir, "page_${String.format("%03d", index)}.$extension")

                // Skip if already downloaded (resume support)
                if (pageFile.exists() && pageFile.length() > 0) {
                    Log.d(TAG, "Skipping already downloaded page $index")
                } else {
                    // Download page image with image-specific headers
                    val referer = page.referer ?: source.baseUrl
                    val imageHeaders = mapOf(
                        "Accept" to "image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
                        "Sec-Fetch-Dest" to "image",
                        "Sec-Fetch-Mode" to "no-cors",
                        "Sec-Fetch-Site" to "cross-site"
                    )
                    val response = httpClient.getRaw(page.imageUrl, headers = imageHeaders, referer = referer)
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            Log.e(TAG, "Failed to download page $index: HTTP ${resp.code}")
                            throw Exception("Failed to download page $index: HTTP ${resp.code}")
                        }
                        // Validate response is actually an image
                        val contentType = resp.header("Content-Type") ?: ""
                        if (contentType.startsWith("text/html")) {
                            Log.e(TAG, "Page $index returned HTML instead of image (blocked by CDN?)")
                            throw Exception("CDN blocked image download for page $index")
                        }
                        resp.body?.byteStream()?.use { input ->
                            pageFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }

                // Update progress
                val progress = (index + 1).toFloat() / pages.size
                setProgress(workDataOf("progress" to progress))
                downloadDao.updateProgress(downloadId, progress, "downloading")

                // Rate-limit notification updates: every 3 pages or on the last page
                if (index % 3 == 0 || index == pages.size - 1) {
                    try {
                        setForeground(createForegroundInfo(downloadId, title, progress))
                    } catch (_: Exception) { }
                }
            }

            // Calculate total file size
            val totalSize = downloadDir.walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() }

            // Update entity with final progress and file size
            val entity = downloadDao.getById(downloadId)
            if (entity != null) {
                downloadDao.upsert(entity.copy(
                    progress = 1f,
                    status = "completed",
                    fileSize = totalSize
                ))
            } else {
                downloadDao.updateProgress(downloadId, 1f, "completed")
            }

            Log.d(TAG, "Download complete: $chapterId ($totalSize bytes)")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $downloadId", e)
            downloadDao.updateProgress(downloadId, 0f, "failed")
            Result.failure()
        }
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

    private fun extractChapterNumber(chapterId: String): Float {
        return Regex("""(\d+(?:\.\d+)?)""").find(chapterId)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
    }
}
