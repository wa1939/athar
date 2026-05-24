package com.athar.core.data.repo

import com.athar.core.data.db.dao.CategoryDao
import com.athar.core.data.mapper.toDomain
import com.athar.core.data.mapper.toEntity
import com.athar.core.domain.model.Category
import com.athar.core.domain.model.CategoryKind
import com.athar.core.domain.repo.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao,
) : CategoryRepository {

    override fun observeAll(kind: CategoryKind?, includeArchived: Boolean): Flow<List<Category>> =
        dao.observeAll(kind?.name, includeArchived)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: String): Category? = dao.get(id)?.toDomain()

    override suspend fun upsert(category: Category) = dao.upsert(category.toEntity())

    override suspend fun archive(id: String) = dao.archive(id)

    override suspend fun reorder(ids: List<String>) {
        ids.forEachIndexed { index, id -> dao.setSortOrder(id, index) }
    }
}
