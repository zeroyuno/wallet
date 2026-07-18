package com.walletapp.android.categories

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable
enum class CategoryType { INCOME, EXPENSE }

fun CategoryType.displayLabel(): String = when (this) {
    CategoryType.INCOME -> "Ingreso"
    CategoryType.EXPENSE -> "Gasto"
}

@Serializable
data class CategoryRequest(val name: String, val type: CategoryType, val parentCategoryId: String? = null)

@Serializable
data class CategoryResponse(val id: String, val name: String, val type: CategoryType, val parentCategoryId: String? = null)

interface CategoryApi {

    @GET("api/categories")
    suspend fun list(): List<CategoryResponse>

    @POST("api/categories")
    suspend fun create(@Body request: CategoryRequest): CategoryResponse

    @PUT("api/categories/{id}")
    suspend fun update(@Path("id") id: String, @Body request: CategoryRequest): CategoryResponse

    @DELETE("api/categories/{id}")
    suspend fun delete(@Path("id") id: String)
}
