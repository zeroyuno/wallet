package com.walletapp.android.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AuthViewModel"

sealed interface RegisterUiState {
    data object Idle : RegisterUiState
    data object Loading : RegisterUiState
    data class Success(val user: UserResponse) : RegisterUiState
    data class Error(val message: String) : RegisterUiState
}

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data object Success : LoginUiState
    data class Error(val message: String) : LoginUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _registerState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val registerState: StateFlow<RegisterUiState> = _registerState.asStateFlow()

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    fun register(email: String, password: String, displayName: String) {
        Log.d(TAG, "register() -> Loading")
        _registerState.value = RegisterUiState.Loading
        viewModelScope.launch {
            authRepository.register(email, password, displayName)
                .onSuccess {
                    Log.d(TAG, "register() -> Success")
                    _registerState.value = RegisterUiState.Success(it)
                }
                .onFailure {
                    Log.e(TAG, "register() -> Error: ${it.message}")
                    _registerState.value = RegisterUiState.Error(it.message ?: "Error desconocido")
                }
        }
    }

    fun login(email: String, password: String) {
        Log.d(TAG, "login() -> Loading")
        _loginState.value = LoginUiState.Loading
        viewModelScope.launch {
            authRepository.login(email, password)
                .onSuccess {
                    Log.d(TAG, "login() -> Success")
                    _loginState.value = LoginUiState.Success
                }
                .onFailure {
                    Log.e(TAG, "login() -> Error: ${it.message}")
                    _loginState.value = LoginUiState.Error(it.message ?: "Credenciales inválidas")
                }
        }
    }

    fun logout() {
        Log.d(TAG, "logout() -> iniciando")
        viewModelScope.launch {
            authRepository.logout()
            Log.d(TAG, "logout() -> estado reseteado")
            _loginState.value = LoginUiState.Idle
            _registerState.value = RegisterUiState.Idle
        }
    }
}
