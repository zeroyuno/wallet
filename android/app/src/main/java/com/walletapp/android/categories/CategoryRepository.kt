package com.walletapp.android.categories

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(private val categoryApi: CategoryApi) {

    suspend fun list(): Result<List<CategoryResponse>> = runCatching { categoryApi.list() }

    suspend fun create(name: String, type: CategoryType, parentCategoryId: String?): Result<CategoryResponse> =
        runCatching { categoryApi.create(CategoryRequest(name, type, parentCategoryId)) }

    suspend fun update(id: String, name: String, type: CategoryType, parentCategoryId: String?): Result<CategoryResponse> =
        runCatching { categoryApi.update(id, CategoryRequest(name, type, parentCategoryId)) }

    suspend fun delete(id: String): Result<Unit> = runCatching { categoryApi.delete(id) }
}
