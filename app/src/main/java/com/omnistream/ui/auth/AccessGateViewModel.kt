package com.omnistream.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnistream.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccessGateViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    companion object {
        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_MS = 60_000L // 1 minute lockout
    }

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked

    private var failedAttempts = 0
    private var lockoutEndTime = 0L

    fun onPasswordChanged(value: String) {
        _password.value = value
        _error.value = null
    }

    fun submit() {
        val now = System.currentTimeMillis()
        if (_isLocked.value && now < lockoutEndTime) {
            val remaining = ((lockoutEndTime - now) / 1000).toInt()
            _error.value = "Too many attempts. Try again in ${remaining}s"
            return
        }
        if (_isLocked.value && now >= lockoutEndTime) {
            _isLocked.value = false
            failedAttempts = 0
        }

        val tier = authRepository.validateAccessPassword(_password.value)
        if (tier != null) {
            failedAttempts = 0
            viewModelScope.launch {
                authRepository.unlockApp(tier)
                _isUnlocked.value = true
            }
        } else {
            failedAttempts++
            if (failedAttempts >= MAX_ATTEMPTS) {
                _isLocked.value = true
                lockoutEndTime = System.currentTimeMillis() + LOCKOUT_MS
                _error.value = "Too many failed attempts. Locked for 60 seconds."
                viewModelScope.launch {
                    delay(LOCKOUT_MS)
                    _isLocked.value = false
                    failedAttempts = 0
                    _error.value = null
                }
            } else {
                _error.value = "Invalid access password (${MAX_ATTEMPTS - failedAttempts} attempts remaining)"
            }
        }
    }
}
