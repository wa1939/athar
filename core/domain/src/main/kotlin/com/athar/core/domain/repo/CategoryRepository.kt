package com.athar.core.domain.repo

import com.athar.core.domain.model.Category
import com.athar.core.domain.model.CategoryKind
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeAll(kind: CategoryKind? = null, includeArchived: Boolean = false): Flow<List<Category>>
    suspend fun get(id: String): Category?
    suspend fun upsert(category: Category)
    suspend fun archive(id: String)
    suspend fun reorder(ids: List<String>)
}
