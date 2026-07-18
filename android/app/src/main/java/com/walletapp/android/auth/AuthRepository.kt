package com.walletapp.android.auth

import android.util.Log
import com.walletapp.android.data.TokenStore
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthRepository"

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore
) {

    // Nunca se loguea la contraseña — solo el email y el resultado de la llamada.
    suspend fun register(email: String, password: String, displayName: String): Result<UserResponse> {
        Log.d(TAG, "register: POST /api/auth/register email=$email")
        return runCatching { authApi.register(RegisterRequest(email, password, displayName)) }
            .onSuccess { Log.d(TAG, "register: OK id=${it.id}") }
            .onFailure { Log.e(TAG, "register: falló para email=$email", it) }
    }

    suspend fun login(email: String, password: String): Result<AuthResponse> {
        Log.d(TAG, "login: POST /api/auth/login email=$email")
        return runCatching { authApi.login(LoginRequest(email, password)) }
            .onSuccess {
                tokenStore.saveToken(it.accessToken)
                Log.d(TAG, "login: OK, token guardado (expira ${it.expiresAt})")
            }
            .onFailure { Log.e(TAG, "login: falló para email=$email", it) }
    }

    fun hasStoredToken(): Boolean = tokenStore.getToken() != null

    // Confirma contra el backend que el token guardado todavía es válido (no expiró, no fue
    // revocado desde otro dispositivo/logout). Si no lo es, limpia el token local.
    suspend fun validateSession(): Result<UserResponse> {
        Log.d(TAG, "validateSession: GET /api/auth/me")
        return runCatching { authApi.me() }
            .onSuccess { Log.d(TAG, "validateSession: OK, sesión sigue activa") }
            .onFailure {
                Log.w(TAG, "validateSession: token inválido o expirado, se limpia", it)
                tokenStore.clearToken()
            }
    }

    suspend fun logout(): Result<Unit> {
        Log.d(TAG, "logout: POST /api/auth/logout")
        return runCatching { authApi.logout() }
            .onSuccess { Log.d(TAG, "logout: token revocado en el servidor") }
            .onFailure { Log.e(TAG, "logout: falló la llamada al servidor (se limpia el token local igual)", it) }
            .also { tokenStore.clearToken() }
    }
}
