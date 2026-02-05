package com.omnistream.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnistream.core.update.UpdateManager
import com.omnistream.data.local.UserPreferences
import com.omnistream.data.repository.UpdateRepository
import com.omnistream.domain.models.AppUpdate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val updateRepository: UpdateRepository,
    private val updateManager: UpdateManager
) : ViewModel() {

    sealed class StartDestination {
        data object Loading : StartDestination()
        data object AccessGate : StartDestination()
        data object Login : StartDestination()
        data object Home : StartDestination()
    }

    private val _startDestination = MutableStateFlow<StartDestination>(StartDestination.Loading)
    val startDestination: StateFlow<StartDestination> = _startDestination

    private val _availableUpdate = MutableStateFlow<AppUpdate?>(null)
    val availableUpdate: StateFlow<AppUpdate?> = _availableUpdate

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog

    val colorScheme = userPreferences.colorScheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "purple")

    val darkMode = userPreferences.darkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "dark")

    val downloadProgress = updateManager.downloadProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.omnistream.core.update.DownloadState.Idle)

    init {
        android.util.Log.d("MainViewModel", "===== MainViewModel CREATED =====")
        viewModelScope.launch {
            android.util.Log.d("MainViewModel", "Init coroutine started")
            val hasToken = userPreferences.authToken.first() != null
            val hasLoggedInBefore = userPreferences.hasLoggedInBefore.first()
            android.util.Log.d("MainViewModel", "hasToken=$hasToken, hasLoggedInBefore=$hasLoggedInBefore")

            _startDestination.value = when {
                // Currently logged in - go to home
                hasToken -> StartDestination.Home
                // Not logged in, but has logged in before - skip passcode, go to login
                hasLoggedInBefore -> StartDestination.Login
                // Brand new install, never logged in - require passcode
                else -> StartDestination.AccessGate
            }

            // Check for updates after a short delay (let app initialize first)
            android.util.Log.d("MainViewModel", "Waiting 2 seconds before checking for updates...")
            delay(2000)
            android.util.Log.d("MainViewModel", "Now calling checkForUpdates()")
            checkForUpdates()
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            try {
                val update = updateRepository.checkForUpdate()
                if (update != null) {
                    // Check if user already dismissed this version
                    val dismissedVersion = userPreferences.dismissedUpdateVersion.first()
                    if (dismissedVersion != update.getVersionNumber()) {
                        _availableUpdate.value = update
                        _showUpdateDialog.value = true
                    }
                }
            } catch (e: Exception) {
                // Silently fail - don't interrupt user experience
                android.util.Log.e("MainViewModel", "Failed to check for updates", e)
            }
        }
    }

    fun downloadUpdate() {
        val update = _availableUpdate.value
        if (update != null) {
            val apkUrl = update.getApkUrl()
            if (apkUrl != null) {
                updateManager.downloadAndInstall(apkUrl, update.getVersionNumber())
                _showUpdateDialog.value = false
            }
        }
    }

    fun dismissUpdateDialog() {
        viewModelScope.launch {
            val update = _availableUpdate.value
            if (update != null) {
                // Save dismissed version so we don't show this update again
                userPreferences.setDismissedUpdateVersion(update.getVersionNumber())
            }
            _showUpdateDialog.value = false
        }
    }

    fun resetDownloadState() {
        updateManager.resetState()
    }
}
