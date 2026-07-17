package com.walletapp.android.auth

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET

@Serializable
data class RegisterRequest(val email: String, val password: String, val displayName: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class UserResponse(val id: String, val email: String, val displayName: String)

@Serializable
data class AuthResponse(val accessToken: String, val tokenType: String, val expiresAt: String)

interface AuthApi {

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): UserResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @GET("api/auth/me")
    suspend fun me(): UserResponse

    @POST("api/auth/logout")
    suspend fun logout()
}
