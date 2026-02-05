package com.omnistream.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.omnistream.data.remote.GitHubApiService
import com.omnistream.domain.models.AppUpdate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing app updates from GitHub
 */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val githubApi: GitHubApiService
) {
    companion object {
        private const val GITHUB_OWNER = "TamerAli-0"
        private const val GITHUB_REPO = "OmniStream-Beta"
    }

    /**
     * Get current app version name
     */
    fun getCurrentVersion(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    /**
     * Check if an update is available
     * @return AppUpdate if newer version exists, null otherwise
     */
    suspend fun checkForUpdate(): AppUpdate? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("UpdateRepository", "Checking for updates...")
            android.util.Log.d("UpdateRepository", "Fetching releases from: $GITHUB_OWNER/$GITHUB_REPO")

            val releases = githubApi.getReleases(GITHUB_OWNER, GITHUB_REPO)
            android.util.Log.d("UpdateRepository", "Found ${releases.size} releases")

            if (releases.isEmpty()) {
                android.util.Log.d("UpdateRepository", "No releases found")
                return@withContext null
            }

            // Get the first release (most recent, includes prereleases)
            val latestRelease = releases.first()
            val currentVersion = getCurrentVersion()
            val latestVersion = latestRelease.getVersionNumber()

            android.util.Log.d("UpdateRepository", "Current version: $currentVersion")
            android.util.Log.d("UpdateRepository", "Latest version: $latestVersion")

            if (isNewerVersion(latestVersion, currentVersion)) {
                android.util.Log.d("UpdateRepository", "Update available! $currentVersion -> $latestVersion")
                latestRelease
            } else {
                android.util.Log.d("UpdateRepository", "No update needed. Already on latest or newer version")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("UpdateRepository", "Error checking for updates", e)
            null
        }
    }

    /**
     * Compare version strings (e.g., "1.2.3" vs "1.2.0")
     * @return true if remoteVersion is newer than currentVersion
     */
    private fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val remoteParts = remoteVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(remoteParts.size, currentParts.size)

        for (i in 0 until maxLength) {
            val remotePart = remoteParts.getOrNull(i) ?: 0
            val currentPart = currentParts.getOrNull(i) ?: 0

            when {
                remotePart > currentPart -> return true
                remotePart < currentPart -> return false
            }
        }

        return false // Versions are equal
    }
}
