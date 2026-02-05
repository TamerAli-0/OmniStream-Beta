package com.omnistream.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for player preferences using DataStore.
 *
 * Provides Flow-based reactive access to player settings including:
 * - Video quality constraints (maxVideoHeight for TrackSelectionParameters)
 * - Subtitle settings (enabled state and language preference)
 *
 * Uses the preferencesDataStore delegate for proper DataStore lifecycle management.
 * All write operations are suspend functions to avoid blocking the main thread.
 *
 * Pattern reference: https://developer.android.com/topic/libraries/architecture/datastore
 */
class PlayerPreferencesRepository(context: Context) {

    private val dataStore = context.dataStore

    companion object {
        /**
         * DataStore delegate - creates singleton DataStore instance per context.
         * The delegate ensures proper lifecycle management and thread-safety.
         */
        private val Context.dataStore by preferencesDataStore(name = "player_prefs")

        /**
         * Preference keys for player settings.
         * Using strongly-typed keys prevents runtime errors from typos.
         */
        private val MAX_VIDEO_HEIGHT = intPreferencesKey("max_video_height")
        private val SUBTITLE_ENABLED = booleanPreferencesKey("subtitle_enabled")
        private val SUBTITLE_LANGUAGE = stringPreferencesKey("subtitle_language")
    }

    /**
     * Flow of player preferences.
     *
     * Emits whenever preferences change, enabling reactive UI updates.
     * Default values:
     * - maxVideoHeight: Int.MAX_VALUE (Auto quality)
     * - subtitleEnabled: true
     * - subtitleLanguage: "en"
     */
    val preferences: Flow<PlayerPreferences> = dataStore.data
        .map { prefs ->
            PlayerPreferences(
                maxVideoHeight = prefs[MAX_VIDEO_HEIGHT] ?: Int.MAX_VALUE,
                subtitleEnabled = prefs[SUBTITLE_ENABLED] ?: true,
                subtitleLanguage = prefs[SUBTITLE_LANGUAGE] ?: "en"
            )
        }

    /**
     * Set maximum video height constraint for quality selection.
     *
     * This value is used with TrackSelectionParameters.setMaxVideoSize() to constrain
     * video quality selection. Use Int.MAX_VALUE for automatic quality selection.
     *
     * @param height Maximum video height in pixels, or Int.MAX_VALUE for auto
     */
    suspend fun setMaxVideoHeight(height: Int) {
        dataStore.edit { prefs ->
            prefs[MAX_VIDEO_HEIGHT] = height
        }
    }

    /**
     * Enable or disable subtitle rendering.
     *
     * @param enabled true to show subtitles, false to hide
     */
    suspend fun setSubtitleEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SUBTITLE_ENABLED] = enabled
        }
    }

    /**
     * Set preferred subtitle language code.
     *
     * @param language ISO 639-1 language code (e.g., "en", "es", "fr")
     */
    suspend fun setSubtitleLanguage(language: String) {
        dataStore.edit { prefs ->
            prefs[SUBTITLE_LANGUAGE] = language
        }
    }
}

/**
 * Player preferences data class.
 *
 * Immutable snapshot of player settings at a point in time.
 *
 * @property maxVideoHeight Maximum allowed video height (Int.MAX_VALUE for auto)
 * @property subtitleEnabled Whether subtitles should be displayed
 * @property subtitleLanguage Preferred subtitle language code
 */
data class PlayerPreferences(
    val maxVideoHeight: Int,
    val subtitleEnabled: Boolean,
    val subtitleLanguage: String
)
