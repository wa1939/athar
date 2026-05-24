package com.athar.core.data.repo

import com.athar.core.data.db.dao.InvestmentDao
import com.athar.core.data.mapper.toDomain
import com.athar.core.data.mapper.toEntity
import com.athar.core.domain.model.InvestmentContribution
import com.athar.core.domain.model.InvestmentPool
import com.athar.core.domain.repo.InvestmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class InvestmentRepositoryImpl @Inject constructor(
    private val dao: InvestmentDao,
) : InvestmentRepository {

    override fun observePoolsWithContributions(): Flow<Map<InvestmentPool, List<InvestmentContribution>>> =
        combine(dao.observePools(), dao.observeContributions()) { pools, contributions ->
            val poolsByDomain = pools.associateBy({ it.toDomain() }, { it.id })
            val contributionsByPoolId = contributions.groupBy { it.poolId }
            poolsByDomain.entries.associate { (poolDomain, poolId) ->
                poolDomain to (contributionsByPoolId[poolId].orEmpty().map { it.toDomain() })
            }
        }

    override suspend fun upsertPool(pool: InvestmentPool) = dao.upsertPool(pool.toEntity())

    override suspend fun upsertContribution(contribution: InvestmentContribution) =
        dao.upsertContribution(contribution.toEntity())
}
