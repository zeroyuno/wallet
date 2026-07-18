package com.walletapp.android.di

import com.walletapp.android.accounts.AccountApi
import com.walletapp.android.auth.AuthApi
import com.walletapp.android.categories.CategoryApi
import com.walletapp.android.data.TokenStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

// IP local de la Mac en la red WiFi (backend corriendo ahí en :8080). Se prueba en un dispositivo
// físico en la misma red — el emulador de Android en esta máquina no logra conectar apps normales
// (UID no privilegiado) a 10.0.2.2 pese a que curl/Chrome/shell sí pueden, algo específico de este
// entorno. Si volvés a usar el emulador, cambiar por "http://10.0.2.2:8080/".
private const val BASE_URL = "http://192.168.68.111:8080/"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(tokenStore: TokenStore): OkHttpClient =
        OkHttpClient.Builder()
            // Modo de conexión secuencial clásico en vez del "fast fallback" (Happy Eyeballs) de
            // OkHttp 5 — no era la causa del problema de conectividad del emulador, pero es un modo
            // más simple/predecible de todas formas para esta app.
            .fastFallback(false)
            .addInterceptor { chain ->
                val token = tokenStore.getToken()
                val request = if (token != null) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideAccountApi(retrofit: Retrofit): AccountApi = retrofit.create(AccountApi::class.java)

    @Provides
    @Singleton
    fun provideCategoryApi(retrofit: Retrofit): CategoryApi = retrofit.create(CategoryApi::class.java)
}
