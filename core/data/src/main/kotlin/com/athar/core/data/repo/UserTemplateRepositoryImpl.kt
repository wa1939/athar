package com.athar.core.data.repo

import com.athar.core.data.db.dao.UserTemplateDao
import com.athar.core.data.db.entity.UserTemplateEntity
import com.athar.core.domain.model.TxType
import com.athar.core.domain.model.UserTemplate
import com.athar.core.domain.repo.UserTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class UserTemplateRepositoryImpl @Inject constructor(
    private val dao: UserTemplateDao,
) : UserTemplateRepository {

    override fun observeAll(): Flow<List<UserTemplate>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun upsert(template: UserTemplate) {
        dao.upsert(template.toEntity())
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    override suspend fun get(id: String): UserTemplate? = dao.get(id)?.toDomain()

    private fun UserTemplateEntity.toDomain(): UserTemplate = UserTemplate(
        id = id,
        displayName = displayName,
        sender = sender,
        txType = TxType.valueOf(txType),
        amountAnchorBefore = amountAnchorBefore,
        amountAnchorAfter = amountAnchorAfter,
        merchantAnchorBefore = merchantAnchorBefore,
        merchantAnchorAfter = merchantAnchorAfter,
        counterpartyAnchorBefore = counterpartyAnchorBefore,
        counterpartyAnchorAfter = counterpartyAnchorAfter,
        sampleBody = sampleBody,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
    )

    private fun UserTemplate.toEntity(): UserTemplateEntity = UserTemplateEntity(
        id = id,
        displayName = displayName,
        sender = sender,
        txType = txType.name,
        amountAnchorBefore = amountAnchorBefore,
        amountAnchorAfter = amountAnchorAfter,
        merchantAnchorBefore = merchantAnchorBefore,
        merchantAnchorAfter = merchantAnchorAfter,
        counterpartyAnchorBefore = counterpartyAnchorBefore,
        counterpartyAnchorAfter = counterpartyAnchorAfter,
        sampleBody = sampleBody,
        createdAt = createdAt.toEpochMilliseconds(),
    )
}
