package com.omnistream.di

import android.content.Context
import androidx.room.Room
import com.omnistream.core.network.OmniHttpClient
import com.omnistream.data.local.AppDatabase
import com.omnistream.data.local.DownloadDao
import com.omnistream.data.local.FavoriteDao
import com.omnistream.data.local.ReadChaptersDao
import com.omnistream.data.local.SearchHistoryDao
import com.omnistream.data.local.UserPreferences
import com.omnistream.data.local.WatchHistoryDao
import com.omnistream.data.preferences.PlayerPreferencesRepository
import com.omnistream.data.remote.ApiService
import com.omnistream.data.remote.GitHubApiService
import com.omnistream.data.repository.AuthRepository
import com.omnistream.data.repository.SyncRepository
import com.omnistream.data.repository.DownloadRepository
import com.omnistream.data.repository.ReadChaptersRepository
import com.omnistream.data.repository.UpdateRepository
import com.omnistream.data.repository.WatchHistoryRepository
import com.omnistream.source.SourceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOmniHttpClient(): OmniHttpClient {
        return OmniHttpClient()
    }

    @Provides
    @Singleton
    fun provideSourceManager(
        @ApplicationContext context: Context,
        httpClient: OmniHttpClient
    ): SourceManager {
        return SourceManager(context, httpClient)
    }

    @Provides
    @Singleton
    fun provideUserPreferences(
        @ApplicationContext context: Context
    ): UserPreferences {
        return UserPreferences(context)
    }

    @Provides
    @Singleton
    fun provideApiService(): ApiService {
        return ApiService()
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        apiService: ApiService,
        userPreferences: UserPreferences
    ): AuthRepository {
        return AuthRepository(apiService, userPreferences)
    }

    @Provides
    @Singleton
    fun provideSyncRepository(
        apiService: ApiService,
        userPreferences: UserPreferences
    ): SyncRepository {
        return SyncRepository(apiService, userPreferences)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "omnistream.db"
        ).addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3
        ).build()
    }

    @Provides
    fun provideFavoriteDao(database: AppDatabase): FavoriteDao {
        return database.favoriteDao()
    }

    @Provides
    fun provideWatchHistoryDao(database: AppDatabase): WatchHistoryDao {
        return database.watchHistoryDao()
    }

    @Provides
    fun provideSearchHistoryDao(database: AppDatabase): SearchHistoryDao {
        return database.searchHistoryDao()
    }

    @Provides
    fun provideDownloadDao(database: AppDatabase): DownloadDao {
        return database.downloadDao()
    }

    @Provides
    fun provideReadChaptersDao(database: AppDatabase): ReadChaptersDao {
        return database.readChaptersDao()
    }

    @Provides
    @Singleton
    fun provideWatchHistoryRepository(watchHistoryDao: WatchHistoryDao): WatchHistoryRepository {
        return WatchHistoryRepository(watchHistoryDao)
    }

    @Provides
    @Singleton
    fun provideReadChaptersRepository(
        readChaptersDao: ReadChaptersDao,
        aniListAuthManager: com.omnistream.data.anilist.AniListAuthManager,
        aniListSyncManager: com.omnistream.data.anilist.AniListSyncManager
    ): ReadChaptersRepository {
        return ReadChaptersRepository(readChaptersDao, aniListAuthManager, aniListSyncManager)
    }

    @Provides
    @Singleton
    fun provideDownloadRepository(
        downloadDao: DownloadDao,
        @ApplicationContext context: Context
    ): DownloadRepository {
        return DownloadRepository(downloadDao, context)
    }

    @Provides
    @Singleton
    fun providePlayerPreferencesRepository(
        @ApplicationContext context: Context
    ): PlayerPreferencesRepository {
        return PlayerPreferencesRepository(context)
    }

    @Provides
    @Singleton
    fun provideGitHubRetrofit(): Retrofit {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideGitHubApiService(retrofit: Retrofit): GitHubApiService {
        return retrofit.create(GitHubApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideUpdateRepository(
        @ApplicationContext context: Context,
        githubApi: GitHubApiService
    ): UpdateRepository {
        return UpdateRepository(context, githubApi)
    }
}
