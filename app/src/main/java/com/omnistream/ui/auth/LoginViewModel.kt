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
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _loginSuccess = MutableStateFlow(false)
    val loginSuccess: StateFlow<Boolean> = _loginSuccess

    fun onLoginSuccessConsumed() {
        _loginSuccess.value = false
    }

    fun onEmailChanged(value: String) {
        _email.value = value
        _error.value = null
    }

    fun onPasswordChanged(value: String) {
        _password.value = value
        _error.value = null
    }

    fun login() {
        if (_email.value.isBlank() || _password.value.isBlank()) {
            _error.value = "Email and password are required"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = authRepository.login(_email.value.trim(), _password.value)
            result.fold(
                onSuccess = { _loginSuccess.value = true },
                onFailure = { exception ->
                    val errorMsg = exception.message ?: "Login failed"
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
