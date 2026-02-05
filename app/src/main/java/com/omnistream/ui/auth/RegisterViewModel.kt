package com.omnistream.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnistream.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password

    private val _confirmPassword = MutableStateFlow("")
    val confirmPassword: StateFlow<String> = _confirmPassword

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _registerSuccess = MutableStateFlow(false)
    val registerSuccess: StateFlow<Boolean> = _registerSuccess

    fun onRegisterSuccessConsumed() {
        _registerSuccess.value = false
    }

    fun onEmailChanged(value: String) { _email.value = value; _error.value = null }
    fun onUsernameChanged(value: String) { _username.value = value; _error.value = null }
    fun onPasswordChanged(value: String) { _password.value = value; _error.value = null }
    fun onConfirmPasswordChanged(value: String) { _confirmPassword.value = value; _error.value = null }

    fun register() {
        val e = _email.value.trim()
        val u = _username.value.trim()
        val p = _password.value
        val cp = _confirmPassword.value

        if (e.isBlank() || u.isBlank() || p.isBlank()) {
            _error.value = "All fields are required"
            return
        }
        if (u.length < 3) {
            _error.value = "Username must be at least 3 characters"
            return
        }
        if (p.length < 6) {
            _error.value = "Password must be at least 6 characters"
            return
        }
        if (p != cp) {
            _error.value = "Passwords do not match"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = authRepository.register(e, u, p)
            result.fold(
                onSuccess = { _registerSuccess.value = true },
                onFailure = { exception ->
                    val errorMsg = exception.message ?: "Registration failed"
                    // Add retry message for timeout/connection errors
                    _error.value = if (errorMsg.contains("timeout", ignoreCase = true) ||
                        errorMsg.contains("failed to connect", ignoreCase = true) ||
                        errorMsg.contains("unable to resolve host", ignoreCase = true) ||
                        errorMsg.contains("network", ignoreCase = true)
                    ) {
                        "$errorMsg - Please try again"
                    } else {
                        errorMsg
                    }
                }
            )

            _isLoading.value = false
        }
    }
}
