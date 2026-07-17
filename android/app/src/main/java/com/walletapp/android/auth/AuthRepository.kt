package com.walletapp.android.auth

import com.walletapp.android.data.TokenStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore
) {

    suspend fun register(email: String, password: String, displayName: String): Result<UserResponse> =
        runCatching { authApi.register(RegisterRequest(email, password, displayName)) }

    suspend fun login(email: String, password: String): Result<AuthResponse> =
        runCatching { authApi.login(LoginRequest(email, password)) }
            .onSuccess { tokenStore.saveToken(it.accessToken) }

    suspend fun logout(): Result<Unit> =
        runCatching { authApi.logout() }
            .also { tokenStore.clearToken() }
}
