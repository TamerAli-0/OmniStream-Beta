package com.omnistream.data.remote

import com.omnistream.domain.models.AppUpdate
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * GitHub API service for checking app updates
 */
interface GitHubApiService {

    /**
     * Get all releases from GitHub repository (includes prereleases)
     * @param owner GitHub username/organization
     * @param repo Repository name
     */
    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<AppUpdate>
}
