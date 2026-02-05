package com.omnistream.data.repository

import com.omnistream.data.local.UserPreferences
import com.omnistream.data.remote.ApiService
import com.omnistream.data.remote.dto.SyncDataResponse
import com.omnistream.data.remote.dto.SyncUpdateRequest
import com.omnistream.data.remote.dto.LibraryEntryDto
import com.omnistream.data.remote.dto.HistoryEntryDto
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val api: ApiService,
    private val prefs: UserPreferences
) {
    suspend fun fetchSyncData(): Result<SyncDataResponse> {
        val token = prefs.authToken.first() ?: return Result.failure(Exception("Not authenticated"))
        return api.getSyncData(token)
    }

    suspend fun pushSyncData(
        library: List<LibraryEntryDto>,
        history: List<HistoryEntryDto>
    ): Result<SyncDataResponse> {
        val token = prefs.authToken.first() ?: return Result.failure(Exception("Not authenticated"))
        return api.updateSyncData(token, SyncUpdateRequest(library, history))
    }
}
