package com.athar.core.data.repo

import com.athar.core.data.db.dao.WishlistDao
import com.athar.core.data.mapper.toDomain
import com.athar.core.data.mapper.toEntity
import com.athar.core.domain.model.WishlistItem
import com.athar.core.domain.repo.WishlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class WishlistRepositoryImpl @Inject constructor(
    private val dao: WishlistDao,
) : WishlistRepository {

    override fun observeAll(): Flow<List<WishlistItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun upsert(item: WishlistItem) = dao.upsert(item.toEntity())

    override suspend fun delete(id: String) = dao.delete(id)
}
