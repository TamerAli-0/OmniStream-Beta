package com.omnistream.download

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoDownloadCache @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val databaseProvider: StandaloneDatabaseProvider by lazy {
        StandaloneDatabaseProvider(context)
    }

    val cache: SimpleCache by lazy {
        val downloadDir = File(context.filesDir, "downloads/video")
        downloadDir.mkdirs()
        SimpleCache(downloadDir, NoOpCacheEvictor(), databaseProvider)
    }

    fun getCacheDataSourceFactory(upstreamFactory: DataSource.Factory): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(null)
    }
}
