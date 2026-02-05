package com.omnistream.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val IS_UNLOCKED = booleanPreferencesKey("is_unlocked")
        private val HAS_LOGGED_IN_BEFORE = booleanPreferencesKey("has_logged_in_before")
        private val AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val USER_TIER = stringPreferencesKey("user_tier")
        private val USER_NAME = stringPreferencesKey("user_name")
        private val USER_EMAIL = stringPreferencesKey("user_email")
        private val COLOR_SCHEME = stringPreferencesKey("color_scheme")
        private val DARK_MODE = stringPreferencesKey("dark_mode") // "dark", "light", "system"
        private val SEARCH_CONTENT_TYPE_FILTER = stringPreferencesKey("search_content_type")
        private val PREFERRED_TRACKING_SERVICE = stringPreferencesKey("preferred_tracking_service") // "anilist" or "mal"
        private val DISMISSED_UPDATE_VERSION = stringPreferencesKey("dismissed_update_version")
    }

    val isUnlocked: Flow<Boolean> = context.dataStore.data.map { it[IS_UNLOCKED] ?: false }
    val hasLoggedInBefore: Flow<Boolean> = context.dataStore.data.map { it[HAS_LOGGED_IN_BEFORE] ?: false }
    val authToken: Flow<String?> = context.dataStore.data.map { it[AUTH_TOKEN] }
    val userTier: Flow<String?> = context.dataStore.data.map { it[USER_TIER] }
    val userName: Flow<String?> = context.dataStore.data.map { it[USER_NAME] }
    val userEmail: Flow<String?> = context.dataStore.data.map { it[USER_EMAIL] }
    val colorScheme: Flow<String> = context.dataStore.data.map { it[COLOR_SCHEME] ?: "purple" }
    val darkMode: Flow<String> = context.dataStore.data.map { it[DARK_MODE] ?: "dark" }
    val searchContentTypeFilter: Flow<String> = context.dataStore.data.map { it[SEARCH_CONTENT_TYPE_FILTER] ?: "ALL" }
    val preferredTrackingService: Flow<String> = context.dataStore.data.map { it[PREFERRED_TRACKING_SERVICE] ?: "anilist" }
    val dismissedUpdateVersion: Flow<String?> = context.dataStore.data.map { it[DISMISSED_UPDATE_VERSION] }

    suspend fun setUnlocked(unlocked: Boolean, tier: String) {
        context.dataStore.edit {
            it[IS_UNLOCKED] = unlocked
            it[USER_TIER] = tier
        }
    }

    suspend fun setAuthData(token: String, username: String, email: String, tier: String) {
        context.dataStore.edit {
            it[AUTH_TOKEN] = token
            it[USER_NAME] = username
            it[USER_EMAIL] = email
            it[USER_TIER] = tier
            it[HAS_LOGGED_IN_BEFORE] = true  // Mark device as having logged in successfully
        }
    }

    suspend fun clearAuth() {
        context.dataStore.edit {
            it.remove(AUTH_TOKEN)
            it.remove(USER_NAME)
            it.remove(USER_EMAIL)
        }
    }

    suspend fun setColorScheme(scheme: String) {
        context.dataStore.edit { it[COLOR_SCHEME] = scheme }
    }

    suspend fun setDarkMode(mode: String) {
        context.dataStore.edit { it[DARK_MODE] = mode }
    }

    suspend fun setSearchContentTypeFilter(filterType: String) {
        context.dataStore.edit { it[SEARCH_CONTENT_TYPE_FILTER] = filterType }
    }

    suspend fun setPreferredTrackingService(service: String) {
        context.dataStore.edit { it[PREFERRED_TRACKING_SERVICE] = service }
    }

    suspend fun setDismissedUpdateVersion(version: String) {
        context.dataStore.edit { it[DISMISSED_UPDATE_VERSION] = version }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
