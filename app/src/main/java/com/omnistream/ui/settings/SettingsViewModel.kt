package com.omnistream.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnistream.data.anilist.AniListAuthManager
import com.omnistream.data.local.UserPreferences
import com.omnistream.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPreferences: UserPreferences,
    val authManager: AniListAuthManager
) : ViewModel() {

    val userName = userPreferences.userName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val userEmail = userPreferences.userEmail
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val userTier = userPreferences.userTier
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val colorScheme = userPreferences.colorScheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "purple")

    val darkMode = userPreferences.darkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "dark")

    val preferredTrackingService = userPreferences.preferredTrackingService
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "anilist")

    fun setColorScheme(scheme: String) {
        viewModelScope.launch { userPreferences.setColorScheme(scheme) }
    }

    fun setDarkMode(mode: String) {
        viewModelScope.launch { userPreferences.setDarkMode(mode) }
    }

    fun setPreferredTrackingService(service: String) {
        viewModelScope.launch { userPreferences.setPreferredTrackingService(service) }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onLoggedOut()
        }
    }
}
