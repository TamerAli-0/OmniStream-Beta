package com.omnistream.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.omnistream.data.local.DownloadDao
import com.omnistream.data.local.DownloadEntity
import com.omnistream.download.MangaDownloadWorker
import com.omnistream.download.VideoDownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao,
    @ApplicationContext private val context: Context
) {

    val allDownloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()

    fun getByStatus(status: String): Flow<List<DownloadEntity>> = downloadDao.getByStatus(status)

    suspend fun enqueueDownload(entity: DownloadEntity) {
        downloadDao.upsert(entity)

        val data = workDataOf(
            "download_id" to entity.id,
            "source_id" to entity.sourceId,
            "content_id" to entity.contentId,
            "content_type" to entity.contentType,
            "title" to entity.title,
            "episode_id" to (entity.episodeId ?: ""),
            "chapter_id" to (entity.chapterId ?: ""),
            "cover_url" to (entity.coverUrl ?: ""),
            "file_path" to entity.filePath
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workerClass = if (entity.contentType == "manga") {
            MangaDownloadWorker::class.java
        } else {
            VideoDownloadWorker::class.java
        }

        val request = OneTimeWorkRequestBuilder<androidx.work.ListenableWorker>()
            .let {
                // Use the concrete worker class via explicit request builder
                androidx.work.OneTimeWorkRequest.Builder(workerClass)
                    .setInputData(data)
                    .setConstraints(constraints)
                    .addTag("download")
                    .addTag(entity.id)
                    .build()
            }

        WorkManager.getInstance(context)
            .enqueueUniqueWork("download_${entity.id}", ExistingWorkPolicy.KEEP, request)
    }

    fun cancelDownload(downloadId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("download_$downloadId")
    }

    suspend fun pauseDownload(downloadId: String) {
        cancelDownload(downloadId)
        val entity = downloadDao.getById(downloadId)
        if (entity != null) {
            downloadDao.updateProgress(downloadId, entity.progress, "paused")
        }
    }

    suspend fun resumeDownload(downloadId: String) {
        val entity = downloadDao.getById(downloadId)
        if (entity != null) {
            enqueueDownload(entity)
        }
    }

    suspend fun deleteDownload(entity: DownloadEntity) {
        cancelDownload(entity.id)
        downloadDao.delete(entity.id)
        File(entity.filePath).deleteRecursively()
    }

    suspend fun enqueueBatch(entities: List<DownloadEntity>) {
        entities.forEach { enqueueDownload(it) }
    }
}
